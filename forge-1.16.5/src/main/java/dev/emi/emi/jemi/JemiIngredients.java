package dev.emi.emi.jemi;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

/**
 * Captures what a JEI 7 category reports through {@code IRecipeCategory#setIngredients}.
 * Every entry of a per-type list is one slot, holding the alternatives for that slot.
 */
public class JemiIngredients implements IIngredients {
	public final Map<IIngredientType<?>, List<List<?>>> inputs = new LinkedHashMap<>();
	public final Map<IIngredientType<?>, List<List<?>>> outputs = new LinkedHashMap<>();

	@Override
	public void setInputIngredients(List<Ingredient> ingredients) {
		List<List<ItemStack>> lists = Lists.newArrayList();
		for (Ingredient ingredient : ingredients) {
			ItemStack[] stacks = ingredient == null ? new ItemStack[0] : ingredient.getMatchingStacksClient();
			lists.add(Arrays.asList(stacks));
		}
		setInputLists(VanillaTypes.ITEM, lists);
	}

	@Override
	public <T> void setInput(IIngredientType<T> type, T ingredient) {
		setInputLists(type, Collections.singletonList(Collections.singletonList(ingredient)));
	}

	@Override
	public <T> void setInputs(IIngredientType<T> type, List<T> ingredients) {
		setInputLists(type, ingredients.stream().map(Collections::singletonList).collect(Collectors.toList()));
	}

	@Override
	public <T> void setInputLists(IIngredientType<T> type, List<List<T>> ingredients) {
		inputs.put(type, copy(ingredients));
	}

	@Override
	public <T> void setOutput(IIngredientType<T> type, T ingredient) {
		setOutputLists(type, Collections.singletonList(Collections.singletonList(ingredient)));
	}

	@Override
	public <T> void setOutputs(IIngredientType<T> type, List<T> ingredients) {
		setOutputLists(type, ingredients.stream().map(Collections::singletonList).collect(Collectors.toList()));
	}

	@Override
	public <T> void setOutputLists(IIngredientType<T> type, List<List<T>> ingredients) {
		outputs.put(type, copy(ingredients));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<List<T>> getInputs(IIngredientType<T> type) {
		List<List<?>> list = inputs.get(type);
		return list == null ? Collections.emptyList() : (List<List<T>>) (Object) list;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<List<T>> getOutputs(IIngredientType<T> type) {
		List<List<?>> list = outputs.get(type);
		return list == null ? Collections.emptyList() : (List<List<T>>) (Object) list;
	}

	private static <T> List<List<?>> copy(List<List<T>> ingredients) {
		List<List<?>> list = Lists.newArrayList();
		if (ingredients != null) {
			for (List<T> l : ingredients) {
				list.add(l == null ? Collections.emptyList() : Lists.newArrayList(l));
			}
		}
		return list;
	}
}
