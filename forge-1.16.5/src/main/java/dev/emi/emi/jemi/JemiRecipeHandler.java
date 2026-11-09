package dev.emi.emi.jemi;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.backport.TooltipComponent;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.ingredient.IGuiIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.transfer.RecipeTransferErrorInternal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

/**
 * EMI fill button backed by a JEI 7 recipe transfer handler. The JEI handler works on a JEI recipe
 * layout, so one is built for the raw recipe: the wrapped JEI recipe for {@link JemiRecipe}s, or
 * the backing vanilla recipe for EMI's own recipes in categories mapped to JEI vanilla categories.
 */
public class JemiRecipeHandler<T extends ScreenHandler, R> implements EmiRecipeHandler<T> {
	private static final int COSMETIC_COLOR = 0x80FFA500;
	private static final int MISSING_COLOR = 0x66FF0000;
	private final IRecipeTransferHandler<T> handler;
	private final IRecipeCategory<R> category;
	private Object cachedRecipe;
	private RecipeLayout<R> cachedLayout;

	public JemiRecipeHandler(IRecipeTransferHandler<T> handler, IRecipeCategory<R> category) {
		this.handler = handler;
		this.category = category;
	}

	@Override
	public EmiPlayerInventory getInventory(HandledScreen<T> screen) {
		return new EmiPlayerInventory(ImmutableList.of());
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		return getRawRecipe(recipe) != null && recipe.supportsRecipeTree();
	}

	@Override
	public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
		return allowsTransfer(transfer(recipe, context, false, null));
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
		boolean success = allowsTransfer(transfer(recipe, context, true, null));
		if (success) {
			MinecraftClient.getInstance().openScreen(context.getScreen());
		}
		return success;
	}

	@Override
	public List<TooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
		IRecipeTransferError err = transfer(recipe, context, false, null);
		if (err == null) {
			return ImmutableList.of();
		}
		List<Text> message = JemiInternals.getErrorMessage(err);
		if (!message.isEmpty()) {
			return message.stream().map(t -> TooltipComponent.of(t.asOrderedText())).collect(Collectors.toList());
		}
		return EmiRecipeHandler.super.getTooltip(recipe, context);
	}

	@Override
	public void render(EmiRecipe recipe, EmiCraftContext<T> context, List<Widget> widgets, MatrixStack matrices) {
		EmiDrawContext draw = EmiDrawContext.wrap(matrices);
		RecipeLayout<R> layout = createLayout(recipe);
		IRecipeTransferError err = transfer(recipe, context, false, layout);
		if (err == null) {
			return;
		}
		if (err.getType() == IRecipeTransferError.Type.COSMETIC) {
			for (Widget widget : widgets) {
				if (widget instanceof RecipeFillButtonWidget) {
					Bounds b = widget.getBounds();
					draw.fill(b.left(), b.top(), b.width(), b.height(), COSMETIC_COLOR);
				}
			}
		}
		// Slot highlights only line up when EMI displays the JEI layout itself
		if (layout != null && recipe instanceof JemiRecipe) {
			try {
				for (Integer slot : JemiInternals.getErrorSlots(err)) {
					IGuiIngredient<ItemStack> ingredient = layout.getItemStacks().getGuiIngredients().get(slot);
					if (ingredient != null) {
						ingredient.drawHighlight(draw.raw(), MISSING_COLOR, 0, 0);
					}
				}
			} catch (Throwable t) {
			}
		}
	}

	private static boolean allowsTransfer(IRecipeTransferError err) {
		return err == null || err.getType() == IRecipeTransferError.Type.COSMETIC;
	}

	private IRecipeTransferError transfer(EmiRecipe recipe, EmiCraftContext<T> context, boolean doTransfer, RecipeLayout<R> layout) {
		try {
			R raw = getRawRecipe(recipe);
			if (raw == null) {
				return RecipeTransferErrorInternal.INSTANCE;
			}
			if (layout == null) {
				layout = getLayout(raw);
			}
			if (layout == null) {
				return RecipeTransferErrorInternal.INSTANCE;
			}
			MinecraftClient client = MinecraftClient.getInstance();
			return handler.transferRecipe(context.getScreenHandler(), raw, layout, client.player, context.getAmount() > 1, doTransfer);
		} catch (Throwable t) {
			EmiLog.error("[JEMI] Error executing JEI recipe transfer", t);
		}
		return RecipeTransferErrorInternal.INSTANCE;
	}

	private RecipeLayout<R> createLayout(EmiRecipe recipe) {
		R raw = getRawRecipe(recipe);
		return raw == null ? null : getLayout(raw);
	}

	// canCraft/getTooltip/render are queried every frame for the hovered recipe, so reuse the last layout
	private RecipeLayout<R> getLayout(R raw) {
		if (raw != cachedRecipe || cachedLayout == null) {
			cachedLayout = JemiInternals.createLayout(category, raw);
			cachedRecipe = raw;
		}
		return cachedLayout;
	}

	private R getRawRecipe(EmiRecipe recipe) {
		try {
			Class<? extends R> clazz = category.getRecipeClass();
			if (recipe instanceof JemiRecipe<?> jr && jr.category == category) {
				return clazz.cast(jr.recipe);
			}
			Recipe<?> backing = recipe.getBackingRecipe();
			if (backing != null && clazz.isInstance(backing)) {
				return clazz.cast(backing);
			}
		} catch (Throwable t) {
		}
		return null;
	}
}
