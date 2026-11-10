package dev.emi.emi.network;


import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dev.emi.emi.runtime.EmiLog;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class FillRecipeC2SPacket implements EmiPacket {
	private final int syncId;
	private final int action;
	private final List<Integer> slots, crafting;
	private final int output;
	private final List<ItemStack> stacks;

	public FillRecipeC2SPacket(ScreenHandler handler, int action, List<Slot> slots, List<Slot> crafting, @Nullable Slot output, List<ItemStack> stacks) {
		this.syncId = handler.syncId;
		this.action = action;
		this.slots = slots.stream().map(s -> s == null ? -1 : s.id).collect(Collectors.toList());
		this.crafting = crafting.stream().map(s -> s == null ? -1 : s.id).collect(Collectors.toList());
		this.output = output == null ? -1 : output.id;
		this.stacks = stacks;
	}

	public FillRecipeC2SPacket(PacketByteBuf buf) {
		syncId = buf.readInt();
		action = buf.readByte();
		// Untrusted input: every list is bounded, an invalid packet leaves slots as null and is dropped in apply
		List<Integer> slots = parseCompressedSlots(buf);
		List<Integer> crafting = Lists.newArrayList();
		List<ItemStack> stacks = Lists.newArrayList();
		int output = -1;
		if (slots != null) {
			int craftingSize = buf.readVarInt();
			if (craftingSize < 0 || craftingSize > MAX_CRAFTING) {
				slots = null;
			} else {
				for (int i = 0; i < craftingSize; i++) {
					crafting.add(buf.readVarInt());
				}
				if (buf.readBoolean()) {
					output = buf.readVarInt();
				}
				int size = buf.readVarInt();
				if (size < 0 || size > craftingSize) {
					slots = null;
				} else {
					for (int i = 0; i < size; i++) {
						stacks.add(buf.readItemStack());
					}
				}
			}
		}
		if (slots == null) {
			// The rest of an invalid packet is not parsed
			buf.readerIndex(buf.writerIndex());
		}
		this.slots = slots;
		this.crafting = crafting;
		this.output = output;
		this.stacks = stacks;
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeInt(syncId);
		buf.writeByte(action);
		writeCompressedSlots(slots, buf);
		buf.writeVarInt(crafting.size());
		for (Integer s : crafting) {
			buf.writeVarInt(s);
		}
		if (output != -1) {
			buf.writeBoolean(true);
			buf.writeVarInt(output);
		} else {
			buf.writeBoolean(false);
		}
		buf.writeVarInt(stacks.size());
		for (ItemStack stack : stacks) {
			buf.writeItemStack(stack);
		}
	}

	@Override
	public void apply(PlayerEntity player) {
		if (slots == null || crafting == null) {
			EmiLog.error("Client requested fill but passed input and crafting slot information was invalid, aborting");
			return;
		}
		if (player.isSpectator()) {
			return;
		}
		ScreenHandler handler = player.currentScreenHandler;
		if (handler == null || handler.syncId != syncId) {
			EmiLog.warn("Client requested fill but screen handler has changed, aborting");
			return;
		}
		if (!handler.canUse(player)) {
			return;
		}
		// A set: repeated slots would only multiply the work
		Set<Slot> slots = Sets.newLinkedHashSet();
		List<Slot> crafting = Lists.newArrayList();
		Slot output = null;
		for (int i : this.slots) {
			if (i < 0 || i >= handler.slots.size()) {
				EmiLog.error("Client requested fill but passed input slots don't exist, aborting");
				return;
			}
			slots.add(handler.slots.get(i));
		}
		for (int i : this.crafting) {
			if (i >= 0 && i < handler.slots.size()) {
				crafting.add(handler.slots.get(i));
			} else {
				crafting.add(null);
			}
		}
		if (this.output != -1) {
			if (this.output >= 0 && this.output < handler.slots.size()) {
				output = handler.slots.get(this.output);
			}
		}
		if (crafting.size() >= stacks.size()) {
			Set<Slot> craftingSet = Sets.newIdentityHashSet();
			for (Slot s : crafting) {
				if (s != null) {
					craftingSet.add(s);
				}
			}
			List<ItemStack> rubble = Lists.newArrayList();
			try {
				for (Slot s : craftingSet) {
					if (s.canTakeItems(player) && !s.getStack().isEmpty()) {
						ItemStack taken = s.getStack();
						ItemStack copy = taken.copy();
						if (takeAll(s, player, taken)) {
							rubble.add(copy);
						}
					}
				}
				for (int i = 0; i < stacks.size(); i++) {
					ItemStack stack = stacks.get(i);
					if (stack.isEmpty()) {
						continue;
					}
					int gotten = grabMatching(player, slots, rubble, craftingSet, stack);
					if (gotten != stack.getCount()) {
						if (gotten > 0) {
							stack.setCount(gotten);
							player.inventory.offerOrDrop(player.world, stack);
						}
						return;
					} else {
						Slot s = crafting.get(i);
						if (s != null && s.canInsert(stack) && stack.getCount() <= s.getMaxItemCount() && stack.getCount() <= stack.getMaxCount()) {
							if (!s.getStack().isEmpty()) { // Make sure we don't accidentally delete any items that could have been placed in this slot
								ItemStack taken = s.getStack();
								ItemStack copy = taken.copy();
								if (s.canTakeItems(player) && takeAll(s, player, taken)) {
									rubble.add(copy);
								} else {
									player.inventory.offerOrDrop(player.world, stack);
									continue;
								}
							}
							try {
								s.setStack(stack);
							} catch (RuntimeException e) {
								rubble.add(stack);
								throw e;
							}
						} else {
							player.inventory.offerOrDrop(player.world, stack);
						}
					}
				}
				if (output != null) {
					if (action == 1) {
						handler.onSlotClick(output.id, 0, SlotActionType.PICKUP, player);
					} else if (action == 2) {
						handler.onSlotClick(output.id, 0, SlotActionType.QUICK_MOVE, player);
					}
				}
			} finally {
				for (ItemStack stack : rubble) {
					player.inventory.offerOrDrop(player.world, stack);
				}
				// 1.16 does not sync the cursor stack on its own
				handler.sendContentUpdates();
				if (player instanceof ServerPlayerEntity spe) {
					spe.updateCursorStack();
				}
			}
		}
	}

	private static final int MAX_SLOTS = 4096;
	private static final int MAX_CRAFTING = 512;

	/** Empties a slot, returns false for slots that keep their stack (ghost and display slots of mods). */
	private static boolean takeAll(Slot s, PlayerEntity player, ItemStack taken) {
		s.setStack(ItemStack.EMPTY);
		if (!s.getStack().isEmpty()) {
			return false;
		}
		s.onTakeItem(player, taken);
		return true;
	}

	private static List<Integer> parseCompressedSlots(PacketByteBuf buf) {
		List<Integer> list = Lists.newArrayList();
		int amount = buf.readVarInt();
		for (int i = 0; i < amount; i++) {
			int low = buf.readVarInt();
			int high = buf.readVarInt();
			// Untrusted input: an unbounded range would allocate until the server runs out of memory
			if (low < 0 || high < low || high - low >= MAX_SLOTS || list.size() + (high - low + 1) > MAX_SLOTS) {
				return null;
			}
			for (int j = low; j <= high; j++) {
				list.add(j);
			}
		}
		return list;
	}
	
	private static void writeCompressedSlots(List<Integer> list, PacketByteBuf buf) {
		List<Consumer<PacketByteBuf>> postWrite = Lists.newArrayList();
		int groups = 0;
		int i = 0;
		while (i < list.size()) {
			groups++;
			int start = i;
			int startValue = list.get(start);
			while (i < list.size() && i - start == list.get(i) - startValue) {
				i++;
			}
			int end = i - 1;
			postWrite.add(b -> {
				b.writeVarInt(startValue);
				b.writeVarInt(list.get(end));
			});
		}
		buf.writeVarInt(groups);
		for (Consumer<PacketByteBuf> consumer : postWrite) {
			consumer.accept(buf);
		}
	}

	private static boolean canCombine(ItemStack a, ItemStack b) {
		return a.getItem() == b.getItem() && ItemStack.areTagsEqual(a, b);
	}

	private static int grabMatching(PlayerEntity player, Set<Slot> slots, List<ItemStack> rubble, Set<Slot> crafting, ItemStack stack) {
		int[] grabbed = new int[1];
		try {
			grabMatching(player, slots, rubble, crafting, stack, grabbed);
		} catch (RuntimeException e) {
			if (grabbed[0] > 0) {
				ItemStack inFlight = stack.copy();
				inFlight.setCount(grabbed[0]);
				rubble.add(inFlight);
			}
			throw e;
		}
		return grabbed[0];
	}

	private static void grabMatching(PlayerEntity player, Set<Slot> slots, List<ItemStack> rubble, Set<Slot> crafting, ItemStack stack, int[] total) {
		int amount = stack.getCount();
				for (int i = 0; i < rubble.size(); i++) {
			if (total[0] >= amount) {
				return;
			}
			ItemStack r = rubble.get(i);
			if (canCombine(stack, r)) {
				int wanted = amount - total[0];
				if (r.getCount() <= wanted) {
					total[0] += r.getCount();
					rubble.remove(i);
					i--;
				} else {
					total[0] = amount;
					r.setCount(r.getCount() - wanted);
				}
			}
		}
		for (Slot s : slots) {
			if (total[0] >= amount) {
				return;
			}
			if (crafting.contains(s) || !s.canTakeItems(player)) {
				continue;
			}
			ItemStack st = s.getStack();
			if (canCombine(stack, st)) {
				int wanted = amount - total[0];
				ItemStack taken = st.copy();
				if (st.getCount() <= wanted) {
					int count = st.getCount();
					if (takeAll(s, player, taken)) {
						total[0] += count;
					}
				} else {
					// Through the slot, so that the backing inventory or item handler is told about the change
					int before = st.getCount();
					ItemStack removed = s.takeStack(wanted);
					// Slots that hand out copies do not actually lose items
					int lost = before - s.getStack().getCount();
					int got = Math.min(removed.getCount(), Math.max(0, lost));
					if (got > 0) {
						total[0] += got;
						s.onTakeItem(player, removed);
					}
				}
			}
		}
		return;
	}

	@Override
	public Identifier getId() {
		return EmiNetwork.FILL_RECIPE;
	}
}
