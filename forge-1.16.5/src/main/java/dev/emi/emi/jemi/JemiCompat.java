package dev.emi.emi.jemi;

import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.recipe.Ingredient;

/**
 * What JEI categories do not tell: JEI 7 lists everything in the input slots of a recipe as an input,
 * also items that the machine keeps. EMI needs to know, the recipe tree would otherwise count them as cost
 * for every craft. Recipes of other mods are only touched through reflection.
 */
public class JemiCompat {
	private static final String AE2_INSCRIBER = "appeng.recipes.handlers.InscriberRecipe";
	private static boolean failed = false;

	/** Marks the inputs of a JEI recipe that are not used up, by making them their own remainder. */
	public static void markTools(Object recipe, List<EmiIngredient> inputs) {
		if (failed || recipe == null) {
			return;
		}
		try {
			if (recipe.getClass().getName().equals(AE2_INSCRIBER)) {
				markInscriberPresses(recipe, inputs);
			}
		} catch (Throwable t) {
			failed = true;
			EmiLog.error("[JEMI] Could not read recipe details of another mod, its tools will count as materials", t);
		}
	}

	/**
	 * Applied Energistics 2: an inscriber recipe of type INSCRIBE keeps the top and bottom items (the presses),
	 * only the middle one is used up. Type PRESS uses up all three.
	 */
	private static void markInscriberPresses(Object recipe, List<EmiIngredient> inputs) throws Exception {
		Object type = recipe.getClass().getMethod("getProcessType").invoke(recipe);
		if (!(type instanceof Enum) || !((Enum<?>) type).name().equals("INSCRIBE")) {
			return;
		}
		Object middle = recipe.getClass().getMethod("getMiddleInput").invoke(recipe);
		if (!(middle instanceof Ingredient)) {
			return;
		}
		EmiIngredient used = EmiIngredient.of((Ingredient) middle);
		for (EmiIngredient input : inputs) {
			if (EmiIngredient.areEqual(input, used)) {
				continue;
			}
			for (EmiStack stack : input.getEmiStacks()) {
				stack.setRemainder(stack.copy());
			}
		}
	}
}
