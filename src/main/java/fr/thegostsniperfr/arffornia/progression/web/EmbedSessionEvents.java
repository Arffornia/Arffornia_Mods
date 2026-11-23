package fr.thegostsniperfr.arffornia.progression.web;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.network.ServerPayloadChecks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Arffornia.MODID)
public final class EmbedSessionEvents {
    private EmbedSessionEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EmbedSessions.forget(event.getEntity().getUUID());
        ServerPayloadChecks.forget(event.getEntity().getUUID());
    }
}
