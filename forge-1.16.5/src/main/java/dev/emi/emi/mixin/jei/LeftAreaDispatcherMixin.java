package dev.emi.emi.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.screen.Screen;

/**
 * Hides JEI 7's left area (bookmark overlay) the same way as {@link IngredientListOverlayMixin}.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.LeftAreaDispatcher", remap = false)
public class LeftAreaDispatcherMixin {

	@ModifyVariable(method = "updateScreen", at = @At("HEAD"), argsOnly = true, require = 0, remap = false)
	private Screen emi$hideBookmarks(Screen screen) {
		return null;
	}
}
