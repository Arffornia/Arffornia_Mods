package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Arffornia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PacketHandler {
    // 1.1: update_target_name carries a Component (translatable fallbacks) instead of a raw String.
    // 1.2: progression_state syncs the player's active progression.
    // 1.3: multiblocks syncs the structure templates for the Wrench hologram.
    private static final String PROTOCOL_VERSION = "1.3";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Arffornia.MODID)
                .versioned(PROTOCOL_VERSION);

        registrar.playToServer(
                ServerboundSetTargetMilestonePacket.TYPE,
                ServerboundSetTargetMilestonePacket.STREAM_CODEC,
                ServerboundSetTargetMilestonePacket::handle
        );

        registrar.playToClient(
                ClientboundUpdateTargetNamePacket.TYPE,
                ClientboundUpdateTargetNamePacket.STREAM_CODEC,
                ClientboundUpdateTargetNamePacket::handle
        );

        registrar.playToClient(
                ClientboundProgressionStatePacket.TYPE,
                ClientboundProgressionStatePacket.STREAM_CODEC,
                ClientboundProgressionStatePacket::handle
        );

        registrar.playToClient(
                ClientboundMultiblocksPacket.TYPE,
                ClientboundMultiblocksPacket.STREAM_CODEC,
                ClientboundMultiblocksPacket::handle
        );

        registrar.playToServer(
                ServerboundLaunchElevatorPacket.TYPE,
                ServerboundLaunchElevatorPacket.STREAM_CODEC,
                ServerboundLaunchElevatorPacket::handle
        );

        registrar.playToServer(
                ServerboundSelectRecipePacket.TYPE,
                ServerboundSelectRecipePacket.STREAM_CODEC,
                ServerboundSelectRecipePacket::handle
        );

        registrar.playToServer(
                ServerboundPlaceRecipePacket.TYPE,
                ServerboundPlaceRecipePacket.STREAM_CODEC,
                ServerboundPlaceRecipePacket::handle
        );
    }
}