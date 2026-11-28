package fr.thegostsniperfr.arffornia.datagen;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, ExistingFileHelper helper) {
        super(output, registries, Arffornia.MODID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(BlockTags.NEEDS_IRON_TOOL).add(
                ModBlocks.CRAFTER_BLOCK.get(), ModBlocks.CRAFTER_PART_BLOCK.get(),
                ModBlocks.SPACE_ELEVATOR.get(), ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.CRAFTER_BLOCK.get(), ModBlocks.CRAFTER_PART_BLOCK.get(),
                ModBlocks.SPACE_ELEVATOR.get(), ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get(),
                ModBlocks.MULTIBLOCK_PART.get(), ModBlocks.MACHINE_CASING.get(),
                ModBlocks.ITEM_INPUT_PORT.get(), ModBlocks.ITEM_OUTPUT_PORT.get(), ModBlocks.ENERGY_INPUT_PORT.get(),
                ModBlocks.SMELTER_CONTROLLER.get(), ModBlocks.SMELTER.get());
    }
}
