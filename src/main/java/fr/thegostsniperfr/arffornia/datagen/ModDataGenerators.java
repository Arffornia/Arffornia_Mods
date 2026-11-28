package fr.thegostsniperfr.arffornia.datagen;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = Arffornia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModDataGenerators {
    private ModDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var registries = event.getLookupProvider();
        generator.addProvider(event.includeServer() || event.includeClient(), new MultiblockStructureProvider(output));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output, registries));
        generator.addProvider(event.includeServer(), new ModRecipeProvider.SpaceElevatorRecipe(output));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK)), registries));
        generator.addProvider(event.includeServer(), new ModBlockTagProvider(output, registries, event.getExistingFileHelper()));
    }
}
