package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Arffornia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "1.0";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Arffornia.MODID)
                .versioned(PROTOCOL_VERSION);

        registrar.playToServer(
                ServerboundSetTargetMilestonePacket.TYPE,
                ServerboundSetTargetMilestonePacket.STREAM_CODEC,
                ServerboundSetTargetMilestonePacket::handle
        );
    }
}