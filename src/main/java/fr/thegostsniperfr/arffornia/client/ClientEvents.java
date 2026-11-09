package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.screen.ProgressionGraphScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import fr.thegostsniperfr.arffornia.progression.SyncedProgressionState;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (fr.thegostsniperfr.arffornia.client.Keybindings.OPEN_GRAPH_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new ProgressionGraphScreen());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncedProgressionState.clear();
    }
}