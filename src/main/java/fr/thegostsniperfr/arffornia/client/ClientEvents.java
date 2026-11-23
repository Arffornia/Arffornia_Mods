package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.web.ProgressionWebSession;
import fr.thegostsniperfr.arffornia.client.web.WebProgression;
import fr.thegostsniperfr.arffornia.progression.SyncedProgressionState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Keybindings.OPEN_GRAPH_KEY.consumeClick()) {
            WebProgression.open();
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (WebProgression.usable()) {
            WebProgression.prewarm();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncedProgressionState.clear();
        ProgressionWebSession.INSTANCE.clear();
        WebProgression.release();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ProgressionWebSession.INSTANCE.tick();
    }
}
