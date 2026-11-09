package dev.emi.emi.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.screen.Screen;

/**
 * Hides JEI 7's ingredient list overlay (and its config button, search bar and input handling).
 * JEI 7 has no API to replace its runtime UI, so every screen is reported to the overlay as
 * "no screen": it never gets gui properties, so it is never displayed and never takes input.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public class IngredientListOverlayMixin {

	@ModifyVariable(method = "updateScreen", at = @At("HEAD"), argsOnly = true, require = 0, remap = false)
	private Screen emi$hideOverlay(Screen screen) {
		return null;
	}
}
