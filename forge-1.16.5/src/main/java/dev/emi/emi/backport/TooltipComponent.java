package dev.emi.emi.backport;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.Matrix4f;

/**
 * Backport of the tooltip component from Minecraft 1.17. Tooltips in 1.16.5 are text only,
 * so EMI draws its tooltips itself through {@link TooltipRenderer}.
 */
public interface TooltipComponent {

	static TooltipComponent of(OrderedText text) {
		return new OrderedTextTooltipComponent(text);
	}

	int getHeight();

	int getWidth(TextRenderer textRenderer);

	default void drawText(TextRenderer textRenderer, int x, int y, Matrix4f matrix, VertexConsumerProvider.Immediate vertexConsumers) {
	}

	default void drawItems(TextRenderer textRenderer, int x, int y, MatrixStack matrices, ItemRenderer itemRenderer, int z) {
	}
}
