package fr.thegostsniperfr.arffornia.gametest;

import com.mojang.authlib.GameProfile;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.InMemoryProgressionBackend;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import fr.thegostsniperfr.arffornia.network.ServerboundLaunchElevatorPacket;
import fr.thegostsniperfr.arffornia.network.ServerboundSelectRecipePacket;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import fr.thegostsniperfr.arffornia.screen.SpaceElevatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless GameTests for the Crafter and the Space Elevator, run by {@code ./gradlew runGameTestServer} against the
 * in-memory {@link InMemoryProgressionBackend} ({@code -Darffornia.backend=fake}).
 * <p>
 * Tests in a batch run in parallel against the shared fake, so each test uses its own ids (see {@link #nextId()}).
 */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class ArfforniaGameTests {
    /** 5x4x5 template: stone floor at y=0, air above. */
    private static final String PLATFORM = "platform";
    /** Crafter / elevator master position inside the template (part block goes to y=2). */
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    private static final AtomicInteger IDS = new AtomicInteger(10_000);

    private ArfforniaGameTests() {
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static int nextId() {
        return IDS.getAndIncrement();
    }

    private static InMemoryProgressionBackend fake() {
        return ProgressionBackends.fake();
    }

    private static FakePlayer player(GameTestHelper helper, String name, Vec3 relativePos) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 abs = helper.absoluteVec(relativePos);
        player.moveTo(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    /** A player standing next to the machine. */
    private static FakePlayer playerNearMachine(GameTestHelper helper, String name) {
        return player(helper, name, new Vec3(0.5, 1, 0.5));
    }

    private static ArfforniaApiDtos.CustomRecipe recipe(int unlockId, int milestoneId, int energy, int time) {
        return new ArfforniaApiDtos.CustomRecipe(
                unlockId,
                "arffornia:test_recipe_" + unlockId,
                List.of(new ArfforniaApiDtos.RecipeIngredient("minecraft:cobblestone", 1)),
                List.of(new ArfforniaApiDtos.RecipeResult("minecraft:stone", 1)),
                energy,
                time,
                unlockId,
                milestoneId);
    }

    private static void registerRecipe(ArfforniaApiDtos.CustomRecipe recipe) {
        CustomRecipeManager.putRecipeForTesting(recipe);
    }

    /** Places a Crafter owned (linked) by {@code owner}, whose progression is {@code progressionId}. */
    private static CrafterBlockEntity placeCrafter(GameTestHelper helper, ServerPlayer owner, long progressionId) {
        fake().linkPlayer(owner.getUUID(), progressionId);
        placeTwoBlockMachine(helper, ModBlocks.CRAFTER_BLOCK.get().defaultBlockState(), ModBlocks.CRAFTER_PART_BLOCK.get().defaultBlockState());
        CrafterBlockEntity be = (CrafterBlockEntity) helper.getBlockEntity(MACHINE);
        be.setOwner(owner); // completes synchronously with the in-memory backend
        helper.assertTrue(be.getLinkedProgressionId() == progressionId, "Crafter should be linked to progression " + progressionId);
        return be;
    }

    private static SpaceElevatorBlockEntity placeElevator(GameTestHelper helper, ServerPlayer owner, long progressionId) {
        fake().linkPlayer(owner.getUUID(), progressionId);
        placeTwoBlockMachine(helper, ModBlocks.SPACE_ELEVATOR.get().defaultBlockState(), ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get().defaultBlockState());
        SpaceElevatorBlockEntity be = (SpaceElevatorBlockEntity) helper.getBlockEntity(MACHINE);
        be.setOwner(owner);
        helper.assertTrue(owner.getUUID().equals(be.getOwnerUuid()), "Elevator should record its owner");
        return be;
    }

    private static void placeTwoBlockMachine(GameTestHelper helper, BlockState master, BlockState part) {
        helper.setBlock(MACHINE, master);
        helper.setBlock(MACHINE.above(), part);
    }

    private static CrafterMenu openCrafter(ServerPlayer player, CrafterBlockEntity be) {
        CrafterMenu menu = new CrafterMenu(1, player.getInventory(), be);
        player.containerMenu = menu; // FakePlayer.openMenu is a no-op
        return menu;
    }

    private static SpaceElevatorMenu openElevator(ServerPlayer player, SpaceElevatorBlockEntity be) {
        SpaceElevatorMenu menu = new SpaceElevatorMenu(1, player.getInventory(), be);
        player.containerMenu = menu;
        return menu;
    }

    private static ArfforniaApiDtos.MilestoneDetails milestone(int id, int amount) {
        return new ArfforniaApiDtos.MilestoneDetails(id, "Test milestone " + id, "", 1, 1, 0,
                Collections.emptyList(),
                List.of(new ArfforniaApiDtos.MilestoneRequirement("minecraft:iron_ingot", "Iron Ingot", amount)));
    }

    // ---- Crafter: crafting and energy -------------------------------------------------------------------------

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void crafterCraftsWithEnergy(GameTestHelper helper) {
        int id = nextId();
        // energy < time: before the ceil fix this recipe cost 0 FE.
        registerRecipe(recipe(id, id, 10, 20));
        CrafterBlockEntity be = placeCrafter(helper, playerNearMachine(helper, "owner" + id), id);
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
        ((IEnergyStorage) be.energyStorage).receiveEnergy(1000, false);
        be.setSelectedRecipe(id);

        helper.succeedWhen(() -> {
            ItemStack out = be.itemHandler.getStackInSlot(CrafterBlockEntity.INPUT_SLOTS);
            helper.assertTrue(out.is(Items.STONE) && out.getCount() >= 1, "Crafter should produce stone");
            helper.assertTrue(be.getEnergy() <= 1000 - 20, "One craft of 20 ticks should draw at least 1 FE per tick, energy=" + be.getEnergy());
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void crafterStallsWithoutEnergy(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 400, 20));
        CrafterBlockEntity be = placeCrafter(helper, playerNearMachine(helper, "owner" + id), id);
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
        be.setSelectedRecipe(id);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.itemHandler.getStackInSlot(CrafterBlockEntity.INPUT_SLOTS).isEmpty(), "No output without energy");
            helper.assertTrue(be.getProgress() == 0, "No progress without energy");
            helper.assertTrue(be.itemHandler.getStackInSlot(0).getCount() == 4, "Inputs must not be consumed");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void crafterStopsWhenOutputIsFull(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 0, 5));
        CrafterBlockEntity be = placeCrafter(helper, playerNearMachine(helper, "owner" + id), id);
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
        be.itemHandler.setStackInSlot(CrafterBlockEntity.INPUT_SLOTS, new ItemStack(Items.STONE, 64));
        be.setSelectedRecipe(id);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.itemHandler.getStackInSlot(0).getCount() == 4, "Inputs must not be consumed when the output is full");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void crafterIgnoresRecipeWithoutItemId(GameTestHelper helper) {
        int id = nextId();
        // A tag ingredient from the backend has no item id.
        registerRecipe(new ArfforniaApiDtos.CustomRecipe(id, "arffornia:tag_recipe_" + id,
                List.of(new ArfforniaApiDtos.RecipeIngredient(null, 1)),
                List.of(new ArfforniaApiDtos.RecipeResult("minecraft:stone", 1)), 0, 5, id, id));
        CrafterBlockEntity be = placeCrafter(helper, playerNearMachine(helper, "owner" + id), id);
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
        be.setSelectedRecipe(id);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.itemHandler.getStackInSlot(CrafterBlockEntity.INPUT_SLOTS).isEmpty(), "Unusable recipe must not craft");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void crafterKeepsStateAcrossSaveAndLoad(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 0, 200));
        CrafterBlockEntity be = placeCrafter(helper, playerNearMachine(helper, "owner" + id), id);
        be.itemHandler.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 7));
        ((IEnergyStorage) be.energyStorage).receiveEnergy(500, false);
        be.setSelectedRecipe(id);

        var registries = helper.getLevel().registryAccess();
        var tag = be.saveWithoutMetadata(registries);
        CrafterBlockEntity copy = new CrafterBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(tag, registries);

        helper.assertTrue(copy.itemHandler.getStackInSlot(3).getCount() == 7, "Inventory should round-trip");
        helper.assertTrue(copy.getEnergy() == 500, "Energy should round-trip");
        helper.assertTrue(copy.getLinkedProgressionId() == id, "Linked progression should round-trip");
        helper.assertTrue(Integer.valueOf(id).equals(copy.getSelectedRecipeMilestoneUnlockId()), "Selected recipe should round-trip");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void removingCrafterRemovesPartBlock(GameTestHelper helper) {
        int id = nextId();
        placeCrafter(helper, playerNearMachine(helper, "owner" + id), id);
        helper.destroyBlock(MACHINE);
        helper.assertBlockNotPresent(ModBlocks.CRAFTER_PART_BLOCK.get(), MACHINE.above());
        helper.succeed();
    }

    // ---- Crafter: recipe selection validation -----------------------------------------------------------------

    @GameTest(template = PLATFORM)
    public static void selectRecipeAcceptedWhenUnlocked(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 0, 20));
        FakePlayer player = playerNearMachine(helper, "p" + id);
        CrafterBlockEntity be = placeCrafter(helper, player, id);
        fake().completeMilestone(id, id);
        openCrafter(player, be);

        ServerboundSelectRecipePacket.processOnServer(player, new ServerboundSelectRecipePacket(be.getBlockPos(), id))
                .thenAccept(ok -> helper.assertTrue(ok, "Selection should be accepted"));
        helper.succeedWhen(() -> helper.assertTrue(Integer.valueOf(id).equals(be.getSelectedRecipeMilestoneUnlockId()), "Recipe should be selected"));
    }

    @GameTest(template = PLATFORM)
    public static void selectRecipeRejectedForLockedMilestone(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 0, 20));
        FakePlayer player = playerNearMachine(helper, "p" + id);
        CrafterBlockEntity be = placeCrafter(helper, player, id);
        openCrafter(player, be); // milestone NOT completed

        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());
        ServerboundSelectRecipePacket.processOnServer(player, new ServerboundSelectRecipePacket(be.getBlockPos(), id)).thenAccept(results::add);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(results.equals(List.of(false)), "Selection must be rejected, got " + results);
            helper.assertTrue(be.getSelectedRecipeMilestoneUnlockId() == null, "No recipe must be selected");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void selectRecipeRejectedWithoutOpenMenu(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 0, 20));
        FakePlayer player = playerNearMachine(helper, "p" + id);
        CrafterBlockEntity be = placeCrafter(helper, player, id);
        fake().completeMilestone(id, id); // unlocked, but the menu is not open

        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());
        ServerboundSelectRecipePacket.processOnServer(player, new ServerboundSelectRecipePacket(be.getBlockPos(), id)).thenAccept(results::add);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(results.equals(List.of(false)), "Selection must be rejected, got " + results);
            helper.assertTrue(be.getSelectedRecipeMilestoneUnlockId() == null, "No recipe must be selected");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void selectRecipeRejectedFromFarAway(GameTestHelper helper) {
        int id = nextId();
        registerRecipe(recipe(id, id, 0, 20));
        FakePlayer owner = playerNearMachine(helper, "owner" + id);
        CrafterBlockEntity be = placeCrafter(helper, owner, id);
        fake().completeMilestone(id, id);
        FakePlayer far = player(helper, "far" + id, new Vec3(2.5, 1, 40.5));
        openCrafter(far, be);

        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());
        ServerboundSelectRecipePacket.processOnServer(far, new ServerboundSelectRecipePacket(be.getBlockPos(), id)).thenAccept(results::add);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(results.equals(List.of(false)), "Remote selection must be rejected, got " + results);
            helper.assertTrue(be.getSelectedRecipeMilestoneUnlockId() == null, "No recipe must be selected");
            helper.succeed();
        });
    }

    // ---- Space Elevator ---------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM)
    public static void elevatorLaunchCreditsOwner(GameTestHelper helper) {
        int id = nextId();
        FakePlayer owner = playerNearMachine(helper, "owner" + id);
        SpaceElevatorBlockEntity be = placeElevator(helper, owner, id);
        fake().putMilestone(milestone(id, 3));
        be.setCachedMilestoneDetails(milestone(id, 3));
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));

        // A teammate (different player, own progression) launches the owner's elevator.
        FakePlayer teammate = playerNearMachine(helper, "mate" + id);
        fake().linkPlayer(teammate.getUUID(), id + 500_000L);
        openElevator(teammate, be);

        helper.assertTrue(ServerboundLaunchElevatorPacket.processOnServer(teammate, new ServerboundLaunchElevatorPacket(be.getBlockPos())),
                "Launch from the open menu nearby should be accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(fake().isCompleted(id, id), "Milestone should be completed in the owner's progression");
            helper.assertFalse(fake().isCompleted(id + 500_000L, id), "The launcher's own progression must not be credited");
            helper.assertTrue(be.countItems(Items.IRON_INGOT) == 2, "Requirements should be consumed, left=" + be.countItems(Items.IRON_INGOT));
            helper.assertFalse(be.isLaunching(), "Launch should be finished");
        });
    }

    @GameTest(template = PLATFORM)
    public static void elevatorCannotBeLaunchedRemotely(GameTestHelper helper) {
        int id = nextId();
        FakePlayer owner = playerNearMachine(helper, "owner" + id);
        SpaceElevatorBlockEntity be = placeElevator(helper, owner, id);
        be.setCachedMilestoneDetails(milestone(id, 1));
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        FakePlayer far = player(helper, "far" + id, new Vec3(2.5, 1, 40.5));
        openElevator(far, be);

        helper.assertFalse(ServerboundLaunchElevatorPacket.processOnServer(far, new ServerboundLaunchElevatorPacket(be.getBlockPos())),
                "Remote launch must be rejected");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(be.countItems(Items.IRON_INGOT) == 1, "Items must not be consumed");
            helper.assertFalse(fake().isCompleted(id, id), "Milestone must not be completed");
            helper.succeed();
        });
    }

    @GameTest(template = PLATFORM)
    public static void elevatorLaunchRejectedWithoutOpenMenu(GameTestHelper helper) {
        int id = nextId();
        FakePlayer owner = playerNearMachine(helper, "owner" + id);
        SpaceElevatorBlockEntity be = placeElevator(helper, owner, id);
        be.setCachedMilestoneDetails(milestone(id, 1));
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));

        helper.assertFalse(ServerboundLaunchElevatorPacket.processOnServer(owner, new ServerboundLaunchElevatorPacket(be.getBlockPos())),
                "Launch without the menu open must be rejected");
        helper.assertTrue(be.countItems(Items.IRON_INGOT) == 1, "Items must not be consumed");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void elevatorRefundsWhenBackendFails(GameTestHelper helper) {
        int id = nextId();
        FakePlayer owner = playerNearMachine(helper, "owner" + id);
        SpaceElevatorBlockEntity be = placeElevator(helper, owner, id);
        fake().failAddMilestoneFor(id);
        be.setCachedMilestoneDetails(milestone(id, 2));
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        openElevator(owner, be);

        helper.assertTrue(ServerboundLaunchElevatorPacket.processOnServer(owner, new ServerboundLaunchElevatorPacket(be.getBlockPos())),
                "Launch should be attempted");
        helper.succeedWhen(() -> {
            helper.assertFalse(be.isLaunching(), "Launch should be finished");
            helper.assertTrue(owner.getInventory().countItem(Items.IRON_INGOT) == 2, "Consumed items should be refunded to the launcher");
            helper.assertFalse(fake().isCompleted(id, id), "Milestone must not be completed");
        });
    }

    @GameTest(template = PLATFORM)
    public static void elevatorDoesNotLaunchTwice(GameTestHelper helper) {
        int id = nextId();
        FakePlayer owner = playerNearMachine(helper, "owner" + id);
        SpaceElevatorBlockEntity be = placeElevator(helper, owner, id);
        be.setCachedMilestoneDetails(milestone(id, 1));
        be.itemHandler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
        openElevator(owner, be);

        ServerboundLaunchElevatorPacket packet = new ServerboundLaunchElevatorPacket(be.getBlockPos());
        ServerboundLaunchElevatorPacket.processOnServer(owner, packet);
        ServerboundLaunchElevatorPacket.processOnServer(owner, packet);
        helper.succeedWhen(() -> {
            helper.assertFalse(be.isLaunching(), "Launch should be finished");
            long calls = fake().addMilestoneCalls().stream().filter(c -> c.milestoneId() == id).count();
            helper.assertTrue(calls == 1, "Exactly one completion call expected, got " + calls);
            helper.assertTrue(be.countItems(Items.IRON_INGOT) == 4, "Requirements consumed once");
        });
    }

}
