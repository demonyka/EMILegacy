package dev.emi.emi.runtime;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class EmiDrawContext {
	private final MinecraftClient client = MinecraftClient.getInstance();
	private final MatrixStack matrices;
	
	private EmiDrawContext(MatrixStack matrices) {
		this.matrices = matrices;
	}

	public static EmiDrawContext wrap(MatrixStack matrices) {
		return new EmiDrawContext(matrices);
	}

	public MatrixStack raw() {
		return matrices;
	}

	public MatrixStack matrices() {
		return matrices;
	}

	public void push() {
		matrices.push();
	}

	public void pop() {
		matrices.pop();
	}

	public void translate(float x, float y) {
		matrices().translate(x, y, 0);
	}

	public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
		drawTexture(texture, x, y, width, height, u, v, width, height, 256, 256);
	}

	public void drawTexture(Identifier texture, int x, int y, int z, float u, float v, int width, int height) {
		drawTexture(texture, x, y, z, u, v, width, height, 256, 256);
	}

	public void drawTexture(Identifier texture, int x, int y, int z, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		EmiPort.bindTexture(texture);
		RenderSystem.color4f(tintR, tintG, tintB, tintA);
		DrawableHelper.drawTexture(matrices, x, y, z, u, v, width, height, textureWidth, textureHeight);
	}

	public void drawTexture(Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		EmiPort.bindTexture(texture);
		RenderSystem.color4f(tintR, tintG, tintB, tintA);
		DrawableHelper.drawTexture(matrices, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
	}

	public void fill(int x, int y, int width, int height, int color) {
		if (tintR != 1f || tintG != 1f || tintB != 1f || tintA != 1f) {
			int a = (int) ((color >>> 24) * tintA);
			int r = (int) ((color >> 16 & 0xFF) * tintR);
			int g = (int) ((color >> 8 & 0xFF) * tintG);
			int b = (int) ((color & 0xFF) * tintB);
			color = a << 24 | r << 16 | g << 8 | b;
		}
		DrawableHelper.fill(matrices, x, y, x + width, y + height, color);
	}

	public void drawText(Text text, int x, int y) {
		drawText(text, x, y, -1);
	}

	public void drawText(Text text, int x, int y, int color) {
		client.textRenderer.draw(matrices, text, x, y, color);
	}

	public void drawText(OrderedText text, int x, int y, int color) {
		client.textRenderer.draw(matrices, text, x, y, color);
	}

	public void drawTextWithShadow(Text text, int x, int y) {
		drawTextWithShadow(text, x, y, -1);
	}

	public void drawTextWithShadow(Text text, int x, int y, int color) {
		client.textRenderer.drawWithShadow(matrices, text, x, y, color);
	}

	public void drawTextWithShadow(OrderedText text, int x, int y, int color) {
		client.textRenderer.drawWithShadow(matrices, text, x, y, color);
	}

	public void drawCenteredText(Text text, int x, int y) {
		drawCenteredText(text, x, y, -1);
	}

	public void drawCenteredText(Text text, int x, int y, int color) {
		client.textRenderer.draw(matrices, text, x - client.textRenderer.getWidth(text) / 2, y, color);
	}

	public void drawCenteredTextWithShadow(Text text, int x, int y) {
		drawCenteredTextWithShadow(text, x, y, -1);
	}

	public void drawCenteredTextWithShadow(Text text, int x, int y, int color) {
		DrawableHelper.drawCenteredText(matrices, client.textRenderer, text, x, y, color);
	}

	public void enableDepthTest() {
		RenderSystem.enableDepthTest();
	}

	public void disableDepthTest() {
		RenderSystem.disableDepthTest();
	}

	public void enableBlend() {
		RenderSystem.enableBlend();
	}

	public void disableBlend() {
		RenderSystem.disableBlend();
	}

	// Shared by all contexts, like the shader color of newer versions
	private static float tintR = 1f, tintG = 1f, tintB = 1f, tintA = 1f;

	public void resetColor() {
		setColor(1f, 1f, 1f, 1f);
	}

	public void setColor(float r, float g, float b) {
		setColor(r, g, b, 1f);
	}

	public void setColor(float r, float g, float b, float a) {
		tintR = r;
		tintG = g;
		tintB = b;
		tintA = a;
		RenderSystem.color4f(r, g, b, a);
	}

	public void drawStack(EmiIngredient stack, int x, int y) {
		stack.render(raw(), x, y, client.getTickDelta());
	}

	public void drawStack(EmiIngredient stack, int x, int y, int flags) {
		drawStack(stack, x, y, client.getTickDelta(), flags);
	}

	public void drawStack(EmiIngredient stack, int x, int y, float delta, int flags) {
		stack.render(raw(), x, y, delta, flags);
	}
}
