package dev.emi.emi.backport;

import java.util.Objects;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

/**
 * Backport of TagKey from Minecraft 1.18.2: a registry + tag id pair.
 * Values are resolved by {@link dev.emi.emi.runtime.EmiTagKey} through the 1.16 tag manager.
 */
public final class TagKey<T> {
	private final RegistryKey<? extends Registry<T>> registry;
	private final Identifier id;

	private TagKey(RegistryKey<? extends Registry<T>> registry, Identifier id) {
		this.registry = registry;
		this.id = id;
	}

	public static <T> TagKey<T> of(RegistryKey<? extends Registry<T>> registry, Identifier id) {
		return new TagKey<>(registry, id);
	}

	public RegistryKey<? extends Registry<T>> registry() {
		return registry;
	}

	public Identifier id() {
		return id;
	}

	public boolean isOf(RegistryKey<? extends Registry<?>> other) {
		return registry.equals(other);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TagKey<?> other && registry.equals(other.registry) && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(registry, id);
	}

	@Override
	public String toString() {
		return "TagKey[" + registry.getValue() + " / " + id + "]";
	}
}
