package dev.emi.emi.mixin.accessor;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.HoeItem;

@Mixin(HoeItem.class)
public interface HoeItemAccessor {

	@Accessor("TILLED_BLOCKS")
	public static Map<Block, BlockState> getTilledBlocks() {
		throw new AssertionError();
	}
}
