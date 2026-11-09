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
}
