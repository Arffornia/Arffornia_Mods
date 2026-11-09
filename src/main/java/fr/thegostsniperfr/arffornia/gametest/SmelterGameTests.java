package fr.thegostsniperfr.arffornia.gametest;

import com.mojang.authlib.GameProfile;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.item.ModItems;
import fr.thegostsniperfr.arffornia.machine.MachineBlock;
import fr.thegostsniperfr.arffornia.machine.MachineStatus;
import fr.thegostsniperfr.arffornia.machine.MachineTypes;
import fr.thegostsniperfr.arffornia.machine.SideMode;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinition;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinitions;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMachineBlockEntity;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockPartBlockEntity;
import fr.thegostsniperfr.arffornia.multiblock.Multiblocks;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.progression.Stages;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Smelter multiblock end to end: formation with the Wrench in every orientation, disassembly, ports, recipe
 * processing against the machine's own progression. Ids start far from {@link ArfforniaGameTests}' so both suites can
 * share the in-memory backend.
 */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class SmelterGameTests {
    private static final String PLATFORM = "platform";
    private static final BlockPos CENTRE = new BlockPos(2, 2, 2);
    private static final AtomicInteger IDS = new AtomicInteger(20_000);

    /** The Smelter template, relative to its centre block: layers bottom to top, rows front (north) to back, west to east. */
    private static final String[][] LAYERS = {
            {"CCC", "CCC", "CCC"},
            {"BKB", "O#I", "BEB"},
            {"BBB", "BBB", "BBB"},
    };
    private static final BlockPos CONTROLLER = new BlockPos(0, 0, -1);
    private static final BlockPos INPUT_PORT = new BlockPos(1, 0, 0);
    private static final BlockPos OUTPUT_PORT = new BlockPos(-1, 0, 0);
    private static final BlockPos ENERGY_PORT = new BlockPos(0, 0, 1);
    private static final BlockPos BRICK = new BlockPos(1, 1, 1);
    private static final BlockPos CASING = new BlockPos(0, -1, 0);

    private SmelterGameTests() {
    }

    private record Built(Map<BlockPos, BlockState> blocks, Rotation rotation, Mirror mirror) {
        BlockPos at(BlockPos relativeToCentre) {
            return CENTRE.offset(StructureTemplate.transform(relativeToCentre, mirror, rotation, BlockPos.ZERO));
        }
    }

    private static BlockState stateOf(char key) {
        return switch (key) {
            case 'C' -> ModBlocks.MACHINE_CASING.get().defaultBlockState();
            case 'B' -> Blocks.BRICKS.defaultBlockState();
            case '#' -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case 'K' -> ModBlocks.SMELTER_CONTROLLER.get().defaultBlockState();
            case 'I' -> ModBlocks.ITEM_INPUT_PORT.get().defaultBlockState();
            case 'O' -> ModBlocks.ITEM_OUTPUT_PORT.get().defaultBlockState();
            case 'E' -> ModBlocks.ENERGY_INPUT_PORT.get().defaultBlockState();
            default -> throw new IllegalArgumentException("unknown key " + key);
        };
    }

    /** Builds the Smelter around {@link #CENTRE}, turned and mirrored, as a player would; returns relative positions. */
    private static Built build(GameTestHelper helper, Rotation rotation, Mirror mirror) {
        Built built = new Built(new HashMap<>(), rotation, mirror);
        for (int y = 0; y < LAYERS.length; y++) {
            for (int z = 0; z < LAYERS[y].length; z++) {
                for (int x = 0; x < LAYERS[y][z].length(); x++) {
                    BlockPos pos = built.at(new BlockPos(x - 1, y - 1, z - 1));
                    BlockState state = stateOf(LAYERS[y][z].charAt(x));
                    helper.setBlock(pos, state);
                    built.blocks().put(pos, state);
                }
            }
        }
        return built;
    }

    private static FakePlayer player(GameTestHelper helper, int id) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "smelter" + id));
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }

    /** A player whose active progression {@code id} the server knows, with these milestones completed. */
    private static FakePlayer playerOnProgression(GameTestHelper helper, int id, Integer... completed) {
        ProgressionBackends.fake().createProgression(id);
        Progressions.store(helper.getLevel().getServer(), new ProgressionSnapshot(id, Set.of(completed), null, false));
        FakePlayer player = player(helper, id);
        player.setData(ModAttachments.ACTIVE_PROGRESSION, (long) id);
        return player;
    }

    private static Multiblocks.Result wrench(GameTestHelper helper, FakePlayer player, BlockPos relative) {
        return Multiblocks.tryForm(helper.getLevel(), helper.absolutePos(relative), player).orElseThrow();
    }

    private static MultiblockMachineBlockEntity formed(GameTestHelper helper, int id, Integer... completed) {
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        FakePlayer player = playerOnProgression(helper, id, completed);
        Multiblocks.Result result = wrench(helper, player, built.at(CONTROLLER));
        helper.assertTrue(result == Multiblocks.Result.FORMED, "Smelter should form, got " + result);
        return (MultiblockMachineBlockEntity) helper.getBlockEntity(built.at(CONTROLLER));
    }

    private static void unlock(GameTestHelper helper, long progressionId, int milestoneId) {
        MinecraftServer server = helper.getLevel().getServer();
        ProgressionSnapshot current = Progressions.find(server, progressionId).orElseThrow();
        Set<Integer> completed = new HashSet<>(current.completedMilestones());
        completed.add(milestoneId);
        Progressions.store(server, new ProgressionSnapshot(progressionId, completed, current.currentMilestoneId(), current.retired()));
    }

    private static void registerRecipe(GameTestHelper helper, int id, MachineRecipe recipe) {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "test/smelter_" + id);
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        List<RecipeHolder<?>> recipes = new ArrayList<>(manager.getRecipes());
        recipes.removeIf(holder -> holder.id().equals(recipeId));
        recipes.add(new RecipeHolder<>(recipeId, recipe));
        manager.replaceRecipes(recipes);
    }

    /** Raw iron + coal into iron ingots, behind milestone {@code id}; ingredients unique to the test so recipes do not collide. */
    private static MachineRecipe ironRecipe(int id, Item input) {
        return new MachineRecipe(MachineTypes.SMELTER.id(), Optional.of(Stages.milestoneStage(id)),
                List.of(SizedIngredient.of(input, 2)), List.of(new ItemStack(Items.IRON_INGOT, 1)), 100, 10);
    }

    private static <T> T capability(GameTestHelper helper, BlockCapability<T, Direction> capability,
                                    BlockPos relative, Direction side) {
        return helper.getLevel().getCapability(capability, helper.absolutePos(relative), side);
    }

    private static void assertBlocksRestored(GameTestHelper helper, Built built, BlockPos except) {
        built.blocks().forEach((pos, state) -> {
            if (!pos.equals(except)) {
                helper.assertBlockPresent(state.getBlock(), pos);
            }
        });
    }

    // ---- formation ----------------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM)
    public static void wrenchFormsACompleteSmelter(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        FakePlayer player = playerOnProgression(helper, id);
        BlockPos controller = helper.absolutePos(built.at(CONTROLLER));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));

        InteractionResult result = ModItems.WRENCH.get().onItemUseFirst(player.getMainHandItem(), new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(controller), Direction.NORTH, controller, false)));

        helper.assertTrue(result.consumesAction(), "The Wrench should act on the controller");
        MultiblockMachineBlockEntity machine = (MultiblockMachineBlockEntity) helper.getBlockEntity(built.at(CONTROLLER));
        helper.assertTrue(machine.linkedProgressionId() == id, "The Smelter should belong to the former's progression");
        helper.assertTrue(machine.getBlockState().getValue(MachineBlock.FACING) == Direction.NORTH, "The Smelter should face north");
        helper.assertTrue(machine.parts().size() == 26, "26 parts expected, got " + machine.parts().size());
        helper.assertBlockPresent(ModBlocks.MULTIBLOCK_PART.get(), built.at(BRICK));
        MultiblockPartBlockEntity brick = (MultiblockPartBlockEntity) helper.getBlockEntity(built.at(BRICK));
        helper.assertTrue(brick.original().is(Blocks.BRICKS), "A part should remember the block it replaced");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void smelterFormsWhenRotated(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.CLOCKWISE_90, Mirror.NONE);

        Multiblocks.Result result = wrench(helper, playerOnProgression(helper, id), built.at(CONTROLLER));

        helper.assertTrue(result == Multiblocks.Result.FORMED, "A rotated Smelter should form, got " + result);
        Direction facing = helper.getBlockState(built.at(CONTROLLER)).getValue(MachineBlock.FACING);
        helper.assertTrue(facing == Direction.EAST, "A Smelter turned clockwise should face east, got " + facing);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void smelterFormsWhenMirroredAndKeepsItsPortsApart(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.COUNTERCLOCKWISE_90, Mirror.FRONT_BACK);

        Multiblocks.Result result = wrench(helper, playerOnProgression(helper, id), built.at(CONTROLLER));

        helper.assertTrue(result == Multiblocks.Result.FORMED, "A mirrored Smelter should form, got " + result);
        IItemHandler input = capability(helper, Capabilities.ItemHandler.BLOCK, built.at(INPUT_PORT), Direction.UP);
        helper.assertTrue(input != null && input.insertItem(0, new ItemStack(Items.RAW_IRON), true).isEmpty(),
                "The mirrored input port should still accept items");
        IItemHandler output = capability(helper, Capabilities.ItemHandler.BLOCK, built.at(OUTPUT_PORT), Direction.UP);
        helper.assertTrue(output != null && !output.insertItem(0, new ItemStack(Items.RAW_IRON), true).isEmpty(),
                "The mirrored output port must not accept items");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void incompleteSmelterDoesNotForm(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        helper.setBlock(built.at(BRICK), Blocks.AIR);
        helper.setBlock(built.at(CASING), Blocks.STONE);

        Multiblocks.Result result = wrench(helper, playerOnProgression(helper, id), built.at(CONTROLLER));

        helper.assertTrue(result == Multiblocks.Result.INCOMPLETE, "A Smelter with a gap and a wrong block must not form, got " + result);
        helper.assertBlockPresent(ModBlocks.SMELTER_CONTROLLER.get(), built.at(CONTROLLER));
        helper.assertBlockPresent(ModBlocks.MACHINE_CASING.get(), built.at(new BlockPos(1, -1, 1)));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void formationNeedsTheStageOfTheDefinition(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        FakePlayer player = playerOnProgression(helper, id);
        MultiblockDefinition smelter = MultiblockDefinitions.forTrigger(helper.getLevel(), ModBlocks.SMELTER_CONTROLLER.get()).orElseThrow();
        MultiblockDefinition gated = new MultiblockDefinition(smelter.id(), smelter.shape(), smelter.trigger(), smelter.master(),
                Optional.of(Stages.milestoneStage(id)));
        BlockPos controller = helper.absolutePos(built.at(CONTROLLER));

        Multiblocks.Result locked = Multiblocks.tryForm(helper.getLevel(), controller, player, gated);
        unlock(helper, id, id);
        Multiblocks.Result unlocked = Multiblocks.tryForm(helper.getLevel(), controller, player, gated);

        helper.assertTrue(locked == Multiblocks.Result.LOCKED, "Formation must wait for the milestone, got " + locked);
        helper.assertTrue(unlocked == Multiblocks.Result.FORMED, "Formation should succeed once unlocked, got " + unlocked);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void formationIsRefusedWithoutAProgression(GameTestHelper helper) {
        Built built = build(helper, Rotation.NONE, Mirror.NONE);

        Multiblocks.Result result = wrench(helper, player(helper, IDS.getAndIncrement()), built.at(CONTROLLER));

        helper.assertTrue(result == Multiblocks.Result.NO_PROGRESSION, "Formation must be refused, got " + result);
        helper.assertBlockPresent(ModBlocks.SMELTER_CONTROLLER.get(), built.at(CONTROLLER));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void formationIsRefusedForARetiredProgression(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        FakePlayer player = playerOnProgression(helper, id);
        Progressions.store(helper.getLevel().getServer(), new ProgressionSnapshot(id, Set.of(), null, true));

        Multiblocks.Result result = wrench(helper, player, built.at(CONTROLLER));

        helper.assertTrue(result == Multiblocks.Result.NOT_SERVING, "Formation must be refused, got " + result);
        helper.succeed();
    }

    // ---- disassembly ---------------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM)
    public static void breakingAPartRestoresEveryOtherBlock(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.CLOCKWISE_180, Mirror.NONE);
        wrench(helper, playerOnProgression(helper, id), built.at(CONTROLLER));

        helper.destroyBlock(built.at(BRICK));

        helper.assertBlockPresent(Blocks.AIR, built.at(BRICK));
        assertBlocksRestored(helper, built, built.at(BRICK));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void breakingTheMasterRestoresEveryOtherBlockAndDropsItsContents(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        wrench(helper, playerOnProgression(helper, id), built.at(CONTROLLER));
        MultiblockMachineBlockEntity machine = (MultiblockMachineBlockEntity) helper.getBlockEntity(built.at(CONTROLLER));
        machine.items().setStackInSlot(0, new ItemStack(Items.RAW_GOLD, 5));

        helper.destroyBlock(built.at(CONTROLLER));

        assertBlocksRestored(helper, built, built.at(CONTROLLER));
        helper.assertItemEntityPresent(Items.RAW_GOLD, built.at(CONTROLLER), 2.0);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void minedPartDropsItsOriginalBlockWithTheRightTool(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        FakePlayer player = playerOnProgression(helper, id);
        wrench(helper, player, built.at(CONTROLLER));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));

        player.gameMode.destroyBlock(helper.absolutePos(built.at(BRICK)));

        helper.assertItemEntityPresent(Items.BRICKS, built.at(BRICK), 2.0);
        assertBlocksRestored(helper, built, built.at(BRICK));
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void minedPartDropsNothingByHandWhenItsOriginalNeedsAPickaxe(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        FakePlayer player = playerOnProgression(helper, id);
        wrench(helper, player, built.at(CONTROLLER));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        player.gameMode.destroyBlock(helper.absolutePos(built.at(BRICK)));

        helper.assertItemEntityNotPresent(Items.BRICKS, built.at(BRICK), 2.0);
        helper.succeed();
    }

    // ---- ports ---------------------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM)
    public static void portsProxyTheMachine(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        MultiblockMachineBlockEntity machine = formed(helper, id);
        Built built = new Built(Map.of(), Rotation.NONE, Mirror.NONE);
        machine.items().setStackInSlot(MachineTypes.SMELTER.layout().outputSlot(0), new ItemStack(Items.IRON_INGOT, 3));

        IItemHandler input = capability(helper, Capabilities.ItemHandler.BLOCK, built.at(INPUT_PORT), Direction.EAST);
        ItemStack rest = input.insertItem(0, new ItemStack(Items.RAW_IRON, 4), false);
        IItemHandler output = capability(helper, Capabilities.ItemHandler.BLOCK, built.at(OUTPUT_PORT), Direction.WEST);
        ItemStack extracted = output.extractItem(0, 64, false);
        IEnergyStorage energy = capability(helper, Capabilities.EnergyStorage.BLOCK, built.at(ENERGY_PORT), Direction.SOUTH);
        int received = energy.receiveEnergy(500, false);

        helper.assertTrue(rest.isEmpty() && machine.items().getStackInSlot(0).is(Items.RAW_IRON), "The input port should fill the machine");
        helper.assertTrue(input.extractItem(0, 1, true).isEmpty(), "The input port must not give items back");
        helper.assertTrue(extracted.is(Items.IRON_INGOT) && extracted.getCount() == 3, "The output port should drain the machine");
        helper.assertTrue(received == 500 && machine.energy().getEnergyStored() == 500, "The energy port should charge the machine");
        helper.assertTrue(energy.extractEnergy(100, true) == 0, "Cables must not drain the machine");
        helper.assertTrue(capability(helper, Capabilities.ItemHandler.BLOCK, built.at(BRICK), Direction.UP) == null, "A brick is not a port");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void wrenchSwitchesAPortFaceOffAndItStaysOffAfterReload(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        formed(helper, id);
        Built built = new Built(Map.of(), Rotation.NONE, Mirror.NONE);
        MultiblockPartBlockEntity port = (MultiblockPartBlockEntity) helper.getBlockEntity(built.at(INPUT_PORT));

        Optional<SideMode> mode = port.cycleSide(Direction.EAST);
        var registries = helper.getLevel().registryAccess();
        MultiblockPartBlockEntity reloaded = new MultiblockPartBlockEntity(port.getBlockPos(), port.getBlockState());
        reloaded.loadWithComponents(port.saveWithoutMetadata(registries), registries);

        helper.assertTrue(mode.equals(Optional.of(SideMode.NONE)), "An input face should switch off, got " + mode);
        helper.assertTrue(capability(helper, Capabilities.ItemHandler.BLOCK, built.at(INPUT_PORT), Direction.EAST) == null, "The switched-off face must expose nothing");
        helper.assertTrue(capability(helper, Capabilities.ItemHandler.BLOCK, built.at(INPUT_PORT), Direction.UP) != null, "Other faces stay open");
        helper.assertTrue(reloaded.side(Direction.EAST) == SideMode.NONE, "The face mode should be saved");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void pipeCapabilityCachedBeforeFormationSeesThePort(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Built built = build(helper, Rotation.NONE, Mirror.NONE);
        BlockCapabilityCache<IItemHandler, Direction> cache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK,
                helper.getLevel(), helper.absolutePos(built.at(INPUT_PORT)), Direction.EAST);
        helper.assertTrue(cache.getCapability() == null, "An unformed port exposes nothing");

        wrench(helper, playerOnProgression(helper, id), built.at(CONTROLLER));

        IItemHandler port = cache.getCapability();
        helper.assertTrue(port != null && port.insertItem(0, new ItemStack(Items.RAW_IRON), true).isEmpty(),
                "A pipe's cached capability should see the formed input port");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void onlyThePlayersOfItsProgressionMaySwitchAPort(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        MultiblockMachineBlockEntity machine = formed(helper, id);
        BlockPos port = helper.absolutePos(new Built(Map.of(), Rotation.NONE, Mirror.NONE).at(INPUT_PORT));
        FakePlayer stranger = playerOnProgression(helper, id + 500_000);
        FakePlayer owner = playerOnProgression(helper, id);
        UseOnContext onEastFace = new UseOnContext(stranger, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(port), Direction.EAST, port, false));

        ModItems.WRENCH.get().onItemUseFirst(new ItemStack(ModItems.WRENCH.get()), onEastFace);
        SideMode afterStranger = ((MultiblockPartBlockEntity) helper.getLevel().getBlockEntity(port)).side(Direction.EAST);
        ModItems.WRENCH.get().onItemUseFirst(new ItemStack(ModItems.WRENCH.get()), new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(port), Direction.EAST, port, false)));
        SideMode afterOwner = ((MultiblockPartBlockEntity) helper.getLevel().getBlockEntity(port)).side(Direction.EAST);

        helper.assertTrue(machine.linkedProgressionId() == id, "The Smelter belongs to progression " + id);
        helper.assertTrue(afterStranger == SideMode.INPUT, "Another progression must not switch the port");
        helper.assertTrue(afterOwner == SideMode.NONE, "The owner's Wrench should switch the face off");
        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void smelterWaitsWhenItsOutputIsFull(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        registerRecipe(helper, id, ironRecipe(id, Items.RAW_COPPER_BLOCK));
        MultiblockMachineBlockEntity machine = formed(helper, id, id);
        machine.items().setStackInSlot(MachineTypes.SMELTER.layout().outputSlot(0), new ItemStack(Items.DIRT, 64));
        machine.items().setStackInSlot(0, new ItemStack(Items.RAW_COPPER_BLOCK, 4));
        machine.energy().receiveEnergy(10_000, false);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(machine.status() == MachineStatus.OUTPUT_FULL, "Status should be OUTPUT_FULL, got " + machine.status());
            helper.assertTrue(machine.items().getStackInSlot(0).getCount() == 4, "Inputs must not be consumed");
            helper.succeed();
        });
    }

    // ---- processing ----------------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void smelterWaitsForItsStageThenSmelts(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        registerRecipe(helper, id, ironRecipe(id, Items.RAW_IRON));
        MultiblockMachineBlockEntity machine = formed(helper, id);
        machine.items().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 4));
        machine.energy().receiveEnergy(10_000, false);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(machine.items().getStackInSlot(MachineTypes.SMELTER.layout().outputSlot(0)).isEmpty(), "A locked recipe must not run");
            helper.assertTrue(machine.status() == MachineStatus.LOCKED, "Status should be LOCKED, got " + machine.status());
            unlock(helper, id, id);
            helper.succeedWhen(() -> {
                ItemStack out = machine.items().getStackInSlot(MachineTypes.SMELTER.layout().outputSlot(0));
                helper.assertTrue(out.is(Items.IRON_INGOT), "The Smelter should smelt once its progression has the stage");
                helper.assertTrue(machine.energy().getEnergyStored() <= 10_000 - 100, "A craft should cost its energy, left " + machine.energy().getEnergyStored());
            });
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void smelterStallsWithoutEnergyAndKeepsItsInputs(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        registerRecipe(helper, id, ironRecipe(id, Items.RAW_COPPER));
        MultiblockMachineBlockEntity machine = formed(helper, id, id);
        machine.items().setStackInSlot(0, new ItemStack(Items.RAW_COPPER, 4));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(machine.status() == MachineStatus.NO_ENERGY, "Status should be NO_ENERGY, got " + machine.status());
            helper.assertTrue(machine.items().getStackInSlot(0).getCount() == 4, "Inputs must not be consumed");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void retiredProgressionStopsTheSmelter(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        registerRecipe(helper, id, ironRecipe(id, Items.RAW_GOLD));
        MultiblockMachineBlockEntity machine = formed(helper, id, id);
        Progressions.store(helper.getLevel().getServer(), new ProgressionSnapshot(id, Set.of(id), null, true));
        machine.items().setStackInSlot(0, new ItemStack(Items.RAW_GOLD, 4));
        machine.energy().receiveEnergy(10_000, false);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(machine.status() == MachineStatus.STOPPED, "Status should be STOPPED, got " + machine.status());
            helper.assertTrue(machine.items().getStackInSlot(MachineTypes.SMELTER.layout().outputSlot(0)).isEmpty(), "A retired progression's Smelter must not smelt");
            helper.assertTrue(machine.items().getStackInSlot(0).getCount() == 4, "Inputs must not be consumed");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void smelterKeepsItsStateAcrossSaveAndLoad(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        MultiblockMachineBlockEntity machine = formed(helper, id);
        machine.items().setStackInSlot(1, new ItemStack(Items.RAW_IRON, 7));
        machine.energy().receiveEnergy(900, false);
        var registries = helper.getLevel().registryAccess();

        MultiblockMachineBlockEntity copy = new MultiblockMachineBlockEntity(machine.getType(), MachineTypes.SMELTER, machine.getBlockPos(), machine.getBlockState());
        copy.loadWithComponents(machine.saveWithoutMetadata(registries), registries);

        helper.assertTrue(copy.items().getStackInSlot(1).getCount() == 7, "Inventory should round-trip");
        helper.assertTrue(copy.energy().getEnergyStored() == 900, "Energy should round-trip");
        helper.assertTrue(copy.linkedProgressionId() == id, "Progression should round-trip");
        helper.assertTrue(copy.parts().equals(machine.parts()), "Parts should round-trip");
        helper.assertTrue(copy.original().is(ModBlocks.SMELTER_CONTROLLER.get()), "The replaced controller should round-trip");
        helper.succeed();
    }
}
