package dev.emi.emi.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Mouse;

/** Used by the development test harness ({@link dev.emi.emi.runtime.dev.EmiDevScript}) to inject input. */
@Mixin(Mouse.class)
public interface MouseAccessor {

	@Invoker("onMouseButton")
	void emi$onMouseButton(long window, int button, int action, int mods);

	@Invoker("onCursorPos")
	void emi$onCursorPos(long window, double x, double y);
}
