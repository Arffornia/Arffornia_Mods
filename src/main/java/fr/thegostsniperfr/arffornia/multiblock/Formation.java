package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Where a complete structure stands.
 *
 * @param trigger   the block the Wrench was used on; it becomes the master
 * @param facing    the direction the structure's front faces
 * @param mirrored  whether it was built as the mirror image of its template
 * @param positions every block of the structure, the trigger included
 */
public record Formation(BlockPos trigger, Direction facing, boolean mirrored, List<BlockPos> positions) {
    public Formation {
        positions = List.copyOf(positions);
    }
}
