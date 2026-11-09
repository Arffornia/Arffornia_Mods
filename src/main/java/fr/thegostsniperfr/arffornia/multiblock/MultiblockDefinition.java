package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * A structure the Wrench can form.
 *
 * @param id      the definition file, {@code data/<ns>/multiblock/<path>.json}
 * @param shape   which blocks make it up
 * @param trigger the block the Wrench is used on; it becomes {@code master}
 * @param master  the block that holds the machine once formed
 * @param stage   the progression stage key needed to form it; empty means always allowed
 */
public record MultiblockDefinition(ResourceLocation id, MultiblockShape shape, Block trigger, MultiblockMasterBlock master,
                                   Optional<String> stage) {
}
