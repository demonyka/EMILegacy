package dev.emi.emi.screen.tooltip;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.backport.OrderedTextTooltipComponent;
import net.minecraft.text.OrderedText;

public class EmiTextTooltipWrapper extends OrderedTextTooltipComponent {
	public EmiIngredient stack;

	public EmiTextTooltipWrapper(EmiIngredient stack, OrderedText text) {
		super(text);
		this.stack = stack;
	}

	public EmiTextTooltipWrapper(EmiIngredient stack, OrderedTextTooltipComponent original) {
		super(((OrderedTextTooltipComponent) original).getText());
		this.stack = stack;
	}
}
