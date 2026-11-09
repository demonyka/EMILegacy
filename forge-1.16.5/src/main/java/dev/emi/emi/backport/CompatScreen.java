package dev.emi.emi.backport;

import java.util.List;

import com.google.common.collect.Lists;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * Screen with the 1.17 widget registration methods (addDrawable / addDrawableChild),
 * implemented on top of the 1.16 buttons and children lists.
 */
public abstract class CompatScreen extends Screen {
	private final List<Drawable> drawables = Lists.newArrayList();

	protected CompatScreen(Text title) {
		super(title);
	}

	protected <T extends Drawable> T addDrawable(T drawable) {
		drawables.add(drawable);
		return drawable;
	}

	protected <T extends Element & Drawable> T addDrawableChild(T drawable) {
		if (drawable instanceof ClickableWidget widget) {
			addButton(widget);
		} else {
			drawables.add(drawable);
			children.add(drawable);
		}
		return drawable;
	}

	protected <T extends Element> T addSelectableChild(T child) {
		return addChild(child);
	}

	protected void clearChildren() {
		drawables.clear();
		buttons.clear();
		children.clear();
	}

	@Override
	public void init(MinecraftClient client, int width, int height) {
		drawables.clear();
		super.init(client, width, height);
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		for (Drawable drawable : drawables) {
			drawable.render(matrices, mouseX, mouseY, delta);
		}
		super.render(matrices, mouseX, mouseY, delta);
	}
}
