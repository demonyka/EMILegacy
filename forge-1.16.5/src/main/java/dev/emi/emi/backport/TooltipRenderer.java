package dev.emi.emi.backport;

import java.util.List;

import org.lwjgl.opengl.GL11;

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
import net.minecraft.util.math.Matrix4f;

/**
 * Draws a list of {@link TooltipComponent}s the way Minecraft 1.17+ does
 * (Screen#renderTooltipFromComponents), using 1.16 fixed-function rendering.
 */
public final class TooltipRenderer {
	private static final int BACKGROUND = 0xF0100010;
	private static final int BORDER_TOP = 0x505000FF;
	private static final int BORDER_BOTTOM = 0x5028007F;

	private TooltipRenderer() {
	}

	public static void render(MatrixStack matrices, List<TooltipComponent> components, int x, int y, int screenWidth, int screenHeight) {
		if (components.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer textRenderer = client.textRenderer;
		ItemRenderer itemRenderer = client.getItemRenderer();

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
		gradient(matrix, buf, left - 3, top - 4, left + width + 3, top - 3, z, BACKGROUND, BACKGROUND);
		gradient(matrix, buf, left - 3, top + height + 3, left + width + 3, top + height + 4, z, BACKGROUND, BACKGROUND);
		gradient(matrix, buf, left - 3, top - 3, left + width + 3, top + height + 3, z, BACKGROUND, BACKGROUND);
		gradient(matrix, buf, left - 4, top - 3, left - 3, top + height + 3, z, BACKGROUND, BACKGROUND);
		gradient(matrix, buf, left + width + 3, top - 3, left + width + 4, top + height + 3, z, BACKGROUND, BACKGROUND);
		gradient(matrix, buf, left - 3, top - 2, left - 2, top + height + 2, z, BORDER_TOP, BORDER_BOTTOM);
		gradient(matrix, buf, left + width + 2, top - 2, left + width + 3, top + height + 2, z, BORDER_TOP, BORDER_BOTTOM);
		gradient(matrix, buf, left - 3, top - 3, left + width + 3, top - 2, z, BORDER_TOP, BORDER_TOP);
		gradient(matrix, buf, left - 3, top + height + 2, left + width + 3, top + height + 3, z, BORDER_BOTTOM, BORDER_BOTTOM);
		buf.end();
		BufferRenderer.draw(buf);
		RenderSystem.shadeModel(GL11.GL_FLAT);
		RenderSystem.disableBlend();
		RenderSystem.enableAlphaTest();
		RenderSystem.enableTexture();

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
		matrices.pop();

		lineY = top;
		for (int i = 0; i < components.size(); i++) {
			TooltipComponent component = components.get(i);
			component.drawItems(textRenderer, left, lineY, matrices, itemRenderer, 400);
			lineY += component.getHeight() + (i == 0 ? 2 : 0);
		}
		itemRenderer.zOffset = oldZ;
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
