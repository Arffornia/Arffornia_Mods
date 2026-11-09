package fr.thegostsniperfr.arffornia.datagen;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = Arffornia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModDataGenerators {
    private ModDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer() || event.includeClient(),
                new MultiblockStructureProvider(event.getGenerator().getPackOutput()));
    }
}
