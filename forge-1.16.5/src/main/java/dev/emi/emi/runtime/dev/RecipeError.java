package dev.emi.emi.runtime.dev;


import com.github.bsideup.jabel.Desugar;
import java.util.List;

import net.minecraft.client.gui.tooltip.TooltipComponent;

@Desugar


public record RecipeError(Severity severity, List<TooltipComponent> tooltip) {
	
	public static enum Severity {
		ERROR,
		WARNING
	}
}
