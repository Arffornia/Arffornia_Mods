package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.gui.ProgressionGraphScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (fr.thegostsniperfr.arffornia.client.Keybindings.OPEN_GRAPH_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new ProgressionGraphScreen());
        }
    }
}