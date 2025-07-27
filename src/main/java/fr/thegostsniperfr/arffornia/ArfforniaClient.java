package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.client.Keybindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ArfforniaClient {
    public ArfforniaClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }


    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Keybindings.OPEN_GRAPH_KEY);
    }
}