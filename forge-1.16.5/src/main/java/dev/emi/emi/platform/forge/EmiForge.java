package dev.emi.emi.platform.forge;

import org.apache.commons.lang3.tuple.Pair;

import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.platform.EmiMain;
import dev.emi.emi.registry.EmiCommands;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

@Mod("emi")
public class EmiForge {

	public EmiForge() {
		// EMI is optional on either side: do not mark servers without it (or with another version) as incompatible
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
			() -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (version, network) -> true));
		EmiMain.init();
		EmiPacketHandler.init();
		EmiNetwork.initServer((player, packet) -> {
			EmiPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
		});
		MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
		MinecraftForge.EVENT_BUS.addListener(this::playerConnect);
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> EmiClientForge::construct);
	}

	public void registerCommands(RegisterCommandsEvent event) {
		EmiCommands.registerCommands(event.getDispatcher());
	}

	public void playerConnect(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getPlayer() instanceof ServerPlayerEntity spe && EmiPacketHandler.CHANNEL.isRemotePresent(spe.networkHandler.connection)) {
			EmiNetwork.sendToClient(spe, new PingS2CPacket());
		}
	}
}
