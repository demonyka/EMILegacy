package dev.emi.emi.runtime.dev;

import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.registry.DynamicRegistryManager;
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
 * Development aid for testing without manual input: with -Demi.devAutoWorld=true the client
 * creates/loads a creative test world from the title screen and opens the inventory once EMI
 * has finished reloading. Inert unless the system property is set.
 */
@Mod.EventBusSubscriber(modid = "emi", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmiDevAutoWorld {
	private static final String WORLD = "emi-test";
	private static final boolean ENABLED = Boolean.getBoolean("emi.devAutoWorld");
	private static boolean started, inventoryOpened;
	private static final String SHOW_RECIPES = System.getProperty("emi.devShowRecipes");
	private static int ticksInWorld;
	private static boolean recipesShown;

	private EmiDevAutoWorld() {
	}

	@SubscribeEvent
	public static void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (!ENABLED || started || !(event.getGui() instanceof TitleScreen)) {
			return;
		}
		started = true;
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.getLevelStorage().levelExists(WORLD)) {
				EmiLog.info("[dev] Loading test world");
				client.startIntegratedServer(WORLD);
			} else {
				EmiLog.info("[dev] Creating test world");
				DynamicRegistryManager.Impl registries = DynamicRegistryManager.create();
				LevelInfo info = new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
					new GameRules(), DataPackSettings.SAFE_MODE);
				client.createWorld(WORLD, info, registries, GeneratorOptions.method_31112(registries));
			}
		});
	}

	@SubscribeEvent
	public static void onTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			ticksInWorld = 0;
			return;
		}
		ticksInWorld++;
		// Optionally open the recipe screen for an item id, e.g. -Demi.devShowRecipes=mekanism:crusher
		if (SHOW_RECIPES != null && !recipesShown && inventoryOpened && ticksInWorld > 80) {
			recipesShown = true;
			net.minecraft.item.Item item = net.minecraft.util.registry.Registry.ITEM.get(new net.minecraft.util.Identifier(SHOW_RECIPES));
			EmiLog.info("[dev] Showing recipes for " + SHOW_RECIPES);
			dev.emi.emi.api.EmiApi.displayRecipes(dev.emi.emi.api.stack.EmiStack.of(item));
			return;
		}
		// Keep the inventory open (focus loss may close it) so screenshots always show EMI
		if (ticksInWorld > 40 && ticksInWorld % 20 == 0 && client.currentScreen == null && EmiReloadManager.isLoaded()) {
			if (!inventoryOpened) {
				EmiLog.info("[dev] EMI loaded, opening inventory");
			}
			inventoryOpened = true;
			client.openScreen(new InventoryScreen(client.player));
		}
	}
}
