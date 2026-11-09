package dev.emi.emi.jemi;

import java.util.Collections;
import java.util.List;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.gui.GuiScreenHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Rect2i;

/**
 * Lets EMI drag and drop target JEI ghost ingredient handlers (filters, recipe pattern slots, ...).
 */
public class JemiDragDropHandler implements EmiDragDropHandler<Screen> {

	@Override
	public boolean dropStack(Screen screen, EmiIngredient stack, int x, int y) {
		try {
			Object ingredient = getIngredient(stack);
			if (ingredient == null) {
				return false;
			}
			IGhostIngredientHandler<Screen> handler = getHandler(screen);
			if (handler == null) {
				return false;
			}
			for (IGhostIngredientHandler.Target<Object> target : handler.getTargets(screen, ingredient, true)) {
				Rect2i r = target.getArea();
				if (x >= r.getX() && y >= r.getY() && x < r.getX() + r.getWidth() && y < r.getY() + r.getHeight()) {
					target.accept(ingredient);
					handler.onComplete();
					return true;
				}
			}
			handler.onComplete();
		} catch (Throwable t) {
		}
		return false;
	}

	@Override
	public void render(Screen screen, EmiIngredient dragged, MatrixStack matrices, int mouseX, int mouseY, float delta) {
		try {
			Object ingredient = getIngredient(dragged);
			if (ingredient == null) {
				return;
			}
			IGhostIngredientHandler<Screen> handler = getHandler(screen);
			if (handler == null || !handler.shouldHighlightTargets()) {
				return;
			}
			EmiDrawContext context = EmiDrawContext.wrap(matrices);
			for (IGhostIngredientHandler.Target<Object> target : getTargets(handler, screen, ingredient)) {
				Rect2i r = target.getArea();
				context.fill(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 0x8822BB33);
			}
		} catch (Throwable t) {
		}
	}

	private static Object getIngredient(EmiIngredient ingredient) {
		List<EmiStack> stacks = ingredient.getEmiStacks();
		if (stacks.isEmpty()) {
			return null;
		}
		return JemiUtil.getJeiIngredient(stacks.get(0));
	}

	private static List<IGhostIngredientHandler.Target<Object>> getTargets(IGhostIngredientHandler<Screen> handler, Screen screen, Object ingredient) {
		List<IGhostIngredientHandler.Target<Object>> targets = handler.getTargets(screen, ingredient, false);
		return targets == null ? Collections.emptyList() : targets;
	}

	private static IGhostIngredientHandler<Screen> getHandler(Screen screen) {
		GuiScreenHelper helper = JemiInternals.getScreenHelper();
		if (helper == null) {
			return null;
		}
		return helper.getGhostIngredientHandler(screen);
	}
}
