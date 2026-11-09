package dev.emi.emi.jemi;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.runtime.EmiLog;
import mezz.jei.Internal;
import mezz.jei.api.gui.ingredient.ITooltipCallback;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ingredients.GuiIngredientGroup;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.ShapelessIcon;
import mezz.jei.recipes.RecipeTransferManager;
import mezz.jei.runtime.JeiRuntime;
import net.minecraft.text.Text;

/**
 * Access to JEI 7 internals that the public API does not expose (slot positions, gui handlers,
 * transfer handlers). Everything is guarded: if JEI changes, the dependent feature silently turns off.
 */
public class JemiInternals {
	private static final Field GUI_SCREEN_HELPER = field("mezz.jei.gui.overlay.IngredientListOverlay", "guiScreenHelper");
	private static final Field TRANSFER_MANAGER = field("mezz.jei.gui.recipes.RecipesGui", "recipeTransferManager");
	private static final Field LAYOUT_GROUPS = field("mezz.jei.gui.recipes.RecipeLayout", "guiIngredientGroups");
	private static final Field LAYOUT_SHAPELESS = field("mezz.jei.gui.recipes.RecipeLayout", "shapelessIcon");
	private static final Field GROUP_TOOLTIPS = field("mezz.jei.gui.ingredients.GuiIngredientGroup", "tooltipCallbacks");
	private static final Field ERROR_MESSAGE = field("mezz.jei.transfer.RecipeTransferErrorTooltip", "message");
	private static final Field ERROR_SLOTS = field("mezz.jei.transfer.RecipeTransferErrorSlots", "slots");

	private static Field field(String clazz, String name) {
		try {
			Field f = Class.forName(clazz).getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable t) {
			EmiLog.warn("[JEMI] Could not access JEI internal field " + clazz + "#" + name);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T get(Field field, Object o) {
		if (field == null || o == null) {
			return null;
		}
		try {
			return (T) field.get(o);
		} catch (Throwable t) {
			return null;
		}
	}

	public static @Nullable JeiRuntime getRuntime() {
		try {
			return Internal.getRuntime();
		} catch (Throwable t) {
			return null;
		}
	}

	public static @Nullable GuiScreenHelper getScreenHelper() {
		JeiRuntime runtime = getRuntime();
		if (runtime == null) {
			return null;
		}
		return get(GUI_SCREEN_HELPER, runtime.getIngredientListOverlay());
	}

	public static @Nullable RecipeTransferManager getTransferManager() {
		JeiRuntime runtime = getRuntime();
		if (runtime == null) {
			return null;
		}
		return get(TRANSFER_MANAGER, runtime.getRecipesGui());
	}

	/**
	 * Creates a recipe layout at 0, 0 without focus and without the transfer button.
	 * The public {@code IRecipeManager#createRecipeLayoutDrawable} requires a non-null focus.
	 */
	public static <T> @Nullable RecipeLayout<T> createLayout(IRecipeCategory<T> category, T recipe) {
		try {
			return RecipeLayout.create(-1, category, recipe, null, Internal.getHelpers().getModIdHelper(), 0, 0);
		} catch (Throwable t) {
			EmiLog.error("[JEMI] Exception creating JEI recipe layout", t);
			return null;
		}
	}

	public static Collection<GuiIngredientGroup<?>> getGroups(RecipeLayout<?> layout) {
		Map<IIngredientType<?>, GuiIngredientGroup<?>> map = get(LAYOUT_GROUPS, layout);
		if (map != null) {
			return Lists.newArrayList(map.values());
		}
		List<GuiIngredientGroup<?>> list = Lists.newArrayList();
		list.add(layout.getItemStacks());
		if (layout.getFluidStacks() instanceof GuiIngredientGroup<?> g) {
			list.add(g);
		}
		return list;
	}

	public static @Nullable ShapelessIcon getShapelessIcon(RecipeLayout<?> layout) {
		return get(LAYOUT_SHAPELESS, layout);
	}

	public static <T> List<ITooltipCallback<T>> getTooltipCallbacks(GuiIngredientGroup<T> group) {
		List<ITooltipCallback<T>> list = get(GROUP_TOOLTIPS, group);
		return list == null ? Collections.emptyList() : list;
	}

	public static List<Text> getErrorMessage(Object error) {
		List<Text> list = get(ERROR_MESSAGE, error);
		return list == null ? Collections.emptyList() : list;
	}

	public static Collection<Integer> getErrorSlots(Object error) {
		Collection<Integer> list = get(ERROR_SLOTS, error);
		return list == null ? Collections.emptyList() : list;
	}
}
