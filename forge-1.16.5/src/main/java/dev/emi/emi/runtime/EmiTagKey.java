package dev.emi.emi.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.registry.EmiTags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.fluid.Fluid;
import dev.emi.emi.backport.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.tag.ServerTagManagerHolder;
import net.minecraft.tag.Tag;
import net.minecraft.tag.TagGroup;
import net.minecraft.tag.TagManager;

// Wrapper around TagKeys
public class EmiTagKey<T> {
	public static final Map<TagKey<?>, EmiTagKey<?>> CACHE = Maps.newHashMap();
	private final TagKey<T> raw;
	private List<T> cached;
	
	private EmiTagKey(TagKey<T> raw) {
		this.raw = raw;
		recalculate();
	}

	public void recalculate() {
		cached = stream().collect(Collectors.toList());
	}

	public TagKey<T> raw() {
		return raw;
	}

	public boolean isOf(Registry<?> registry) {
		return raw.isOf(registry.getKey());
	}

	public Identifier id() {
		return raw.id();
	}

	@SuppressWarnings("unchecked")
	public Registry<T> registry() {
		return (Registry<T>) Registry.REGISTRIES.get(raw.registry().getValue());
	}

	public Stream<T> stream() {
		TagGroup<T> group = getGroup(raw.registry().getValue());
		Tag<T> tag = group == null ? null : group.getTag(raw.id());
		if (tag == null) {
			return Stream.empty();
		}
		if (raw.registry().getValue().equals(Registry.FLUID_KEY.getValue())) {
			return tag.values().stream().filter(o -> {
				Fluid f = (Fluid) o;
				return f.isStill(f.getDefaultState());
			});
		}
		return tag.values().stream();
	}

	public boolean contains(T t) {
		TagGroup<T> group = getGroup(raw.registry().getValue());
		Tag<T> tag = group == null ? null : group.getTag(raw.id());
		return tag != null && tag.contains(t);
	}

	/** 1.16 keeps tags per known registry in the tag manager instead of in the registry itself. */
	@SuppressWarnings("unchecked")
	private static <T> @Nullable TagGroup<T> getGroup(Identifier registry) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		// With an integrated server the client handler only gets its tags after the recipes packet, which is
		// what starts an EMI reload: its manager is still empty then. The server's tags are bound earlier.
		TagManager manager = handler == null || client.isIntegratedServerRunning()
			? ServerTagManagerHolder.getTagManager() : handler.getTagManager();
		if (registry.equals(Registry.ITEM_KEY.getValue())) {
			return (TagGroup<T>) manager.getItems();
		} else if (registry.equals(Registry.BLOCK_KEY.getValue())) {
			return (TagGroup<T>) manager.getBlocks();
		} else if (registry.equals(Registry.FLUID_KEY.getValue())) {
			return (TagGroup<T>) manager.getFluids();
		} else if (registry.equals(Registry.ENTITY_TYPE_KEY.getValue())) {
			return (TagGroup<T>) manager.getEntityTypes();
		}
		return null;
	}

	public List<T> getList() {
		return cached;
	}

	public Set<T> getSet() {
		return stream().collect(Collectors.toSet());
	}

	public Text getTagName() {
		String s = getTagTranslationKey();
		if (s == null) {
			return EmiPort.literal(getReadableName(this.id()));
		} else {
			return EmiPort.translatable(s);
		}
	}

	/** "forge:dusts/quartz" becomes "Quartz Dusts": mods rarely ship names for their tags. */
	private static String getReadableName(Identifier id) {
		String[] parts = id.getPath().split("/");
		StringBuilder sb = new StringBuilder();
		for (int i = parts.length - 1; i >= 0; i--) {
			for (String word : parts[i].split("_")) {
				if (word.isEmpty()) {
					continue;
				}
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return sb.length() == 0 ? "#" + id : sb.toString();
	}

	public boolean hasTranslation() {
		return getTagTranslationKey() != null;
	}

	private @Nullable String getTagTranslationKey() {
		Identifier registry = raw.registry().getValue();
		if (registry.getNamespace().equals("minecraft")) {
			String s = translatePrefix("tag." + registry.getPath().replace("/", ".") + ".", this.id());
			if (s != null) {
				return s;
			}
		} else {
			String s = translatePrefix("tag." + registry.getNamespace() + "." + registry.getPath().replace("/", ".") + ".", this.id());
			if (s != null) {
				return s;
			}
		}
		return translatePrefix("tag.", this.id());
	}

	private static @Nullable String translatePrefix(String prefix, Identifier id) {
		String s = EmiUtil.translateId(prefix, id);
		if (I18n.hasTranslation(s)) {
			return s;
		}
		if (id.getNamespace().equals("forge")) {
			s = EmiUtil.translateId(prefix, EmiPort.id("c", id.getPath()));
			if (I18n.hasTranslation(s)) {
				return s;
			}
		}
		return null;
	}

	public @Nullable Identifier getCustomModel() {
		Identifier rid = this.id();
		if (rid.getNamespace().equals("forge") && !EmiTags.MODELED_TAGS.containsKey(raw())) {
			return EmiTagKey.of(registry(), EmiPort.id("c", rid.getPath())).getCustomModel();
		}
		return EmiTags.MODELED_TAGS.get(raw());
	}

	public boolean hasCustomModel() {
		return getCustomModel() != null;
	}

	@Override
	public int hashCode() {
		return raw().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof EmiTagKey other && raw().equals(other.raw());
	}

	@SuppressWarnings("unchecked")
	public static <T> EmiTagKey<T> of(TagKey<T> raw) {
		return (EmiTagKey<T>) CACHE.computeIfAbsent(raw, k -> new EmiTagKey<>(k));
	}

	public static <T> EmiTagKey<T> of(Registry<T> registry, Identifier id) {
		return of(TagKey.of(registry.getKey(), id));
	}

	public static <T> Stream<EmiTagKey<T>> fromRegistry(Registry<T> registry) {
		TagGroup<T> group = getGroup(registry.getKey().getValue());
		if (group == null) {
			return Stream.empty();
		}
		return group.getTagIds().stream().map(id -> of(registry, id));
	}

	public static void reload() {
		for (EmiTagKey<?> key : CACHE.values()) {
			key.recalculate();
		}
	}
}
