package dev.emi.emi.backport;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.math.Matrix4f;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Draws a list of {@link TooltipComponent}s the way Minecraft 1.17+ does
 * (Screen#renderTooltipFromComponents), using 1.16 fixed-function rendering.
 * <p>
 * When the tooltip belongs to an item, the Forge tooltip events are fired like
 * GuiUtils#drawHoveringText does, so mods that draw on item tooltips (AppleSkin food
 * icons, Quark previews, tooltip recolor mods) keep working on EMI's tooltips.
 */
public final class TooltipRenderer {
	private static final int BACKGROUND = 0xF0100010;
	private static final int BORDER_TOP = 0x505000FF;
	private static final int BORDER_BOTTOM = 0x5028007F;

	private TooltipRenderer() {
	}

	public static void render(MatrixStack matrices, List<TooltipComponent> components, int x, int y, int screenWidth, int screenHeight) {
		render(matrices, components, x, y, screenWidth, screenHeight, ItemStack.EMPTY);
	}

	public static void render(MatrixStack matrices, List<TooltipComponent> components, int x, int y, int screenWidth, int screenHeight, ItemStack stack) {
		if (components.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer textRenderer = client.textRenderer;
		ItemRenderer itemRenderer = client.getItemRenderer();
		boolean events = !stack.isEmpty();
		List<StringVisitable> lines = events ? toLines(components) : null;

		if (events) {
			RenderTooltipEvent.Pre pre = new RenderTooltipEvent.Pre(stack, lines, matrices, x, y, screenWidth, screenHeight, -1, textRenderer);
			if (MinecraftForge.EVENT_BUS.post(pre)) {
				return;
			}
			x = pre.getX();
			y = pre.getY();
			textRenderer = pre.getFontRenderer();
		}

		int width = 0;
		int height = components.size() == 1 ? -2 : 0;
		for (TooltipComponent component : components) {
			width = Math.max(width, component.getWidth(textRenderer));
			height += component.getHeight();
		}
		int left = x + 12;
		int top = y - 12;
		if (left + width > screenWidth) {
			left -= 28 + width;
		}
		if (top + height + 6 > screenHeight) {
			top = screenHeight - height - 6;
		}

		int background = BACKGROUND, borderTop = BORDER_TOP, borderBottom = BORDER_BOTTOM;
		if (events) {
			RenderTooltipEvent.Color color = new RenderTooltipEvent.Color(stack, lines, matrices, left, top, textRenderer,
				background, borderTop, borderBottom);
			MinecraftForge.EVENT_BUS.post(color);
			background = color.getBackground();
			borderTop = color.getBorderStart();
			borderBottom = color.getBorderEnd();
		}

		matrices.push();
		float oldZ = itemRenderer.zOffset;
		itemRenderer.zOffset = 400.0f;
		Matrix4f matrix = matrices.peek().getModel();
		int z = 400;

		RenderSystem.disableTexture();
		RenderSystem.enableBlend();
		RenderSystem.disableAlphaTest();
		RenderSystem.defaultBlendFunc();
		RenderSystem.shadeModel(GL11.GL_SMOOTH);
		RenderSystem.enableDepthTest();
		BufferBuilder buf = Tessellator.getInstance().getBuffer();
		buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
		gradient(matrix, buf, left - 3, top - 4, left + width + 3, top - 3, z, background, background);
		gradient(matrix, buf, left - 3, top + height + 3, left + width + 3, top + height + 4, z, background, background);
		gradient(matrix, buf, left - 3, top - 3, left + width + 3, top + height + 3, z, background, background);
		gradient(matrix, buf, left - 4, top - 3, left - 3, top + height + 3, z, background, background);
		gradient(matrix, buf, left + width + 3, top - 3, left + width + 4, top + height + 3, z, background, background);
		gradient(matrix, buf, left - 3, top - 2, left - 2, top + height + 2, z, borderTop, borderBottom);
		gradient(matrix, buf, left + width + 2, top - 2, left + width + 3, top + height + 2, z, borderTop, borderBottom);
		gradient(matrix, buf, left - 3, top - 3, left + width + 3, top - 2, z, borderTop, borderTop);
		gradient(matrix, buf, left - 3, top + height + 2, left + width + 3, top + height + 3, z, borderBottom, borderBottom);
		buf.end();
		BufferRenderer.draw(buf);
		RenderSystem.shadeModel(GL11.GL_FLAT);
		RenderSystem.disableBlend();
		RenderSystem.enableAlphaTest();
		RenderSystem.enableTexture();

		if (events) {
			MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostBackground(stack, lines, matrices, left, top, textRenderer, width, height));
		}

		VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
		matrices.translate(0.0, 0.0, 400.0);
		Matrix4f textMatrix = matrices.peek().getModel();
		int lineY = top;
		for (int i = 0; i < components.size(); i++) {
			TooltipComponent component = components.get(i);
			component.drawText(textRenderer, left, lineY, textMatrix, immediate);
			lineY += component.getHeight() + (i == 0 ? 2 : 0);
		}
		immediate.draw();
		if (events) {
			MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostText(stack, lines, matrices, left, top, textRenderer, width, height));
		}
		matrices.pop();

		lineY = top;
		for (int i = 0; i < components.size(); i++) {
			TooltipComponent component = components.get(i);
			component.drawItems(textRenderer, left, lineY, matrices, itemRenderer, 400);
			lineY += component.getHeight() + (i == 0 ? 2 : 0);
		}
		itemRenderer.zOffset = oldZ;
	}

	/** Text of the components, for the event listeners that inspect tooltip lines. Non-text components become blank lines. */
	private static List<StringVisitable> toLines(List<TooltipComponent> components) {
		List<StringVisitable> lines = Lists.newArrayList();
		for (TooltipComponent component : components) {
			if (component instanceof OrderedTextTooltipComponent text) {
				MutableText line = new LiteralText("");
				text.getText().accept((index, style, codePoint) -> {
					line.append(new LiteralText(new String(Character.toChars(codePoint))).setStyle(style));
					return true;
				});
				lines.add(line);
			} else {
				lines.add(new LiteralText(""));
			}
		}
		return lines;
	}

	private static void gradient(Matrix4f m, BufferBuilder buf, int x1, int y1, int x2, int y2, int z, int top, int bottom) {
		float ta = (top >>> 24 & 255) / 255f, tr = (top >> 16 & 255) / 255f, tg = (top >> 8 & 255) / 255f, tb = (top & 255) / 255f;
		float ba = (bottom >>> 24 & 255) / 255f, br = (bottom >> 16 & 255) / 255f, bg = (bottom >> 8 & 255) / 255f, bb = (bottom & 255) / 255f;
		buf.vertex(m, x2, y1, z).color(tr, tg, tb, ta).next();
		buf.vertex(m, x1, y1, z).color(tr, tg, tb, ta).next();
		buf.vertex(m, x1, y2, z).color(br, bg, bb, ba).next();
		buf.vertex(m, x2, y2, z).color(br, bg, bb, ba).next();
	}
}
