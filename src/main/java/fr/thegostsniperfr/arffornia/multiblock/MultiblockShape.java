package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.List;
import java.util.Optional;

/**
 * Decides which blocks around a trigger make up a structure. {@link TemplateShape} is a fixed template; structures whose
 * size varies (a ring of pylons, several units sharing a tank) implement this interface with their own search, and
 * formation, ports and disassembly work unchanged because they only use the {@link Formation}.
 */
public interface MultiblockShape {
    /** The structure around {@code trigger} when it is complete, in any orientation. */
    Optional<Formation> match(BlockGetter level, BlockPos trigger);

    /**
     * The blocks still missing or wrong around {@code trigger}, for the closest orientation; ties go to the one whose
     * front faces {@code preferredFacing}. Positions are absolute and states oriented.
     */
    List<BlockPlacement> missing(BlockGetter level, BlockPos trigger, Direction preferredFacing);
}
