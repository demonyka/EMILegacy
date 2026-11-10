package dev.emi.emi.bom;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

public class MaterialTree {
	public MaterialNode goal;
	public TreeCost cost = new TreeCost();
	public Map<EmiIngredient, EmiRecipe> resolutions = Maps.newHashMap();
	public long batches = 1;

	public MaterialTree(EmiRecipe recipe) {
		EmiStack output = recipe.getOutputs().get(0);
		goal = new MaterialNode(output);
		goal.defineRecipe(recipe);
		recalculate();
	}

	public EmiRecipe getRecipe(EmiIngredient stack) {
		EmiRecipe recipe = resolutions.get(stack);
		if (recipe == null && !resolutions.containsKey(stack)) {
			recipe = BoM.getRecipe(stack);
		}
		return recipe;
	}

	public void addResolution(EmiIngredient ingredient, EmiRecipe recipe) {
		resolutions.put(ingredient, recipe);
		if (ingredient.equals(goal.ingredient)) {
			goal.defineRecipe(recipe);
			goal.amount = ingredient.getAmount();
		}
		recalculate();
	}

	public void recalculate() {
		goal.recalculate(this);
	}

	/**
	 * Resolves every ingredient of the tree that has no recipe yet and exactly one sensible way to make it,
	 * level by level until nothing is left to resolve. Ingredients with several ways (ingots from ore, dust,
	 * blocks...) and tags stay as they are, for the player to choose.
	 * @return how many ingredients got a recipe
	 */
	public int expandAll() {
		int added = 0;
		for (int pass = 0; pass < 64 && added < 1024; pass++) {
			Map<EmiIngredient, EmiRecipe> found = Maps.newHashMap();
			collectExpansions(goal, Lists.newArrayList(), found);
			if (found.isEmpty()) {
				break;
			}
			resolutions.putAll(found);
			added += found.size();
			recalculate();
		}
		return added;
	}

	/** Back to what the default recipes give: forgets everything chosen for this tree. */
	public void resetResolutions() {
		resolutions.clear();
		goal.defineRecipe(goal.recipe);
		recalculate();
	}

	private void collectExpansions(MaterialNode node, List<EmiIngredient> ancestors, Map<EmiIngredient, EmiRecipe> found) {
		if (node.recipe == null) {
			// Not ones the player cleared on purpose, and not ones only cut off to break a cycle
			if (!resolutions.containsKey(node.ingredient) && !found.containsKey(node.ingredient) && BoM.getRecipe(node.ingredient) == null) {
				EmiRecipe recipe = getUnambiguousRecipe(node.ingredient, ancestors);
				if (recipe != null) {
					found.put(node.ingredient, recipe);
				}
			}
			return;
		}
		if (node.children == null) {
			return;
		}
		ancestors.add(node.ingredient);
		for (MaterialNode child : node.children) {
			collectExpansions(child, ancestors, found);
		}
		ancestors.remove(ancestors.size() - 1);
	}

	private static EmiRecipe getUnambiguousRecipe(EmiIngredient ingredient, List<EmiIngredient> ancestors) {
		if (ingredient.isEmpty() || ingredient.getEmiStacks().size() != 1) {
			return null;
		}
		EmiStack stack = ingredient.getEmiStacks().get(0);
		List<EmiRecipe> candidates = Lists.newArrayList();
		outer:
		for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipesByOutput(stack)) {
			if (!recipe.supportsRecipeTree() || BoM.disabledRecipes.contains(recipe)
					|| recipe.getOutputs().stream().noneMatch(o -> o.isEqual(stack))) {
				continue;
			}
			if (consumes(recipe, stack, ancestors) || isUnpacking(recipe, stack)) {
				continue;
			}
			// The same recipe registered twice is one way, not two
			for (EmiRecipe other : candidates) {
				if (other.getCategory() == recipe.getCategory() && sameInputs(other, recipe)) {
					continue outer;
				}
			}
			candidates.add(recipe);
		}
		if (candidates.size() == 1) {
			return candidates.get(0);
		}
		// A single crafting table recipe next to machine alternatives
		EmiRecipe crafting = null;
		for (EmiRecipe recipe : candidates) {
			if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
				if (crafting != null) {
					return null;
				}
				crafting = recipe;
			}
		}
		return crafting;
	}

	/** Whether the recipe needs the stack itself or something further up in the tree: a cycle. */
	private static boolean consumes(EmiRecipe recipe, EmiStack stack, List<EmiIngredient> ancestors) {
		for (EmiIngredient input : recipe.getInputs()) {
			for (EmiStack in : input.getEmiStacks()) {
				if (in.isEqual(stack)) {
					return true;
				}
				for (EmiIngredient ancestor : ancestors) {
					for (EmiStack a : ancestor.getEmiStacks()) {
						if (in.isEqual(a)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** Ingots from a storage block or from nuggets, an ingot from its dust: the input is itself made from the stack alone. */
	private static boolean isUnpacking(EmiRecipe recipe, EmiStack stack) {
		EmiStack only = null;
		for (EmiIngredient input : recipe.getInputs()) {
			if (input.isEmpty()) {
				continue;
			}
			if (input.getEmiStacks().size() != 1) {
				return false;
			}
			EmiStack in = input.getEmiStacks().get(0);
			if (only != null && !only.isEqual(in)) {
				return false;
			}
			only = in;
		}
		if (only == null) {
			return false;
		}
		for (EmiRecipe reverse : EmiApi.getRecipeManager().getRecipesByOutput(only)) {
			boolean any = false, all = true;
			for (EmiIngredient input : reverse.getInputs()) {
				if (input.isEmpty()) {
					continue;
				}
				any = true;
				if (input.getEmiStacks().stream().noneMatch(s -> s.isEqual(stack))) {
					all = false;
					break;
				}
			}
			if (any && all) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameInputs(EmiRecipe a, EmiRecipe b) {
		List<EmiIngredient> ia = a.getInputs(), ib = b.getInputs();
		if (ia.size() != ib.size()) {
			return false;
		}
		for (int i = 0; i < ia.size(); i++) {
			if (!EmiIngredient.areEqual(ia.get(i), ib.get(i))) {
				return false;
			}
		}
		return true;
	}

	public void calculateProgress(EmiPlayerInventory inventory) {
		cost.calculateProgress(goal, batches, inventory);
	}

	public void calculateCost() {
		cost.calculate(goal, batches);
	}
}
