package dev.emi.emi.data;


import com.github.bsideup.jabel.Desugar;
import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.text.Text;

@Desugar


public record EmiAlias(List<EmiIngredient> stacks, List<String> keys) {

	@Desugar


	public static record Baked(List<EmiIngredient> stacks, List<Text> text) {
	}
}
