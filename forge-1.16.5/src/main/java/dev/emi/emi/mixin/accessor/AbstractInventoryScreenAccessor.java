package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;

@Mixin(AbstractInventoryScreen.class)
public interface AbstractInventoryScreenAccessor {

	@Accessor("drawStatusEffects")
	boolean isEmiDrawingStatusEffects();
}
