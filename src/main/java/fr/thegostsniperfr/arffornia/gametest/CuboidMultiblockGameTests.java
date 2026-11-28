package fr.thegostsniperfr.arffornia.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.machine.MachineBlock;
import fr.thegostsniperfr.arffornia.multiblock.BlockPlacement;
import fr.thegostsniperfr.arffornia.multiblock.CuboidShape;
import fr.thegostsniperfr.arffornia.multiblock.CuboidShape.Interior;
import fr.thegostsniperfr.arffornia.multiblock.CuboidShape.Problem;
import fr.thegostsniperfr.arffornia.multiblock.CuboidShape.Range;
import fr.thegostsniperfr.arffornia.multiblock.CuboidShape.Size;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinition;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMachineBlockEntity;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockShape;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockShapes;
import fr.thegostsniperfr.arffornia.multiblock.Multiblocks;
import fr.thegostsniperfr.arffornia.multiblock.PortKind;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rule-based multiblocks ({@link CuboidShape}): a hollow casing box from 3x3x3 to 4x3x5 (width x height x depth) with
 * one or two item inputs and exactly one item output. The Smelter master stands in for a machine; positions are on the
 * 5x4x5 platform with the floor at y = 0.
 */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class CuboidMultiblockGameTests {
    private static final String PLATFORM = "platform";
    private static final AtomicInteger IDS = new AtomicInteger(50_000);

    private CuboidMultiblockGameTests() {
    }

    private static CuboidShape shape() {
        return new CuboidShape(List.of(ModBlocks.MACHINE_CASING.get()), Interior.HOLLOW, new Size(3, 3, 3), new Size(4, 3, 5),
                Map.of(PortKind.ITEM_INPUT, new Range(1, 2), PortKind.ITEM_OUTPUT, new Range(1, 1)));
    }

    private static MultiblockDefinition definition() {
        return new MultiblockDefinition(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "test/cuboid"), shape(),
                ModBlocks.SMELTER_CONTROLLER.get(), ModBlocks.SMELTER.get(), Optional.empty());
    }

    /** A hollow casing box between two corners (relative), with the given blocks set over its shell afterwards. */
    private static void box(GameTestHelper helper, BlockPos low, BlockPos high, Map<BlockPos, Block> overrides) {
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            boolean shell = pos.getX() == low.getX() || pos.getX() == high.getX() || pos.getY() == low.getY() || pos.getY() == high.getY()
                    || pos.getZ() == low.getZ() || pos.getZ() == high.getZ();
            helper.setBlock(pos, shell ? ModBlocks.MACHINE_CASING.get() : Blocks.AIR);
        }
        overrides.forEach(helper::setBlock);
    }

    private static final BlockPos LOW = new BlockPos(1, 1, 1);
    private static final BlockPos HIGH = new BlockPos(3, 3, 3);
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 1);

    /** The smallest valid box: controller in the north face, input at the back, output on the west side. */
    private static void smallest(GameTestHelper helper) {
        box(helper, LOW, HIGH, Map.of(CONTROLLER, ModBlocks.SMELTER_CONTROLLER.get(),
                new BlockPos(2, 2, 3), ModBlocks.ITEM_INPUT_PORT.get(), new BlockPos(1, 2, 2), ModBlocks.ITEM_OUTPUT_PORT.get()));
    }

    private static CuboidShape.Evaluation evaluate(GameTestHelper helper, BlockPos controller) {
        return shape().evaluate(helper.getLevel(), helper.absolutePos(controller));
    }

    private static void assertProblem(GameTestHelper helper, BlockPos controller, Problem.Kind expected) {
        CuboidShape.Evaluation result = evaluate(helper, controller);
        helper.assertTrue(result.formation().isEmpty(), "The structure must not form");
        helper.assertTrue(result.problem().map(Problem::kind).equals(Optional.of(expected)), "Expected " + expected + ", got " + result.problem());
    }

    private static FakePlayer player(GameTestHelper helper, int id) {
        ProgressionBackends.fake().createProgression(id);
        Progressions.store(helper.getLevel().getServer(), new ProgressionSnapshot(id, Set.of(), null, false));
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cuboid" + id));
        player.setData(ModAttachments.ACTIVE_PROGRESSION, (long) id);
        return player;
    }

    @GameTest(template = PLATFORM)
    public static void smallestBoxFormsAndFacesOutOfItsControllerFace(GameTestHelper helper) {
        smallest(helper);

        Multiblocks.Result result = Multiblocks.tryForm(helper.getLevel(), helper.absolutePos(CONTROLLER), player(helper, IDS.getAndIncrement()), definition());

        helper.assertTrue(result == Multiblocks.Result.FORMED, "The box should form, got " + result);
        MultiblockMachineBlockEntity machine = (MultiblockMachineBlockEntity) helper.getBlockEntity(CONTROLLER);
        helper.assertTrue(machine.parts().size() == 25, "26 shell blocks minus the master expected, got " + machine.parts().size());
        helper.assertTrue(machine.getBlockState().getValue(MachineBlock.FACING) == Direction.NORTH, "The master faces out of the north face");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void largestBoxFormsWithTheControllerInAnEastFace(GameTestHelper helper) {
        BlockPos controller = new BlockPos(4, 2, 2);
        box(helper, new BlockPos(0, 1, 0), new BlockPos(4, 3, 3), Map.of(controller, ModBlocks.SMELTER_CONTROLLER.get(),
                new BlockPos(0, 2, 1), ModBlocks.ITEM_INPUT_PORT.get(), new BlockPos(0, 2, 2), ModBlocks.ITEM_INPUT_PORT.get(),
                new BlockPos(2, 3, 1), ModBlocks.ITEM_OUTPUT_PORT.get()));

        CuboidShape.Evaluation result = evaluate(helper, controller);

        helper.assertTrue(result.formation().map(formation -> formation.facing() == Direction.EAST && formation.positions().size() == 5 * 3 * 4 - 3 * 1 * 2)
                .orElse(false), "A 4 wide, 5 deep box facing east should form, got " + result.problem());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void boxWiderThanTheMaximumIsRefused(GameTestHelper helper) {
        BlockPos controller = new BlockPos(2, 2, 1);
        box(helper, new BlockPos(0, 1, 1), new BlockPos(4, 3, 3), Map.of(controller, ModBlocks.SMELTER_CONTROLLER.get(),
                new BlockPos(2, 2, 3), ModBlocks.ITEM_INPUT_PORT.get(), new BlockPos(0, 2, 2), ModBlocks.ITEM_OUTPUT_PORT.get()));

        assertProblem(helper, controller, Problem.Kind.TOO_LARGE);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void boxShallowerThanTheMinimumIsRefused(GameTestHelper helper) {
        box(helper, LOW, new BlockPos(3, 3, 2), Map.of(CONTROLLER, ModBlocks.SMELTER_CONTROLLER.get(),
                new BlockPos(2, 2, 2), ModBlocks.ITEM_INPUT_PORT.get(), new BlockPos(1, 2, 2), ModBlocks.ITEM_OUTPUT_PORT.get()));

        assertProblem(helper, CONTROLLER, Problem.Kind.TOO_SMALL);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void missingRequiredPortIsRefusedAndShownInTheHologram(GameTestHelper helper) {
        box(helper, LOW, HIGH, Map.of(CONTROLLER, ModBlocks.SMELTER_CONTROLLER.get(), new BlockPos(2, 2, 3), ModBlocks.ITEM_INPUT_PORT.get()));
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.AIR);

        assertProblem(helper, CONTROLLER, Problem.Kind.INCOMPLETE);
        List<BlockPlacement> missing = shape().missing(helper.getLevel(), helper.absolutePos(CONTROLLER), Direction.NORTH);
        helper.assertTrue(missing.size() == 1 && missing.getFirst().state().is(ModBlocks.ITEM_OUTPUT_PORT.get())
                && missing.getFirst().pos().equals(helper.absolutePos(new BlockPos(1, 2, 2))), "The hole should be shown as the missing output port, got " + missing);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void boxWithoutItsOutputPortIsRefused(GameTestHelper helper) {
        box(helper, LOW, HIGH, Map.of(CONTROLLER, ModBlocks.SMELTER_CONTROLLER.get(), new BlockPos(2, 2, 3), ModBlocks.ITEM_INPUT_PORT.get()));

        assertProblem(helper, CONTROLLER, Problem.Kind.PORT_COUNT);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void tooManyPortsOfAKindAreRefused(GameTestHelper helper) {
        smallest(helper);
        helper.setBlock(new BlockPos(3, 2, 2), ModBlocks.ITEM_OUTPUT_PORT.get());

        assertProblem(helper, CONTROLLER, Problem.Kind.PORT_COUNT);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void portKindTheRulesDoNotListIsRefused(GameTestHelper helper) {
        smallest(helper);
        helper.setBlock(new BlockPos(3, 2, 2), ModBlocks.ENERGY_INPUT_PORT.get());

        assertProblem(helper, CONTROLLER, Problem.Kind.PORT_COUNT);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void portOnAnEdgeIsRefused(GameTestHelper helper) {
        smallest(helper);
        helper.setBlock(new BlockPos(1, 2, 2), ModBlocks.MACHINE_CASING.get());
        helper.setBlock(new BlockPos(1, 3, 2), ModBlocks.ITEM_OUTPUT_PORT.get());

        assertProblem(helper, CONTROLLER, Problem.Kind.PORT_PLACEMENT);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void holeInTheShellIsRefusedAndShownInTheHologram(GameTestHelper helper) {
        smallest(helper);
        BlockPos hole = new BlockPos(3, 3, 3);
        helper.setBlock(hole, Blocks.AIR);

        assertProblem(helper, CONTROLLER, Problem.Kind.INCOMPLETE);
        List<BlockPlacement> missing = shape().missing(helper.getLevel(), helper.absolutePos(CONTROLLER), Direction.NORTH);
        helper.assertTrue(missing.equals(List.of(new BlockPlacement(helper.absolutePos(hole), ModBlocks.MACHINE_CASING.get().defaultBlockState()))),
                "Only the hole should be missing, got " + missing);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void blockInsideAHollowBoxIsRefusedAndOutlined(GameTestHelper helper) {
        smallest(helper);
        BlockPos inside = new BlockPos(2, 2, 2);
        helper.setBlock(inside, Blocks.STONE);

        assertProblem(helper, CONTROLLER, Problem.Kind.INCOMPLETE);
        List<BlockPlacement> missing = shape().missing(helper.getLevel(), helper.absolutePos(CONTROLLER), Direction.NORTH);
        helper.assertTrue(missing.equals(List.of(new BlockPlacement(helper.absolutePos(inside), Blocks.AIR.defaultBlockState()))),
                "The stone inside should be the only wrong block, got " + missing);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void twoBoxesSharingAWallAreRefused(GameTestHelper helper) {
        box(helper, new BlockPos(0, 1, 1), new BlockPos(2, 3, 3), Map.of());
        box(helper, new BlockPos(2, 1, 1), new BlockPos(4, 3, 3), Map.of(new BlockPos(1, 2, 1), ModBlocks.SMELTER_CONTROLLER.get(),
                new BlockPos(1, 2, 3), ModBlocks.ITEM_INPUT_PORT.get(), new BlockPos(0, 2, 2), ModBlocks.ITEM_OUTPUT_PORT.get()));

        CuboidShape.Evaluation result = evaluate(helper, new BlockPos(1, 2, 1));

        helper.assertTrue(result.formation().isEmpty(), "A box glued to another must not form, got " + result.formation());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void controllerOnAnEdgeIsRefused(GameTestHelper helper) {
        BlockPos corner = new BlockPos(1, 2, 1);
        box(helper, LOW, HIGH, Map.of(corner, ModBlocks.SMELTER_CONTROLLER.get(),
                new BlockPos(2, 2, 3), ModBlocks.ITEM_INPUT_PORT.get(), new BlockPos(3, 2, 2), ModBlocks.ITEM_OUTPUT_PORT.get()));

        assertProblem(helper, corner, Problem.Kind.CONTROLLER_PLACEMENT);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void hologramOfALoneControllerIsTheSmallestBoxBehindIt(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.SMELTER_CONTROLLER.get());

        List<BlockPlacement> missing = shape().missing(helper.getLevel(), helper.absolutePos(CONTROLLER), Direction.NORTH);

        long inputs = missing.stream().filter(placement -> placement.state().is(ModBlocks.ITEM_INPUT_PORT.get())).count();
        long outputs = missing.stream().filter(placement -> placement.state().is(ModBlocks.ITEM_OUTPUT_PORT.get())).count();
        boolean behind = missing.stream().allMatch(placement -> {
            BlockPos pos = placement.pos().subtract(helper.absolutePos(BlockPos.ZERO));
            return pos.getX() >= 1 && pos.getX() <= 3 && pos.getY() >= 1 && pos.getY() <= 3 && pos.getZ() >= 1 && pos.getZ() <= 3;
        });
        helper.assertTrue(missing.size() == 25 && inputs == 1 && outputs == 1 && behind,
                "Expected the 25 shell blocks of a 3x3x3 box behind the controller with one port of each kind, got " + missing.size()
                        + " blocks, " + inputs + " inputs, " + outputs + " outputs, inside the box: " + behind);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void breakingAPartOfAFormedBoxRestoresIt(GameTestHelper helper) {
        smallest(helper);
        Multiblocks.tryForm(helper.getLevel(), helper.absolutePos(CONTROLLER), player(helper, IDS.getAndIncrement()), definition());

        helper.destroyBlock(new BlockPos(3, 3, 3));

        helper.assertBlockPresent(ModBlocks.SMELTER_CONTROLLER.get(), CONTROLLER);
        helper.assertBlockPresent(ModBlocks.ITEM_OUTPUT_PORT.get(), new BlockPos(1, 2, 2));
        helper.assertBlockPresent(ModBlocks.MACHINE_CASING.get(), new BlockPos(1, 1, 1));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void cuboidRulesReadFromJsonAndSurviveTheClientPacket(GameTestHelper helper) {
        String json = """
                {"type": "arffornia:cuboid", "frame": ["arffornia:machine_casing"], "min": [3, 3, 3], "max": [4, 3, 5],
                 "ports": {"item_input": {"min": 1, "max": 2}, "item_output": {"min": 1, "max": 1}}}""";

        MultiblockShape parsed = MultiblockShapes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        CuboidShape.STREAM_CODEC.encode(buf, (CuboidShape) parsed);
        CuboidShape decoded = CuboidShape.STREAM_CODEC.decode(buf);

        helper.assertTrue(parsed.equals(shape()) && decoded.equals(shape()), "JSON and packet should give back the same rules, got " + parsed + " / " + decoded);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void cuboidWithAnImpossibleMinimumIsRejected(GameTestHelper helper) {
        String json = """
                {"type": "arffornia:cuboid", "frame": ["arffornia:machine_casing"], "min": [2, 3, 3], "max": [4, 3, 5]}""";

        boolean rejected;
        try {
            rejected = MultiblockShapes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError();
        } catch (IllegalArgumentException e) {
            rejected = true;
        }

        helper.assertTrue(rejected, "A 2-wide minimum leaves no room for the controller away from the edges");
        helper.succeed();
    }
}
