package dev.emi.emi.backport;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.Matrix4f;

public class OrderedTextTooltipComponent implements TooltipComponent {
	private static final int FULL_BRIGHT = 0xF000F0;
	private final OrderedText text;

	public OrderedTextTooltipComponent(OrderedText text) {
		this.text = text;
	}

	public OrderedText getText() {
		return text;
	}

	@Override
	public int getHeight() {
		return 10;
	}

	@Override
	public int getWidth(TextRenderer textRenderer) {
		return textRenderer.getWidth(text);
	}

	@Override
	public void drawText(TextRenderer textRenderer, int x, int y, Matrix4f matrix, VertexConsumerProvider.Immediate vertexConsumers) {
		textRenderer.draw(text, x, y, -1, true, matrix, vertexConsumers, false, 0, FULL_BRIGHT);
	}
}
