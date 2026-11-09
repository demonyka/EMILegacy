package dev.emi.emi.mixin.jei;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.jemi.JemiPlugin;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.util.Identifier;

/**
 * Sends every request to open JEI 7's recipe screen to EMI: clickable areas of machine screens
 * (progress arrows), JEI's own key binds and mods that open recipes through the JEI API.
 * JEI's screen still opens for what EMI cannot show.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public class RecipesGuiMixin {

	@Inject(method = "show(Lmezz/jei/api/recipe/IFocus;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void emi$show(IFocus<?> focus, CallbackInfo info) {
		if (JemiPlugin.showFocus(focus)) {
			info.cancel();
		}
	}

	@Inject(method = "showCategories(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void emi$showCategories(List<Identifier> uids, CallbackInfo info) {
		if (JemiPlugin.showCategories(uids)) {
			info.cancel();
		}
	}
}
