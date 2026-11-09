package dev.emi.emi.runtime.dev;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.mixin.accessor.KeyboardAccessor;
import dev.emi.emi.mixin.accessor.MouseAccessor;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.ConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development test harness. Inert unless {@code -Demi.devScript=<file>} is set.
 * Runs one command per line from the file, in client ticks, so EMI can be exercised
 * and screenshotted without manual input. Commands:
 * <pre>
 * world [survival|creative]   create/load the test world (only valid as first command)
 * connect HOST PORT           join a server instead (only valid as first command)
 * wait N | waitloaded          wait N ticks / until EMI has reloaded
 * cmd TEXT                     run a server command (without slash)
 * use X Y Z                    right-click the block at X Y Z as the player (opens machine GUIs)
 * inventory | close | config   open the inventory / close the screen / open EMI config
 * recipes ID | uses ID | tree  open EMI recipe screen for an item / uses / recipe tree
 * move X Y | click X Y [B]     move the mouse / click at GUI coordinates (button 0=left, 1=right)
 * key NAME | type TEXT         press a key (e.g. key.keyboard.r) / type text
 * dump                         log current screen, cursor stack and container slots
 * shot NAME                    save run/screenshots/NAME.png
 * log TEXT | quit
 * </pre>
 */
@Mod.EventBusSubscriber(modid = "emi", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmiDevScript {
	private static final String WORLD = "emi-test";
	private static final String SCRIPT = System.getProperty("emi.devScript");
	private static final Deque<String> LINES = new ArrayDeque<>();
	private static boolean worldStarted;
	private static int waitTicks;
	private static boolean waitLoaded;
	private static GameMode mode = GameMode.CREATIVE;
	private static String pendingShot;
	private static int pendingUse;
	private static String connectHost;
	private static int connectPort;

	static {
		if (SCRIPT != null) {
			try {
				List<String> lines = Files.readAllLines(new File(SCRIPT).toPath(), StandardCharsets.UTF_8);
				for (String line : lines) {
					line = line.trim();
					if (!line.isEmpty() && !line.startsWith("#")) {
						LINES.add(line);
					}
				}
				if (!LINES.isEmpty() && LINES.peekFirst().startsWith("connect ")) {
					String[] parts = LINES.pollFirst().split(" ");
					connectHost = parts[1];
					connectPort = Integer.parseInt(parts[2]);
				} else if (!LINES.isEmpty() && LINES.peekFirst().startsWith("world")) {
					String[] parts = LINES.pollFirst().split(" ");
					if (parts.length > 1 && parts[1].equals("survival")) {
						mode = GameMode.SURVIVAL;
					}
				}
			} catch (Exception e) {
				EmiLog.error("[devscript] Could not read " + SCRIPT, e);
			}
		}
	}

	private EmiDevScript() {
	}

	@SubscribeEvent
	public static void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (SCRIPT == null) {
			return;
		}
		// Modded dimensions trigger the vanilla "experimental settings" confirmation: proceed
		if (worldStarted && MinecraftClient.getInstance().player == null
				&& event.getGui() instanceof net.minecraft.client.gui.screen.ConfirmScreen && !event.getWidgetList().isEmpty()) {
			EmiLog.info("[devscript] Confirming " + event.getGui().getTitle().getString());
			net.minecraft.client.gui.widget.ButtonWidget proceed = (net.minecraft.client.gui.widget.ButtonWidget) event.getWidgetList().get(0);
			MinecraftClient.getInstance().execute(proceed::onPress);
			return;
		}
		if (worldStarted || !(event.getGui() instanceof TitleScreen)) {
			return;
		}
		worldStarted = true;
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (connectHost != null) {
				client.openScreen(new net.minecraft.client.gui.screen.ConnectScreen(event.getGui(), client, connectHost, connectPort));
			} else if (client.getLevelStorage().levelExists(WORLD)) {
				client.startIntegratedServer(WORLD);
			} else {
				DynamicRegistryManager.Impl registries = DynamicRegistryManager.create();
				LevelInfo info = new LevelInfo(WORLD, mode, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackSettings.SAFE_MODE);
				client.createWorld(WORLD, info, registries, GeneratorOptions.method_31112(registries));
			}
		});
	}

	// Screenshots are taken at the end of the frame, before the buffers are swapped
	@SubscribeEvent
	public static void onRender(TickEvent.RenderTickEvent event) {
		if (pendingShot != null && event.phase == TickEvent.Phase.END) {
			String name = pendingShot;
			pendingShot = null;
			shot(MinecraftClient.getInstance(), name);
		}
	}

	@SubscribeEvent
	public static void onTick(TickEvent.ClientTickEvent event) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (SCRIPT == null || event.phase != TickEvent.Phase.END || client.player == null) {
			return;
		}
		if (pendingShot != null) {
			return;
		}
		// Press "use" a couple of ticks after turning, once the crosshair target is updated
		if (pendingUse > 0 && --pendingUse == 0) {
			net.minecraft.client.option.KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(client.options.keyUse.getBoundKeyTranslationKey()));
			return;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		if (waitLoaded) {
			if (!EmiReloadManager.isLoaded()) {
				return;
			}
			waitLoaded = false;
		}
		String line = LINES.pollFirst();
		if (line == null) {
			return;
		}
		EmiLog.info("[devscript] " + line);
		try {
			run(client, line);
		} catch (Throwable t) {
			EmiLog.error("[devscript] FAILED: " + line, t);
		}
	}

	private static void run(MinecraftClient client, String line) {
		String[] p = line.split(" ");
		String rest = line.indexOf(' ') < 0 ? "" : line.substring(line.indexOf(' ') + 1);
		long window = client.getWindow().getHandle();
		double scale = client.getWindow().getScaleFactor();
		switch (p[0]) {
			case "wait" -> waitTicks = Integer.parseInt(p[1]);
			case "waitloaded" -> waitLoaded = true;
			case "cmd" -> client.player.sendChatMessage("/" + rest);
			case "use" -> {
				// Like a player: look at the block, then press the "use" key (raycast + packet happen as usual)
				BlockPos pos = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
				client.player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.ofCenter(pos));
				pendingUse = 2;
			}
			case "inventory" -> client.openScreen(new InventoryScreen(client.player));
			case "close" -> {
				if (client.currentScreen != null) {
					client.currentScreen.onClose();
				}
			}
			case "config" -> client.openScreen(new ConfigScreen(client.currentScreen));
			case "recipes" -> EmiApi.displayRecipes(EmiStack.of(Registry.ITEM.get(new Identifier(p[1]))));
			case "uses" -> EmiApi.displayUses(EmiStack.of(Registry.ITEM.get(new Identifier(p[1]))));
			case "tree" -> EmiApi.viewRecipeTree();
			case "move" -> moveMouse(client, window, scale, Double.parseDouble(p[1]), Double.parseDouble(p[2]));
			case "click" -> {
				moveMouse(client, window, scale, Double.parseDouble(p[1]), Double.parseDouble(p[2]));
				int button = p.length > 3 ? Integer.parseInt(p[3]) : 0;
				MouseAccessor mouse = (MouseAccessor) client.mouse;
				mouse.emi$onMouseButton(window, button, GLFW.GLFW_PRESS, 0);
				mouse.emi$onMouseButton(window, button, GLFW.GLFW_RELEASE, 0);
			}
			case "key" -> {
				InputUtil.Key key = InputUtil.fromTranslationKey(p[1]);
				client.keyboard.onKey(window, key.getCode(), 0, GLFW.GLFW_PRESS, 0);
				client.keyboard.onKey(window, key.getCode(), 0, GLFW.GLFW_RELEASE, 0);
			}
			case "type" -> {
				for (char c : rest.toCharArray()) {
					((KeyboardAccessor) client.keyboard).emi$onChar(window, c, 0);
				}
			}
			case "dump" -> dump(client);
			case "shot" -> pendingShot = p[1];
			case "log" -> EmiLog.info("[devscript] LOG " + rest);
			case "quit" -> client.scheduleStop();
			default -> EmiLog.warn("[devscript] Unknown command " + p[0]);
		}
	}

	private static void moveMouse(MinecraftClient client, long window, double scale, double x, double y) {
		GLFW.glfwSetCursorPos(window, x * scale, y * scale);
		((MouseAccessor) client.mouse).emi$onCursorPos(window, x * scale, y * scale);
	}

	// Encodes the frame with ImageIO: stb-based vanilla saving fails in some launch environments
	private static void shot(MinecraftClient client, String name) {
		try (net.minecraft.client.texture.NativeImage image = ScreenshotUtils.takeScreenshot(
				client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight(), client.getFramebuffer())) {
			java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(image.getWidth(), image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int abgr = image.getPixelColor(x, y);
					out.setRGB(x, y, (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF));
				}
			}
			File dir = new File(client.runDirectory, "screenshots");
			dir.mkdirs();
			javax.imageio.ImageIO.write(out, "png", new File(dir, name + ".png"));
			EmiLog.info("[devscript] Saved screenshot " + name);
		} catch (Exception e) {
			EmiLog.error("[devscript] Screenshot failed", e);
		}
	}

	private static void dump(MinecraftClient client) {
		EmiLog.info("[devscript] DUMP screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getName())
			+ " cursor=" + str(client.player.inventory.getCursorStack()));
		if (client.currentScreen instanceof HandledScreen<?> hs) {
			StringBuilder sb = new StringBuilder();
			for (Slot slot : hs.getScreenHandler().slots) {
				if (slot.hasStack()) {
					sb.append(" [").append(slot.id).append("]=").append(str(slot.getStack()));
				}
			}
			EmiLog.info("[devscript] DUMP slots" + sb);
		}
	}

	private static String str(ItemStack stack) {
		return stack.isEmpty() ? "empty" : stack.getCount() + "x" + Registry.ITEM.getId(stack.getItem());
	}
}
