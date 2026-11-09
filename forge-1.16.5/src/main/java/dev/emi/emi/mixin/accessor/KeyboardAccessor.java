package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Keyboard;

/** Used by the development test harness ({@link dev.emi.emi.runtime.dev.EmiDevScript}) to inject input. */
@Mixin(Keyboard.class)
public interface KeyboardAccessor {

	@Invoker("onChar")
	void emi$onChar(long window, int codePoint, int modifiers);
}
