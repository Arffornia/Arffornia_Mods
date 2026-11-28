package fr.thegostsniperfr.arffornia.multiblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A box whose size varies between limits: a shell of frame blocks with typed ports set into its faces, hollow (air
 * inside) or solid (frame inside). The trigger sits in one side face, away from its edges; that face is the front.
 * Found by walking the frame and port blocks connected to the trigger, so a box touching another one is refused.
 * In a definition file:
 * <pre>{@code
 * "shape": { "type": "arffornia:cuboid", "frame": ["arffornia:machine_casing"], "interior": "hollow",
 *            "min": [3, 3, 3], "max": [5, 4, 7], "ports": { "item_input": { "min": 1, "max": 2 } } }
 * }</pre>
 * Sizes are width (across the front), height, depth (away from the front). A port kind not listed may not appear.
 */
public record CuboidShape(List<Block> frame, Interior interior, Size min, Size max, Map<PortKind, Range> ports) implements MultiblockShape {
    public enum Interior implements StringRepresentable {
        HOLLOW, SOLID;

        public static final Codec<Interior> CODEC = StringRepresentable.fromEnum(Interior::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Size(int width, int height, int depth) {
        static final Codec<Size> CODEC = Codec.INT.listOf(3, 3).xmap(list -> new Size(list.get(0), list.get(1), list.get(2)),
                size -> List.of(size.width, size.height, size.depth));
        static final StreamCodec<ByteBuf, Size> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Size::width, ByteBufCodecs.VAR_INT, Size::height, ByteBufCodecs.VAR_INT, Size::depth, Size::new);

        boolean fits(Size max) {
            return width <= max.width && height <= max.height && depth <= max.depth;
        }

        @Override
        public String toString() {
            return width + "x" + height + "x" + depth;
        }
    }

    /** How many ports of one kind, inclusive. */
    public record Range(int min, int max) {
        static final Codec<Range> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("min", 0).forGetter(Range::min),
                Codec.INT.fieldOf("max").forGetter(Range::max)
        ).apply(instance, Range::new));
        static final StreamCodec<ByteBuf, Range> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Range::min, ByteBufCodecs.VAR_INT, Range::max, Range::new);

        boolean contains(int count) {
            return count >= min && count <= max;
        }
    }

    /** Why a structure does not form; {@link #message} names the machine. */
    public record Problem(Kind kind, List<Object> args) {
        public enum Kind { TOO_SMALL, TOO_LARGE, CONTROLLER_PLACEMENT, INCOMPLETE, PORT_PLACEMENT, PORT_COUNT }

        Component message(Component machine) {
            List<Object> all = new ArrayList<>();
            all.add(machine);
            all.addAll(args);
            return Component.translatable("chat.arffornia.multiblock." + kind.name().toLowerCase(Locale.ROOT), all.toArray());
        }
    }

    /** The result of checking the blocks around a trigger: a formation, or the first problem found. */
    public record Evaluation(Optional<Formation> formation, Optional<Problem> problem) {
        static Evaluation failed(Problem.Kind kind, Object... args) {
            return new Evaluation(Optional.empty(), Optional.of(new Problem(kind, List.of(args))));
        }
    }

    private static final int MAX_SIDE = 32;

    public static final MapCodec<CuboidShape> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().listOf(1, 16).fieldOf("frame").forGetter(CuboidShape::frame),
            Interior.CODEC.optionalFieldOf("interior", Interior.HOLLOW).forGetter(CuboidShape::interior),
            Size.CODEC.fieldOf("min").forGetter(CuboidShape::min),
            Size.CODEC.fieldOf("max").forGetter(CuboidShape::max),
            Codec.unboundedMap(PortKind.CODEC, Range.CODEC).optionalFieldOf("ports", Map.of()).forGetter(CuboidShape::ports)
    ).apply(instance, CuboidShape::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CuboidShape> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.BLOCK).apply(ByteBufCodecs.list(16)), CuboidShape::frame,
            ByteBufCodecs.idMapper(i -> Interior.values()[i], Interior::ordinal), CuboidShape::interior,
            Size.STREAM_CODEC, CuboidShape::min,
            Size.STREAM_CODEC, CuboidShape::max,
            ByteBufCodecs.<ByteBuf, PortKind, Range, Map<PortKind, Range>>map(HashMap::new, PortKind.STREAM_CODEC, Range.STREAM_CODEC), CuboidShape::ports,
            CuboidShape::new);

    public CuboidShape {
        frame = List.copyOf(frame);
        ports = Map.copyOf(ports);
        // The trigger must sit in a face away from its edges, which needs three blocks across that face.
        // Every Wrench use and hologram refresh walks the box, so it stays small.
        if (min.width < 3 || min.height < 3 || min.depth < 2 || !min.fits(max) || !max.fits(new Size(MAX_SIDE, MAX_SIDE, MAX_SIDE))) {
            throw new IllegalArgumentException("sizes need 3 <= min width and height, 2 <= min depth, min <= max <= " + MAX_SIDE + "; got " + min + " to " + max);
        }
        ports.forEach((kind, range) -> {
            if (range.min < 0 || range.min > range.max) {
                throw new IllegalArgumentException("bad port range for " + kind.getSerializedName() + ": " + range.min + " to " + range.max);
            }
        });
    }

    @Override
    public Optional<Formation> match(BlockGetter level, BlockPos trigger) {
        return evaluate(level, trigger).formation();
    }

    @Override
    public Optional<Component> problem(BlockGetter level, BlockPos trigger, Component machine) {
        return evaluate(level, trigger).problem().map(problem -> problem.message(machine));
    }

    public Evaluation evaluate(BlockGetter level, BlockPos trigger) {
        Scan scan = scan(level, trigger);
        if (scan.overflow()) {
            return Evaluation.failed(Problem.Kind.TOO_LARGE, max.toString());
        }
        Box box = scan.box();
        Optional<Direction> front = box.frontOf(trigger);
        if (front.isEmpty()) {
            return Evaluation.failed(Problem.Kind.CONTROLLER_PLACEMENT);
        }
        Size size = box.size(front.get());
        if (!min.fits(size)) {
            return Evaluation.failed(Problem.Kind.TOO_SMALL, size.toString(), min.toString());
        }
        if (!size.fits(max)) {
            return Evaluation.failed(Problem.Kind.TOO_LARGE, max.toString());
        }
        List<BlockPos> positions = new ArrayList<>();
        int wrong = 0;
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            boolean shell = box.faces(pos) > 0;
            if (shell || interior == Interior.SOLID) {
                boolean fits = pos.equals(trigger) || (shell ? accepts(state) : isFrame(state));
                if (fits) {
                    positions.add(pos.immutable());
                } else {
                    wrong++;
                }
            } else if (!state.isAir()) {
                wrong++;
            }
        }
        if (wrong > 0) {
            return Evaluation.failed(Problem.Kind.INCOMPLETE, wrong);
        }
        Map<PortKind, Integer> counts = new EnumMap<>(PortKind.class);
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).getBlock() instanceof PortBlock port) {
                if (box.faces(pos) != 1) {
                    return Evaluation.failed(Problem.Kind.PORT_PLACEMENT);
                }
                counts.merge(port.kind(), 1, Integer::sum);
            }
        }
        for (PortKind kind : PortKind.values()) {
            Range range = ports.getOrDefault(kind, new Range(0, 0));
            int count = counts.getOrDefault(kind, 0);
            if (!range.contains(count)) {
                return Evaluation.failed(Problem.Kind.PORT_COUNT, range.min, range.max, PortBlock.of(kind).getName(), count);
            }
        }
        return new Evaluation(Optional.of(new Formation(trigger, front.get(), false, positions)), Optional.empty());
    }

    /**
     * The smallest valid box through what is built, grown from the trigger to the minimum size (behind the front,
     * centred across it). Lists frame blocks to place, blocks to clear inside a hollow box, and the ports still needed
     * on free face positions.
     */
    @Override
    public List<BlockPlacement> missing(BlockGetter level, BlockPos trigger, Direction preferredFacing) {
        Scan scan = scan(level, trigger);
        if (scan.overflow()) {
            return List.of();
        }
        Direction front = scan.box().frontOf(trigger).orElse(preferredFacing);
        Box box = scan.box().grownTo(min, trigger, front);
        List<BlockPlacement> missing = new ArrayList<>();
        List<Integer> portSpots = new ArrayList<>();
        Map<PortKind, Integer> counts = new EnumMap<>(PortKind.class);
        BlockState frameState = frame.getFirst().defaultBlockState();
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            int faces = box.faces(pos);
            if (pos.equals(trigger)) {
                continue;
            }
            if (faces > 0 || interior == Interior.SOLID) {
                if (faces > 0 && state.getBlock() instanceof PortBlock port) {
                    counts.merge(port.kind(), 1, Integer::sum);
                } else if (!(faces > 0 ? accepts(state) : isFrame(state))) {
                    if (faces == 1) {
                        portSpots.add(missing.size());
                    }
                    missing.add(new BlockPlacement(pos.immutable(), frameState));
                }
            } else if (!state.isAir()) {
                missing.add(new BlockPlacement(pos.immutable(), Blocks.AIR.defaultBlockState()));
            }
        }
        int spot = 0;
        for (PortKind kind : PortKind.values()) {
            int needed = ports.getOrDefault(kind, new Range(0, 0)).min - counts.getOrDefault(kind, 0);
            for (; needed > 0 && spot < portSpots.size(); needed--, spot++) {
                int index = portSpots.get(spot);
                missing.set(index, new BlockPlacement(missing.get(index).pos(), PortBlock.of(kind).defaultBlockState()));
            }
        }
        return missing;
    }

    private boolean isFrame(BlockState state) {
        return frame.contains(state.getBlock());
    }

    private boolean accepts(BlockState state) {
        return isFrame(state) || state.getBlock() instanceof PortBlock;
    }

    private record Scan(Box box, boolean overflow) {
    }

    /** The frame and port blocks connected to the trigger; stops once they spread wider than the largest box. */
    private Scan scan(BlockGetter level, BlockPos trigger) {
        int horizontal = Math.max(max.width, max.depth);
        Box box = new Box(trigger, trigger);
        Set<BlockPos> seen = new HashSet<>(List.of(trigger));
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(trigger));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            box = box.including(pos);
            if (box.spanX() > horizontal || box.spanZ() > horizontal || box.spanY() > max.height) {
                return new Scan(box, true);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next) && accepts(level.getBlockState(next))) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return new Scan(box, false);
    }

    /** An axis-aligned box of block positions, corners inclusive. */
    record Box(BlockPos low, BlockPos high) {
        Box including(BlockPos pos) {
            return new Box(new BlockPos(Math.min(low.getX(), pos.getX()), Math.min(low.getY(), pos.getY()), Math.min(low.getZ(), pos.getZ())),
                    new BlockPos(Math.max(high.getX(), pos.getX()), Math.max(high.getY(), pos.getY()), Math.max(high.getZ(), pos.getZ())));
        }

        int spanX() {
            return high.getX() - low.getX() + 1;
        }

        int spanY() {
            return high.getY() - low.getY() + 1;
        }

        int spanZ() {
            return high.getZ() - low.getZ() + 1;
        }

        Size size(Direction front) {
            return front.getAxis() == Direction.Axis.Z ? new Size(spanX(), spanY(), spanZ()) : new Size(spanZ(), spanY(), spanX());
        }

        Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(low, high);
        }

        /** On how many of the six faces {@code pos} lies: 0 inside, 1 in a face, 2 or more on an edge. */
        int faces(BlockPos pos) {
            int faces = 0;
            for (Direction.Axis axis : Direction.Axis.values()) {
                int value = pos.get(axis);
                faces += (value == low.get(axis) ? 1 : 0) + (value == high.get(axis) ? 1 : 0);
            }
            return faces;
        }

        /** The side face {@code pos} lies in, when it lies in exactly one face and that face is not the top or bottom. */
        Optional<Direction> frontOf(BlockPos pos) {
            if (faces(pos) != 1 || pos.getY() == low.getY() || pos.getY() == high.getY()) {
                return Optional.empty();
            }
            if (pos.getX() == low.getX()) {
                return Optional.of(Direction.WEST);
            }
            if (pos.getX() == high.getX()) {
                return Optional.of(Direction.EAST);
            }
            return Optional.of(pos.getZ() == low.getZ() ? Direction.NORTH : Direction.SOUTH);
        }

        /** Grows behind {@code front} for depth and around {@code centre} for width and height until it reaches {@code min}. */
        Box grownTo(Size min, BlockPos centre, Direction front) {
            int[] lo = {low.getX(), low.getY(), low.getZ()};
            int[] hi = {high.getX(), high.getY(), high.getZ()};
            int depthAxis = front.getAxis().ordinal();
            int widthAxis = front.getClockWise().getAxis().ordinal();
            int[] c = {centre.getX(), centre.getY(), centre.getZ()};
            while (hi[depthAxis] - lo[depthAxis] + 1 < min.depth) {
                if (front.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    lo[depthAxis]--;
                } else {
                    hi[depthAxis]++;
                }
            }
            for (int[] axisAndSize : new int[][]{{widthAxis, min.width}, {1, min.height}}) {
                int axis = axisAndSize[0];
                while (hi[axis] - lo[axis] + 1 < axisAndSize[1]) {
                    if (c[axis] - lo[axis] <= hi[axis] - c[axis]) {
                        lo[axis]--;
                    } else {
                        hi[axis]++;
                    }
                }
            }
            return new Box(new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2]));
        }
    }
}
