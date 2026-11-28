package fr.thegostsniperfr.arffornia.gametest;

import com.mojang.authlib.GameProfile;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.shop.PendingReward;
import fr.thegostsniperfr.arffornia.shop.RewardClaimLedger;
import fr.thegostsniperfr.arffornia.shop.RewardEntry;
import fr.thegostsniperfr.arffornia.shop.RewardHandler;
import fr.thegostsniperfr.arffornia.shop.RewardsClient;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import fr.thegostsniperfr.arffornia.item.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Claim orchestration against a scripted {@link RewardsClient}: delivery happens only after a successful claim, once. */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class RewardHandlerGameTests {
    private static final AtomicLong NEXT_ID = new AtomicLong(910_000);

    private static final class ScriptedClient implements RewardsClient {
        final Queue<PendingResult> pending = new ArrayDeque<>();
        final Queue<ClaimResult> claims = new ArrayDeque<>();
        final List<String> claimKeys = new CopyOnWriteArrayList<>();

        @Override
        public synchronized CompletableFuture<PendingResult> fetchPending(UUID player) {
            PendingResult next = pending.poll();
            return CompletableFuture.completedFuture(next != null ? next : new PendingResult.Found(List.of()));
        }

        @Override
        public synchronized CompletableFuture<ClaimResult> claim(long rewardId, UUID player, String idempotencyKey) {
            claimKeys.add(rewardId + ":" + idempotencyKey);
            ClaimResult next = claims.poll();
            return CompletableFuture.completedFuture(next != null ? next : new ClaimResult.Failed());
        }

        @Override
        public CompletableFuture<StreakResult> recordDailyConnection(UUID player) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "claimer"));
        player.getInventory().clearContent();
        player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        return player;
    }

    private static PendingReward keys(long id, int count) {
        return new PendingReward(id, "streak", List.of(new RewardEntry.Key("arffornia:streak", count)));
    }

    private static void fill(ServerPlayer player, int slots) {
        for (int i = 0; i < slots; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    private static boolean nothingDropped(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty();
    }

    private static PendingReward diamonds(long id, int count) {
        return new PendingReward(id, "shop", List.of(new RewardEntry.Item("minecraft:diamond", count)));
    }

    private static void runTwice(GameTestHelper helper, RewardHandler handler, ServerPlayer player, Runnable assertions) {
        handler.claimRewardsForPlayer(player)
                .thenCompose(ignored -> CompletableFuture.supplyAsync(() -> null, helper.getLevel().getServer()))
                .thenCompose(ignored -> handler.claimRewardsForPlayer(player))
                .whenComplete((ignored, error) -> helper.getLevel().getServer().execute(() -> {
                    if (error != null) {
                        helper.fail("claim flow failed: " + error);
                        return;
                    }
                    assertions.run();
                    helper.succeed();
                }));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void claimedRewardIsDeliveredOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(diamonds(id, 2))));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(diamonds(id, 2)));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 2, "2 diamonds once");
            helper.assertTrue(client.claimKeys.size() == 1, "claimed once");
            helper.assertTrue(RewardClaimLedger.get(helper.getLevel().getServer()).openClaimsOf(player.getUUID()).isEmpty(), "ledger closed");
        });
    }

    private static void claimWithAnnouncer(GameTestHelper helper, PendingReward reward, java.util.function.Consumer<List<Long>> assertions) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(reward)));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(reward));
        List<Long> announced = new CopyOnWriteArrayList<>();
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player,
                (buyer, announcedReward) -> announced.add(announcedReward.id()));
        runTwice(helper, handler, player, () -> assertions.accept(announced));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void claimedAnnouncedRewardIsAnnouncedOnce(GameTestHelper helper) {
        long id = NEXT_ID.incrementAndGet();
        PendingReward reward = new PendingReward(id, "shop", List.of(new RewardEntry.Item("minecraft:diamond", 1)), true, java.util.Map.of("en", "Pack", "fr", "Pack"));

        claimWithAnnouncer(helper, reward, announced -> helper.assertTrue(announced.equals(List.of(id)), "announced once, got " + announced));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void claimedRewardWithoutAnnounceFlagIsNotAnnounced(GameTestHelper helper) {
        claimWithAnnouncer(helper, diamonds(NEXT_ID.incrementAndGet(), 1), announced -> helper.assertTrue(announced.isEmpty(), "nothing announced, got " + announced));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void claimWithUnknownOutcomeIsRetriedWithTheSameKeyEvenWhenNoLongerPending(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(diamonds(id, 3))));
        client.claims.add(new RewardsClient.ClaimResult.Failed());
        client.pending.add(new RewardsClient.PendingResult.Found(List.of()));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(diamonds(id, 3)));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(client.claimKeys.size() == 2, "claimed twice, got " + client.claimKeys);
            helper.assertTrue(client.claimKeys.get(0).equals(client.claimKeys.get(1)), "same reward and key");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "delivered after the replay");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void rejectedClaimDeliversNothingAndIsNotRetried(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(diamonds(id, 1))));
        client.claims.add(new RewardsClient.ClaimResult.Rejected(409));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "nothing delivered");
            helper.assertTrue(client.claimKeys.size() == 1, "not retried");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void rewardClaimedWhileThePlayerIsAwayIsDeliveredOnTheNextClaim(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        AtomicInteger lookups = new AtomicInteger();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(diamonds(id, 4))));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(diamonds(id, 4)));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(diamonds(id, 4)));
        // Lookup 1 plans the claim, lookup 2 settles it: the player has left by then.
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> lookups.incrementAndGet() == 2 ? null : player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(client.claimKeys.size() == 2, "claimed again on the next attempt, got " + client.claimKeys);
            helper.assertTrue(client.claimKeys.get(0).equals(client.claimKeys.get(1)), "replayed with the same key");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 4, "delivered once");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void keyRewardIsDeliveredOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(keys(id, 2))));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(keys(id, 2)));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(player.getInventory().countItem(ModItems.CRATE_KEY.get()) == 2, "2 keys once");
            helper.assertTrue(client.claimKeys.size() == 1, "claimed once");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void rewardThatDoesNotFitStaysPendingAndNothingDrops(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        fill(player, 35);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(diamonds(id, 65))));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(client.claimKeys.isEmpty(), "not claimed: 2 slots needed, 1 free");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "nothing given");
            helper.assertTrue(nothingDropped(helper, player), "nothing dropped");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void rewardLargerThanTheInventoryStaysPendingWithAnEmptyInventory(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(diamonds(id, 64 * 37))));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(client.claimKeys.isEmpty(), "a 37-slot reward is never claimed");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "nothing given");
            helper.assertTrue(nothingDropped(helper, player), "nothing dropped");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void luckyBoxIsRolledAndCheckedBeforeItIsClaimed(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        fill(player, 36);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(new PendingReward(id, "streak", List.of(new RewardEntry.LuckyBox("arffornia:streak", 1))))));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        runTwice(helper, handler, player, () -> {
            helper.assertTrue(client.claimKeys.isEmpty(), "full inventory: the box is not claimed");
            helper.assertTrue(nothingDropped(helper, player), "nothing dropped");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void keysThatFitAreClaimedOnJoinAndOtherRewardsWait(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ScriptedClient client = new ScriptedClient();
        long keyId = NEXT_ID.incrementAndGet();
        long itemId = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(keys(keyId, 1), diamonds(itemId, 1))));
        client.claims.add(new RewardsClient.ClaimResult.Claimed(keys(keyId, 1)));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        handler.onPlayerJoined(player).whenComplete((ignored, error) -> helper.getLevel().getServer().execute(() -> {
            helper.assertTrue(error == null, "join flow failed: " + error);
            helper.assertTrue(player.getInventory().countItem(ModItems.CRATE_KEY.get()) == 1, "key given on join");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "the item reward waits for the command");
            helper.assertTrue(client.claimKeys.size() == 1 && client.claimKeys.get(0).startsWith(keyId + ":"), "only the key reward claimed");
            helper.succeed();
        }));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void keysThatDoNotFitOnJoinStayPending(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        fill(player, 36);
        ScriptedClient client = new ScriptedClient();
        long id = NEXT_ID.incrementAndGet();
        client.pending.add(new RewardsClient.PendingResult.Found(List.of(keys(id, 1))));
        RewardHandler handler = new RewardHandler(client, helper.getLevel().getServer(), uuid -> player);

        handler.onPlayerJoined(player).whenComplete((ignored, error) -> helper.getLevel().getServer().execute(() -> {
            helper.assertTrue(client.claimKeys.isEmpty(), "not claimed");
            helper.assertTrue(nothingDropped(helper, player), "nothing dropped");
            helper.succeed();
        }));
    }
}
