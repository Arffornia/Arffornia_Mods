package fr.thegostsniperfr.arffornia.datagen;

import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Set;

public final class ModBlockLootProvider extends BlockLootSubProvider {
    private static final List<Block> SELF_DROPPING = List.of(
            ModBlocks.CRAFTER_BLOCK.get(), ModBlocks.SPACE_ELEVATOR.get(), ModBlocks.MACHINE_CASING.get(),
            ModBlocks.SMELTER_CONTROLLER.get(), ModBlocks.ITEM_INPUT_PORT.get(), ModBlocks.ITEM_OUTPUT_PORT.get(),
            ModBlocks.ENERGY_INPUT_PORT.get());

    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        SELF_DROPPING.forEach(this::dropSelf);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return SELF_DROPPING;
    }
}
