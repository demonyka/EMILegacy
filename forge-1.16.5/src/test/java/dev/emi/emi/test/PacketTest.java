package dev.emi.emi.test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import dev.emi.emi.network.FillRecipeC2SPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/**
 * Decoder checks for the recipe fill packet, the only EMI packet with variable sized content from clients.
 * Runs without the game: ./gradlew packetTest
 */
public class PacketTest {
	private static int failures = 0;

	public static void main(String[] args) throws Exception {
		// A valid packet: 36 inventory slots, a 2x2 grid, an output slot, no stacks
		FillRecipeC2SPacket valid = decode(buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(9);
			buf.writeVarInt(44);
			buf.writeVarInt(4);
			for (int i = 1; i <= 4; i++) {
				buf.writeVarInt(i);
			}
			buf.writeBoolean(true);
			buf.writeVarInt(0);
			buf.writeVarInt(0);
		});
		check("valid packet: slots", size(valid, "slots") == 36);
		check("valid packet: crafting", size(valid, "crafting") == 4);
		check("valid packet: output", ((Integer) get(valid, "output")) == 0);

		// One range covering all integers: used to loop forever and exhaust memory
		rejected("unbounded slot range", buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(0);
			buf.writeVarInt(Integer.MAX_VALUE);
		});
		rejected("reversed slot range", buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(10);
			buf.writeVarInt(5);
		});
		rejected("negative slot", buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(-5);
			buf.writeVarInt(5);
		});
		// Many small ranges adding up
		rejected("too many slots in total", buf -> {
			header(buf);
			buf.writeVarInt(5000);
			for (int i = 0; i < 5000; i++) {
				buf.writeVarInt(0);
				buf.writeVarInt(0);
			}
		});
		rejected("huge group count", buf -> {
			header(buf);
			buf.writeVarInt(Integer.MAX_VALUE);
			buf.writeVarInt(0);
			buf.writeVarInt(4095);
			buf.writeVarInt(0);
			buf.writeVarInt(4095);
		});
		// Thousands of crafting entries made every requested stack scan every slot
		rejected("too many crafting slots", buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(0);
			buf.writeVarInt(35);
			buf.writeVarInt(6000);
			for (int i = 0; i < 6000; i++) {
				buf.writeVarInt(100);
			}
			buf.writeBoolean(false);
			buf.writeVarInt(0);
		});
		rejected("negative crafting size", buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(0);
			buf.writeVarInt(35);
			buf.writeVarInt(-1);
		});
		rejected("more stacks than crafting slots", buf -> {
			header(buf);
			buf.writeVarInt(1);
			buf.writeVarInt(0);
			buf.writeVarInt(35);
			buf.writeVarInt(2);
			buf.writeVarInt(1);
			buf.writeVarInt(2);
			buf.writeBoolean(false);
			buf.writeVarInt(3000);
		});

		if (failures > 0) {
			System.out.println(failures + " packet checks FAILED");
			System.exit(1);
		}
		System.out.println("All packet checks passed");
	}

	private static void header(PacketByteBuf buf) {
		buf.writeInt(0);
		buf.writeByte(0);
	}

	private static FillRecipeC2SPacket decode(Consumer<PacketByteBuf> writer) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		writer.accept(buf);
		return new FillRecipeC2SPacket(buf);
	}

	private static void rejected(String name, Consumer<PacketByteBuf> writer) throws Exception {
		long start = System.currentTimeMillis();
		FillRecipeC2SPacket packet;
		try {
			packet = decode(writer);
		} catch (RuntimeException e) {
			// A decoder exception drops the packet as well
			check(name + " (" + e.getClass().getSimpleName() + ")", System.currentTimeMillis() - start < 1000);
			return;
		}
		check(name, get(packet, "slots") == null && System.currentTimeMillis() - start < 1000);
	}

	private static Object get(Object o, String field) throws Exception {
		Field f = o.getClass().getDeclaredField(field);
		f.setAccessible(true);
		return f.get(o);
	}

	private static int size(Object o, String field) throws Exception {
		Object list = get(o, field);
		return list == null ? -1 : ((List<?>) list).size();
	}

	private static void check(String name, boolean ok) {
		System.out.println((ok ? "ok      " : "FAILED  ") + name);
		if (!ok) {
			failures++;
		}
	}
}
