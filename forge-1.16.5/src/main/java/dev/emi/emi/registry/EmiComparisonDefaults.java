package dev.emi.emi.registry;


import com.google.common.collect.ImmutableMap;
import java.util.Map;

import dev.emi.emi.api.stack.Comparison;

public class EmiComparisonDefaults {
	public static Map<Object, Comparison> comparisons = ImmutableMap.of();

	public static Comparison get(Object obj) {
		// One read: the reload thread swaps the map
		Comparison comparison = comparisons.get(obj);
		return comparison == null ? Comparison.DEFAULT_COMPARISON : comparison;
	}
}
