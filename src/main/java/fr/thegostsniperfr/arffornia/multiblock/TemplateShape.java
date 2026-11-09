package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A fixed structure read from a structure template. The template is authored with its front facing north; it matches in
 * the four horizontal rotations and their mirror images. Blocks are compared by type, not by state.
 */
public final class TemplateShape implements MultiblockShape {
    private static final List<Orientation> ORIENTATIONS = orientations();

    private final List<BlockPlacement> blocks;
    private final BlockPos triggerPos;

    private TemplateShape(List<BlockPlacement> blocks, BlockPos triggerPos) {
        this.blocks = List.copyOf(blocks);
        this.triggerPos = triggerPos;
    }

    /** Fails when the template does not contain {@code trigger} exactly once. */
    public static TemplateShape of(List<BlockPlacement> blocks, Block trigger) {
        List<BlockPos> triggers = blocks.stream().filter(block -> block.state().is(trigger)).map(BlockPlacement::pos).toList();
        if (triggers.size() != 1) {
            throw new IllegalArgumentException("the template must contain its trigger block exactly once, found " + triggers.size());
        }
        return new TemplateShape(blocks, triggers.getFirst());
    }

    public List<BlockPlacement> blocks() {
        return blocks;
    }

    @Override
    public Optional<Formation> match(BlockGetter level, BlockPos trigger) {
        for (Orientation orientation : ORIENTATIONS) {
            if (countMatching(level, trigger, orientation) == blocks.size()) {
                List<BlockPos> positions = blocks.stream().map(block -> worldPos(trigger, block.pos(), orientation)).toList();
                return Optional.of(new Formation(trigger, orientation.facing(), orientation.mirror() != Mirror.NONE, positions));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<BlockPlacement> missing(BlockGetter level, BlockPos trigger, Direction preferredFacing) {
        Orientation best = null;
        int bestScore = -1;
        for (Orientation orientation : ORIENTATIONS) {
            int score = countMatching(level, trigger, orientation) * 2 + (orientation.facing() == preferredFacing ? 1 : 0);
            if (score > bestScore) {
                best = orientation;
                bestScore = score;
            }
        }
        List<BlockPlacement> missing = new ArrayList<>();
        for (BlockPlacement block : blocks) {
            BlockPos pos = worldPos(trigger, block.pos(), best);
            if (!level.getBlockState(pos).is(block.state().getBlock())) {
                missing.add(new BlockPlacement(pos, block.state().mirror(best.mirror()).rotate(best.rotation())));
            }
        }
        return missing;
    }

    private int countMatching(BlockGetter level, BlockPos trigger, Orientation orientation) {
        int matching = 0;
        for (BlockPlacement block : blocks) {
            if (level.getBlockState(worldPos(trigger, block.pos(), orientation)).is(block.state().getBlock())) {
                matching++;
            }
        }
        return matching;
    }

    private BlockPos worldPos(BlockPos trigger, BlockPos templatePos, Orientation orientation) {
        return trigger.offset(StructureTemplate.transform(templatePos.subtract(triggerPos), orientation.mirror(), orientation.rotation(), BlockPos.ZERO));
    }

    private static List<Orientation> orientations() {
        List<Orientation> orientations = new ArrayList<>();
        for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
            for (Rotation rotation : Rotation.values()) {
                orientations.add(new Orientation(rotation, mirror));
            }
        }
        return List.copyOf(orientations);
    }

    /** Mirroring across the x axis flips left and right of a north-facing template; the front stays the front. */
    private record Orientation(Rotation rotation, Mirror mirror) {
        Direction facing() {
            return rotation.rotate(Direction.NORTH);
        }
    }
}
