package dev.emi.emi.jemi;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Sets;

import dev.emi.emi.api.forge.ForgeEmiStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.platform.EmiAgnos;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Conversion between JEI 7 ingredients and EMI stacks.
 * JEI 7 has no ITypedIngredient: ingredients are raw objects whose type is resolved through the
 * ingredient manager. Items are {@link ItemStack}, fluids are Forge {@link FluidStack}s,
 * anything else (gases, infusion types, aspects, ...) becomes a {@link JemiStack}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JemiUtil {

	public static EmiIngredient getIngredient(@Nullable List<?> ingredients) {
		if (ingredients == null || ingredients.isEmpty()) {
			return EmiStack.EMPTY;
		}
		return EmiIngredient.of(ingredients.stream().map(JemiUtil::getStack).filter(i -> !i.isEmpty()).collect(Collectors.toList()));
	}

	public static EmiIngredient getIngredient(IIngredientType<?> type, @Nullable List<?> ingredients) {
		if (ingredients == null || ingredients.isEmpty()) {
			return EmiStack.EMPTY;
		}
		return EmiIngredient.of(ingredients.stream().map(o -> getStack(type, o)).filter(i -> !i.isEmpty()).collect(Collectors.toList()));
	}

	public static EmiStack getStack(@Nullable Object ingredient) {
		if (ingredient == null) {
			return EmiStack.EMPTY;
		} else if (ingredient instanceof ItemStack stack) {
			return EmiStack.of(stack);
		} else if (ingredient instanceof FluidStack fluid) {
			return getFluidStack(fluid);
		}
		IIngredientType type = getType(ingredient);
		if (type != null) {
			return getStack(type, ingredient);
		}
		return EmiStack.EMPTY;
	}

	public static EmiStack getStack(IIngredientType<?> type, @Nullable Object ingredient) {
		if (ingredient == null) {
			return EmiStack.EMPTY;
		}
		if (type == VanillaTypes.ITEM || ingredient instanceof ItemStack) {
			return EmiStack.of((ItemStack) ingredient);
		} else if (type == VanillaTypes.FLUID || ingredient instanceof FluidStack) {
			return getFluidStack((FluidStack) ingredient);
		} else if (JemiPlugin.runtime != null) {
			try {
				IIngredientManager im = JemiPlugin.runtime.getIngredientManager();
				IIngredientHelper helper = im.getIngredientHelper((IIngredientType) type);
				if (helper.isValidIngredient(ingredient)) {
					return new JemiStack(type, helper, im.getIngredientRenderer((IIngredientType) type), ingredient);
				}
			} catch (Throwable t) {
			}
		}
		return EmiStack.EMPTY;
	}

	public static EmiStack getFluidStack(FluidStack fluid) {
		if (fluid == null || fluid.isEmpty()) {
			return EmiStack.EMPTY;
		}
		return ForgeEmiStack.of(fluid);
	}

	/**
	 * Converts an EMI stack back to the JEI representation, or null if it has none.
	 */
	public static @Nullable Object getJeiIngredient(EmiStack stack) {
		if (stack.isEmpty()) {
			return null;
		} else if (stack instanceof JemiStack js) {
			return js.ingredient;
		} else if (stack.getKey() instanceof Fluid f) {
			if (f == Fluids.EMPTY) {
				return null;
			}
			long amount = stack.getAmount() == 0 ? 1000 : stack.getAmount();
			return new FluidStack(f, (int) Math.min(Integer.MAX_VALUE, amount), stack.getNbt());
		}
		ItemStack is = stack.getItemStack();
		return is.isEmpty() ? null : is;
	}

	public static @Nullable IIngredientType<?> getType(Object ingredient) {
		if (ingredient instanceof ItemStack) {
			return VanillaTypes.ITEM;
		} else if (ingredient instanceof FluidStack) {
			return VanillaTypes.FLUID;
		}
		if (JemiPlugin.runtime == null) {
			return null;
		}
		try {
			return JemiPlugin.runtime.getIngredientManager().getIngredientType(ingredient);
		} catch (Throwable t) {
			// JEI 7 throws for unknown ingredient classes
			return null;
		}
	}

	public static Set<String> getHandledMods() {
		Set<String> set = Sets.newHashSet();
		for (String mod : EmiAgnos.getModsWithPlugins()) {
			set.add(mod);
		}
		return set;
	}
}
