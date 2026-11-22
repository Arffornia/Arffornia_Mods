package fr.thegostsniperfr.arffornia.gametest;

import com.mojang.authlib.GameProfile;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.crate.CrateBlockEntity;
import fr.thegostsniperfr.arffornia.crate.CrateEvents;
import fr.thegostsniperfr.arffornia.crate.CrateOpening;
import fr.thegostsniperfr.arffornia.crate.CrateSequence;
import fr.thegostsniperfr.arffornia.item.CrateKeyItem;
import fr.thegostsniperfr.arffornia.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Spec 020 Testing table, mod side: opening a crate with keys. */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class CrateGameTests {
    private static final ResourceLocation STREAK = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "streak");
    private static final ResourceLocation VOTE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "vote");
    private static final BlockPos CRATE = new BlockPos(1, 2, 1);

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        player.setPos(helper.absoluteVec(new Vec3(1.5, 2, 3)));
        return player;
    }

    private static CrateBlockEntity crate(GameTestHelper helper, ResourceLocation id) {
        helper.setBlock(CRATE, ModBlocks.CRATE.get().defaultBlockState());
        CrateBlockEntity crate = (CrateBlockEntity) helper.getBlockEntity(CRATE);
        crate.setCrate(id);
        return crate;
    }

    private static void giveKeys(ServerPlayer player, ResourceLocation crate, UUID owner, int count) {
        player.setItemInHand(InteractionHand.MAIN_HAND, CrateKeyItem.stack(crate, owner, "owner", count));
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(CRATE);
        BlockHitResult hit = new BlockHitResult(pos.getCenter(), Direction.NORTH, pos, false);
        helper.getLevel().getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static int keys(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.CRATE_KEY.get());
    }

    private static int lootCount(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.is(ModItems.CRATE_KEY.get()) && !stack.is(Items.DIRT)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean nothingDropped(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(CRATE)).inflate(10)).isEmpty();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void crateConsumesExactlyOneKeyAndGivesTheLootAtTheEnd(GameTestHelper helper) {
        FakePlayer player = player(helper, "opener");
        CrateBlockEntity crate = crate(helper, STREAK);
        giveKeys(player, STREAK, player.getUUID(), 3);

        use(helper, player);

        helper.assertTrue(keys(player) == 2, "one key consumed, got " + keys(player));
        helper.assertTrue(crate.isOpening(), "the sequence runs");
        helper.assertTrue(lootCount(player) == 0, "the loot is given at the end of the sequence");
        helper.runAfterDelay(CrateSequence.END + 5, () -> {
            helper.assertTrue(!crate.isOpening(), "the sequence ended");
            helper.assertTrue(lootCount(player) > 0, "loot given");
            helper.assertTrue(keys(player) == 2, "still exactly one key consumed");
            helper.assertTrue(nothingDropped(helper), "nothing dropped");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void fullInventoryKeepsTheKey(GameTestHelper helper) {
        FakePlayer player = player(helper, "full");
        crate(helper, STREAK);
        for (int i = 1; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        giveKeys(player, STREAK, player.getUUID(), 2);

        use(helper, player);

        helper.assertTrue(keys(player) == 2, "no key consumed");
        helper.assertTrue(lootCount(player) == 0, "nothing given");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void lastKeyFreesItsOwnSlotForTheLoot(GameTestHelper helper) {
        FakePlayer player = player(helper, "lastkey");
        CrateBlockEntity crate = crate(helper, STREAK);
        for (int i = 1; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        giveKeys(player, STREAK, player.getUUID(), 1);

        use(helper, player);

        helper.assertTrue(keys(player) == 0 && crate.isOpening(), "the key's slot is room for the loot");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void anotherPlayersKeyIsRefused(GameTestHelper helper) {
        FakePlayer player = player(helper, "thief");
        CrateBlockEntity crate = crate(helper, STREAK);
        giveKeys(player, STREAK, UUID.randomUUID(), 1);

        use(helper, player);

        helper.assertTrue(keys(player) == 1, "the key is kept");
        helper.assertTrue(!crate.isOpening(), "the crate does not open");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void aKeyForAnotherCrateIsRefused(GameTestHelper helper) {
        FakePlayer player = player(helper, "wrongkey");
        CrateBlockEntity crate = crate(helper, STREAK);
        giveKeys(player, VOTE, player.getUUID(), 1);

        use(helper, player);

        helper.assertTrue(keys(player) == 1 && !crate.isOpening(), "a vote key does not open the streak crate");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aBusyCrateRefusesASecondPlayer(GameTestHelper helper) {
        FakePlayer first = player(helper, "first");
        FakePlayer second = player(helper, "second");
        crate(helper, STREAK);
        giveKeys(first, STREAK, first.getUUID(), 1);
        giveKeys(second, STREAK, second.getUUID(), 1);

        use(helper, first);
        use(helper, second);

        helper.assertTrue(keys(first) == 0, "the first player opens");
        helper.assertTrue(keys(second) == 1, "the second player keeps their key");
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void sneakingSkipsToTheReveal(GameTestHelper helper) {
        FakePlayer player = player(helper, "skipper");
        CrateBlockEntity crate = crate(helper, STREAK);
        giveKeys(player, STREAK, player.getUUID(), 2);
        use(helper, player);

        player.setShiftKeyDown(true);
        use(helper, player);

        helper.assertTrue(CrateOpening.tickOf(crate) == CrateSequence.REVEAL, "jumped to the reveal, at " + CrateOpening.tickOf(crate));
        helper.assertTrue(keys(player) == 1, "skipping does not use a key");
        helper.runAfterDelay(CrateSequence.END - CrateSequence.REVEAL + 3, () -> {
            helper.assertTrue(lootCount(player) > 0, "loot given after the shortened sequence");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void leavingDuringTheSequenceStillGivesTheLootOnTheNextJoin(GameTestHelper helper) {
        FakePlayer player = player(helper, "leaver");
        CrateBlockEntity crate = crate(helper, STREAK);
        giveKeys(player, STREAK, player.getUUID(), 1);
        use(helper, player);

        player.disconnect();

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!crate.isOpening(), "the sequence ends when the opener leaves");
            helper.assertTrue(nothingDropped(helper), "nothing dropped");
            FakePlayer back = new FakePlayer(helper.getLevel(), player.getGameProfile());
            back.getInventory().clearContent();
            CrateEvents.giveStashedLoot(back);
            helper.assertTrue(lootCount(back) > 0, "the loot is given on the next join");
            helper.succeed();
        });
    }
}
