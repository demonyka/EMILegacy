package dev.emi.emi.jemi;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.backport.TooltipComponent;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.ingredients.GuiIngredientGroup;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.ShapelessIcon;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.recipe.Recipe;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A JEI 7 recipe shown in EMI. Inputs and outputs come from {@code setIngredients}, the display
 * is the JEI recipe layout itself (background, category drawing, slots) with EMI slot widgets on top.
 */
public class JemiRecipe<T> implements EmiRecipe {
	public List<EmiIngredient> inputs = Lists.newArrayList();
	public List<EmiIngredient> catalysts = Lists.newArrayList();
	public List<EmiStack> outputs = Lists.newArrayList();
	public EmiRecipeCategory recipeCategory;
	public Identifier originalId, id;
	public IRecipeCategory<T> category;
	public T recipe;
	public boolean allowTree = true;
	private final int width, height;
	private static JemiRecipe<?> lastLayoutRecipe;
	private static RecipeLayout<?> lastLayout;
	private boolean layoutFailed, renderErrorLogged, tooltipErrorLogged;

	public JemiRecipe(EmiRecipeCategory recipeCategory, IRecipeCategory<T> category, T recipe) {
		this.recipeCategory = recipeCategory;
		this.category = category;
		this.recipe = recipe;
		if (recipe instanceof Recipe<?> r) {
			this.originalId = r.getId();
		}
		if (this.originalId != null) {
			// Category is part of the id: some mods reuse one recipe in several JEI categories
			this.id = EmiPort.id("jei", "/" + EmiUtil.subId(category.getUid()) + "/" + EmiUtil.subId(this.originalId));
		}
		IDrawable background = category.getBackground();
		this.width = background == null ? 0 : background.getWidth();
		this.height = background == null ? 0 : background.getHeight();

		JemiIngredients ingredients = new JemiIngredients();
		category.setIngredients(recipe, ingredients);
		for (Map.Entry<IIngredientType<?>, List<List<?>>> entry : ingredients.inputs.entrySet()) {
			for (List<?> slot : entry.getValue()) {
				EmiIngredient ing = JemiUtil.getIngredient(entry.getKey(), slot);
				if (!ing.isEmpty()) {
					inputs.add(ing);
				}
			}
		}
		for (Map.Entry<IIngredientType<?>, List<List<?>>> entry : ingredients.outputs.entrySet()) {
			for (List<?> slot : entry.getValue()) {
				EmiIngredient ing = JemiUtil.getIngredient(entry.getKey(), slot);
				if (ing.getEmiStacks().size() > 1) {
					allowTree = false;
				}
				for (EmiStack stack : ing.getEmiStacks()) {
					if (!stack.isEmpty()) {
						outputs.add(stack);
					}
				}
			}
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return recipeCategory;
	}

	@Override
	public @Nullable Recipe<?> getBackingRecipe() {
		return recipe instanceof Recipe<?> r ? r : null;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiIngredient> getCatalysts() {
		return catalysts;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	@Override
	public int getDisplayWidth() {
		return width;
	}

	@Override
	public int getDisplayHeight() {
		return height;
	}

	@Override
	public boolean supportsRecipeTree() {
		return allowTree && EmiRecipe.super.supportsRecipeTree();
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		RecipeLayout<T> layout = getLayout();
		if (layout == null) {
			return;
		}
		List<GuiIngredient<?>> slots = Lists.newArrayList();
		List<JemiSlotWidget<?>> slotWidgets = Lists.newArrayList();
		for (GuiIngredientGroup<?> group : JemiInternals.getGroups(layout)) {
			addSlots(group, slots, slotWidgets);
		}
		widgets.add(new JemiWidget(layout, slots));
		for (JemiSlotWidget<?> widget : slotWidgets) {
			widgets.add(widget);
		}
	}

	/**
	 * Recipe tooltips add the widgets of the same recipe every frame. A new JEI layout every frame would run the
	 * mod's layout code each time and restart the cycling of slot alternatives at a random position.
	 */
	@SuppressWarnings("unchecked")
	private @Nullable RecipeLayout<T> getLayout() {
		if (layoutFailed) {
			return null;
		}
		if (lastLayoutRecipe == this && lastLayout != null) {
			return (RecipeLayout<T>) lastLayout;
		}
		RecipeLayout<T> layout = JemiInternals.createLayout(category, recipe);
		if (layout == null) {
			layoutFailed = true;
			return null;
		}
		lastLayoutRecipe = this;
		lastLayout = layout;
		return layout;
	}

	private <V> void addSlots(GuiIngredientGroup<V> group, List<GuiIngredient<?>> slots, List<JemiSlotWidget<?>> slotWidgets) {
		for (Map.Entry<Integer, GuiIngredient<V>> entry : group.getGuiIngredients().entrySet()) {
			GuiIngredient<V> ingredient = entry.getValue();
			slots.add(ingredient);
			try {
				slotWidgets.add(new JemiSlotWidget<V>(group, entry.getKey(), ingredient, this));
			} catch (Throwable t) {
				EmiLog.error("[JEMI] Exception creating slot for JEI recipe", t);
			}
		}
	}

	/**
	 * Draws the JEI recipe layout like {@code RecipeLayout#drawRecipe}, minus JEI's own recipe border
	 * and slot hover highlight (EMI draws both). Mouse coordinates are relative to the recipe origin.
	 */
	public class JemiWidget extends Widget {
		private final RecipeLayout<T> layout;
		private final List<GuiIngredient<?>> slots;
		private final Bounds bounds;

		public JemiWidget(RecipeLayout<T> layout, List<GuiIngredient<?>> slots) {
			this.layout = layout;
			this.slots = slots;
			this.bounds = new Bounds(0, 0, getDisplayWidth(), getDisplayHeight());
		}

		@Override
		public Bounds getBounds() {
			return bounds;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
			EmiDrawContext context = EmiDrawContext.wrap(matrices);
			RenderSystem.color4f(1, 1, 1, 1);
			RenderSystem.disableLighting();
			RenderSystem.enableAlphaTest();
			context.push();
			try {
				IDrawable background = category.getBackground();
				if (background != null) {
					background.draw(context.raw());
				}
				category.draw(recipe, context.raw(), mouseX, mouseY);
				RenderSystem.color4f(1, 1, 1, 1);
				ShapelessIcon shapeless = JemiInternals.getShapelessIcon(layout);
				if (shapeless != null && background != null) {
					shapeless.draw(context.raw(), background.getWidth());
				}
				for (GuiIngredient<?> slot : slots) {
					slot.draw(context.raw(), 0, 0);
				}
			} catch (Throwable t) {
				if (!renderErrorLogged) {
					renderErrorLogged = true;
					EmiLog.error("[JEMI] Exception rendering JEI recipe " + id, t);
				}
			}
			context.pop();
			RenderSystem.color4f(1, 1, 1, 1);
			RenderSystem.disableBlend();
			RenderSystem.disableLighting();
			RenderSystem.enableDepthTest();
		}

		private boolean isOverSlot(int mouseX, int mouseY) {
			for (GuiIngredient<?> slot : slots) {
				Rect2i r = slot.getRect();
				if (mouseX >= r.getX() && mouseY >= r.getY() && mouseX < r.getX() + r.getWidth() && mouseY < r.getY() + r.getHeight()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public List<TooltipComponent> getTooltip(int mouseX, int mouseY) {
			if (isOverSlot(mouseX, mouseY)) {
				return Lists.newArrayList();
			}
			try {
				List<Text> strings = category.getTooltipStrings(recipe, mouseX, mouseY);
				if ((strings == null || strings.isEmpty())) {
					ShapelessIcon shapeless = JemiInternals.getShapelessIcon(layout);
					if (shapeless != null) {
						strings = shapeless.getTooltipStrings(mouseX, mouseY);
					}
				}
				if (strings != null) {
					return strings.stream().map(t -> TooltipComponent.of(t.asOrderedText())).collect(Collectors.toList());
				}
			} catch (Throwable t) {
				if (!tooltipErrorLogged) {
					tooltipErrorLogged = true;
					EmiLog.error("[JEMI] Exception getting JEI recipe tooltip " + id, t);
				}
			}
			return Lists.newArrayList();
		}

		@Override
		public boolean mouseClicked(int mouseX, int mouseY, int button) {
			// JEI 7 categories only receive clicks, there is no key input hook
			try {
				return category.handleClick(recipe, mouseX, mouseY, button);
			} catch (Throwable t) {
				EmiLog.error("[JEMI] Exception handling JEI recipe click", t);
				return false;
			}
		}
	}
}
