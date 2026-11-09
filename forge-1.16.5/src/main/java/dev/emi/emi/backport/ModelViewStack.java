package dev.emi.emi.backport;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;

/**
 * Stand-in for RenderSystem.getModelViewStack() from 1.17: operations go straight
 * to the 1.16 GL matrix stack, so applyModelViewMatrix() has nothing to do.
 */
public final class ModelViewStack extends MatrixStack {
	public static final ModelViewStack INSTANCE = new ModelViewStack();

	private ModelViewStack() {
	}

	@Override
	public void push() {
		RenderSystem.pushMatrix();
	}

	@Override
	public void pop() {
		RenderSystem.popMatrix();
	}

	@Override
	public void translate(double x, double y, double z) {
		RenderSystem.translated(x, y, z);
	}

	@Override
	public void scale(float x, float y, float z) {
		RenderSystem.scalef(x, y, z);
	}

	public void multiplyPositionMatrix(Matrix4f matrix) {
		RenderSystem.multMatrix(matrix);
	}
}
