package dev.emi.emi.jemi;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.backport.TooltipComponent;
import dev.emi.emi.config.EmiConfig;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * An EMI stack wrapping a JEI ingredient of a type EMI does not know natively.
 * JEI 7 renderers are fixed to 16x16 and have no subtype-aware base, so the key is the JEI uid.
 */
public class JemiStack<T> extends EmiStack {
	public final IIngredientType<T> type;
	private final IIngredientHelper<T> helper;
	public final Object base;
	public final T ingredient;
	public IIngredientRenderer<T> renderer;

	public JemiStack(IIngredientType<T> type, IIngredientHelper<T> helper, IIngredientRenderer<T> renderer, T ingredient) {
		this.type = type;
		this.helper = helper;
		this.renderer = renderer;
		this.ingredient = ingredient;
		String uid;
		try {
			uid = helper.getUniqueId(ingredient, UidContext.Recipe);
		} catch (Throwable t) {
			uid = String.valueOf(ingredient);
		}
		this.base = type.getIngredientClass().getName() + ":" + uid;
	}

	public String getJeiUid() {
		return helper.getUniqueId(ingredient, UidContext.Ingredient);
	}

	@Override
	public void render(MatrixStack matrices, int x, int y, float delta, int flags) {
		try {
			renderer.render(matrices, x, y, ingredient);
		} catch (Throwable t) {
		}
	}

	@Override
	public JemiStack<T> copy() {
		JemiStack<T> stack = new JemiStack<T>(type, helper, renderer, helper.copyIngredient(ingredient));
		stack.setAmount(getAmount());
		stack.setChance(getChance());
		stack.setRemainder(getRemainder());
		return stack;
	}

	@Override
	public boolean isEmpty() {
		try {
			return !helper.isValidIngredient(ingredient);
		} catch (Throwable t) {
			return true;
		}
	}

	@Override
	public NbtCompound getNbt() {
		return null;
	}

	@Override
	public Object getKey() {
		return base;
	}

	@Override
	public Identifier getId() {
		String namespace = sanitize(helper.getModId(ingredient));
		String path = sanitize(helper.getResourceId(ingredient));
		try {
			return EmiPort.id(namespace.isEmpty() ? "jei" : namespace, path.isEmpty() ? "unknown" : path);
		} catch (Throwable t) {
			return EmiPort.id("jei", "unknown");
		}
	}

	private static String sanitize(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : s.toLowerCase().toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
				sb.append(c);
			} else {
				sb.append('_');
			}
		}
		return sb.toString();
	}

	@Override
	public List<Text> getTooltipText() {
		try {
			return renderer.getTooltip(ingredient, TooltipContext.Default.NORMAL);
		} catch (Throwable t) {
			return ImmutableList.of(getName());
		}
	}

	@Override
	public List<TooltipComponent> getTooltip() {
		List<TooltipComponent> list = Lists.newArrayList();
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			list.addAll(renderer.getTooltip(ingredient, client.options.advancedItemTooltips ? TooltipContext.Default.ADVANCED : TooltipContext.Default.NORMAL)
				.stream().map(EmiPort::ordered).map(TooltipComponent::of).collect(Collectors.toList()));
		} catch (Throwable t) {
			list.add(TooltipComponent.of(EmiPort.ordered(getName())));
		}

		Identifier id = getId();
		if (EmiConfig.appendModId && id != null) {
			String mod = EmiUtil.getModName(id.getNamespace());
			list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.literal(mod, Formatting.BLUE, Formatting.ITALIC))));
		}

		list.addAll(super.getTooltip());
		return list;
	}

	@Override
	public Text getName() {
		try {
			return EmiPort.literal(helper.getDisplayName(ingredient));
		} catch (Throwable t) {
			return EmiPort.literal(String.valueOf(ingredient));
		}
	}
}
