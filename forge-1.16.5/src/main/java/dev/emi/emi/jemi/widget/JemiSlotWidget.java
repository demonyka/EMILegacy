package dev.emi.emi.jemi.widget;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.backport.TooltipComponent;
import dev.emi.emi.jemi.JemiInternals;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.ingredient.ITooltipCallback;
import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.ingredients.GuiIngredientGroup;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.text.Text;

/**
 * An EMI slot placed over a JEI slot. The JEI recipe layout draws the slot contents itself
 * (custom renderers, tanks, backgrounds), this widget provides EMI's hover highlight, tooltips
 * and interactions (recipe/usage lookup, favorites, recipe tree).
 */
public class JemiSlotWidget<T> extends SlotWidget {
	private final GuiIngredient<T> ingredient;
	private final GuiIngredientGroup<T> group;
	private final int slotIndex;

	public JemiSlotWidget(GuiIngredientGroup<T> group, int slotIndex, GuiIngredient<T> ingredient, EmiRecipe recipe) {
		super(JemiUtil.getIngredient(ingredient.getAllIngredients()), ingredient.getRect().getX(), ingredient.getRect().getY());
		this.group = group;
		this.slotIndex = slotIndex;
		this.ingredient = ingredient;
		Rect2i rect = ingredient.getRect();
		this.customBackground(null, 0, 0, rect.getWidth(), rect.getHeight());
		this.drawBack(false);
		if (!ingredient.isInput()) {
			this.recipeContext(recipe);
		}
	}

	@Override
	public void drawStack(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		// Drawn by the JEI recipe layout
	}

	@Override
	public List<TooltipComponent> getTooltip(int mouseX, int mouseY) {
		List<TooltipComponent> list = Lists.newArrayList();
		T displayed = ingredient.getDisplayedIngredient();
		EmiStack stack = JemiUtil.getStack(displayed);
		if (stack.isEmpty()) {
			if (getStack().isEmpty()) {
				return list;
			}
			list.addAll(getStack().getTooltip());
		} else {
			list.addAll(stack.getTooltip());
			addJeiTooltip(list, stack, displayed);
		}
		addSlotTooltip(list);
		return list;
	}

	private void addJeiTooltip(List<TooltipComponent> list, EmiStack stack, T displayed) {
		List<ITooltipCallback<T>> callbacks = JemiInternals.getTooltipCallbacks(group);
		if (callbacks.isEmpty()) {
			return;
		}
		try {
			List<Text> original = stack.getTooltipText();
			Set<String> existing = Sets.newHashSet();
			for (Text t : original) {
				existing.add(t.getString());
			}
			List<Text> event = Lists.newArrayList(original);
			for (ITooltipCallback<T> callback : callbacks) {
				callback.onTooltip(slotIndex, ingredient.isInput(), displayed, event);
			}
			List<TooltipComponent> extra = event.stream().filter(t -> !existing.contains(t.getString()))
				.map(t -> TooltipComponent.of(t.asOrderedText())).collect(Collectors.toList());
			list.addAll(Math.min(list.size(), 1), extra);
		} catch (Throwable t) {
			EmiLog.error("[JEMI] Error building JEI tooltip", t);
		}
	}
}
