package dev.emi.emi.bom;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiTagKey;
import net.minecraft.item.Item;
import net.minecraft.recipe.Recipe;
import net.minecraft.util.Identifier;

public class MaterialTree {
	// Above this, a tag is a class of materials (any dye, any wool) and not a choice between equivalents
	private static final int MAX_TAG_CHOICES = 64;
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
	 * Resolves the tree down to base resources: every ingredient without a recipe gets the best recipe that
	 * makes it, level by level, until only ingredients that cannot sensibly be crafted are left.
	 * See {@link #getCandidates} for what counts as sensible. Ingredients the player cleared by hand stay cleared.
	 * @return how many ingredients got a recipe
	 */
	public int expandAll() {
		Search search = new Search(EmiTagKey.of(EmiPort.getItemRegistry(), EmiPort.id("forge", "ores")).getSet());
		int added = 0;
		for (int pass = 0; pass < 64 && added < 1024; pass++) {
			Map<EmiIngredient, EmiRecipe> found = Maps.newHashMap();
			collectExpansions(goal, Lists.newArrayList(), found, search);
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

	private void collectExpansions(MaterialNode node, List<EmiStack> ancestors, Map<EmiIngredient, EmiRecipe> found, Search search) {
		if (node.recipe == null) {
			// Not ones the player cleared on purpose, and not ones only cut off to break a cycle
			if (!resolutions.containsKey(node.ingredient) && !found.containsKey(node.ingredient) && BoM.getRecipe(node.ingredient) == null) {
				EmiRecipe recipe = getExpansion(node.ingredient, ancestors, search);
				if (recipe != null) {
					found.put(node.ingredient, recipe);
				}
			}
			return;
		}
		if (node.children == null) {
			return;
		}
		int size = ancestors.size();
		ancestors.addAll(node.ingredient.getEmiStacks());
		for (MaterialNode child : node.children) {
			collectExpansions(child, ancestors, found, search);
		}
		while (ancestors.size() > size) {
			ancestors.remove(ancestors.size() - 1);
		}
	}

	private static @Nullable EmiRecipe getExpansion(EmiIngredient ingredient, List<EmiStack> ancestors, Search search) {
		List<EmiStack> stacks = ingredient.getEmiStacks();
		if (ingredient.isEmpty() || stacks.isEmpty()) {
			return null;
		}
		if (stacks.size() == 1) {
			return search.find(stacks.get(0), ancestors, 0);
		}
		// A tag or a list of alternatives. If one of them is a base resource, the whole ingredient is one
		// ("any log", "any dye"). If all of them have to be made, make the first.
		if (stacks.size() > MAX_TAG_CHOICES) {
			return null;
		}
		for (EmiStack stack : stacks) {
			if (BoM.getRecipe(stack) == null && search.find(stack, ancestors, 0) == null) {
				return null;
			}
		}
		return new EmiResolutionRecipe(ingredient, stacks.get(0));
	}

	/** Looks for recipes that lead to base resources without going in circles. */
	private static class Search {
		// Recipe graphs of big packs are dense, the search gives up (and accepts what it has) rather than hang
		private static final int MAX_DEPTH = 10, MAX_STEPS = 50_000;
		private static final int NO_LOOP = Integer.MAX_VALUE;
		private final Set<Item> ores;
		private int steps = 0;

		Search(Set<Item> ores) {
			this.ores = ores;
		}

		/**
		 * @return the recipe to make the stack with, or null: a base resource, or one that can only be made
		 *  from itself or from what is being made further up ({@code ancestors})
		 */
		@Nullable EmiRecipe find(EmiStack stack, List<EmiStack> ancestors, int depth) {
			for (EmiRecipe recipe : getCandidates(stack, ores)) {
				if (getLoop(recipe, stack, ancestors, depth) == NO_LOOP) {
					return recipe;
				}
			}
			return null;
		}

		/**
		 * Whether making the stack with this recipe leads back to something that is being made already.
		 * @return the position in {@code ancestors} (with the stack appended) the loop closes at, or NO_LOOP
		 */
		private int getLoop(EmiRecipe recipe, EmiStack stack, List<EmiStack> ancestors, int depth) {
			int size = ancestors.size();
			ancestors.add(stack);
			try {
				int loop = NO_LOOP;
				for (EmiIngredient input : recipe.getInputs()) {
					if (!input.isEmpty()) {
						loop = Math.min(loop, getLoop(input, ancestors, depth + 1));
					}
				}
				return loop;
			} finally {
				while (ancestors.size() > size) {
					ancestors.remove(ancestors.size() - 1);
				}
			}
		}

		private int getLoop(EmiIngredient input, List<EmiStack> ancestors, int depth) {
			List<EmiStack> stacks = input.getEmiStacks();
			if (stacks.size() != 1) {
				// Alternatives: a loop only if every one of them is being made already
				int loop = NO_LOOP;
				for (EmiStack stack : stacks) {
					int index = indexOf(ancestors, stack);
					if (index < 0) {
						return NO_LOOP;
					}
					loop = Math.min(loop, index);
				}
				return loop;
			}
			EmiStack stack = stacks.get(0);
			int index = indexOf(ancestors, stack);
			if (index >= 0) {
				return index;
			}
			if (depth > MAX_DEPTH || ++steps > MAX_STEPS) {
				return NO_LOOP;
			}
			EmiRecipe preset = BoM.getRecipe(stack);
			List<EmiRecipe> candidates = preset != null ? Lists.newArrayList(preset) : getCandidates(stack, ores);
			// The position this stack takes while its recipes are followed
			int self = ancestors.size();
			int above = NO_LOOP;
			for (EmiRecipe recipe : candidates) {
				int loop = getLoop(recipe, stack, ancestors, depth);
				if (loop >= self) {
					// A way without loops. Or one that only needs the stack itself (a press copied with a press):
					// then it is a base resource here, obtained some other way
					return NO_LOOP;
				}
				above = Math.min(above, loop);
			}
			// No recipes: a base resource. Otherwise every way to make it needs something further up
			return above;
		}

		private static int indexOf(List<EmiStack> stacks, EmiStack stack) {
			for (int i = 0; i < stacks.size(); i++) {
				if (stacks.get(i).isEqual(stack)) {
					return i;
				}
			}
			return -1;
		}
	}

	/**
	 * The recipes worth following to make a stack, best first. Left out:
	 * <ul>
	 * <li>recipes of other mods than the one the item is from: packs are full of alternative sources
	 *  (essences, compat machines), the mod of the item says how it is meant to be made</li>
	 * <li>ore processing: an ingot, gem or dust is the resource, not one of the many ways to get it from ore</li>
	 * <li>unpacking: ingots from a storage block or nuggets, an ingot from its own dust</li>
	 * </ul>
	 * A crafting table recipe comes first, then the recipe with the fewest kinds of inputs.
	 */
	private static List<EmiRecipe> getCandidates(EmiStack stack, Set<Item> ores) {
		Identifier stackId = stack.getId();
		List<EmiRecipe> candidates = Lists.newArrayList();
		if (stackId == null) {
			return candidates;
		}
		for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipesByOutput(stack)) {
			if (!recipe.supportsRecipeTree() || BoM.disabledRecipes.contains(recipe) || recipe.getCategory() == null
					|| recipe.getOutputs().stream().noneMatch(o -> o.isEqual(stack))) {
				continue;
			}
			if (!stackId.getNamespace().equals(getNamespace(recipe))) {
				continue;
			}
			if (usesOre(recipe, ores) || isUnpacking(recipe, stack)) {
				continue;
			}
			candidates.add(recipe);
		}
		candidates.sort(Comparator
			.<EmiRecipe>comparingInt(r -> r.getCategory() == VanillaEmiRecipeCategories.CRAFTING ? 0 : 1)
			.thenComparingInt(r -> (int) r.getInputs().stream().filter(i -> !i.isEmpty()).count())
			.thenComparing(r -> r.getId() == null ? "~" : r.getId().toString()));
		return candidates;
	}

	/** The mod (or datapack) a recipe comes from. Recipes read from JEI have ids made by EMI, their real recipe tells. */
	private static String getNamespace(EmiRecipe recipe) {
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing != null && backing.getId() != null) {
			return backing.getId().getNamespace();
		}
		Identifier id = recipe.getId();
		if (id != null && !id.getNamespace().equals("jei")) {
			return id.getNamespace();
		}
		return recipe.getCategory().getId().getNamespace();
	}

	private static boolean usesOre(EmiRecipe recipe, Set<Item> ores) {
		for (EmiIngredient input : recipe.getInputs()) {
			for (EmiStack in : input.getEmiStacks()) {
				if (ores.contains(in.getItemStack().getItem())) {
					return true;
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
		// Only by the recipes of the mod of that input: crushers of other mods turn most things back
		String namespace = only.getId() == null ? "" : only.getId().getNamespace();
		for (EmiRecipe reverse : EmiApi.getRecipeManager().getRecipesByOutput(only)) {
			if (reverse.getCategory() == null || !namespace.equals(getNamespace(reverse))) {
				continue;
			}
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

	public void calculateProgress(EmiPlayerInventory inventory) {
		cost.calculateProgress(goal, batches, inventory);
	}

	public void calculateCost() {
		cost.calculate(goal, batches);
	}
}
