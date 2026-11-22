package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Delivers website rewards: list the player's pending rewards, claim each one with a key recorded in
 * {@link RewardClaimLedger}, then run the fixed action of each typed entry. A reward is delivered only after its claim
 * succeeded. A claim whose outcome is unknown, or whose player left, is claimed again with the same key on the next
 * attempt; the website answers with a replay.
 *
 * <p>Spec 020 "Inventory room": a reward is claimed only when all of it fits in the inventory, lucky boxes rolled first.
 * Nothing is ever dropped: a reward too large for any inventory stays pending, and what no longer fits at delivery goes
 * to the {@link LootStash}. Rewards made only of keys are claimed on join (D5).
 */
public class RewardHandler {
    private final RewardsClient client;
    private final MinecraftServer server;
    private final RewardDelivery delivery;
    private final Function<UUID, ServerPlayer> onlinePlayer;

    private final Set<UUID> claimsInFlight = ConcurrentHashMap.newKeySet();

    public RewardHandler(RewardsClient client, MinecraftServer server) {
        this(client, server, uuid -> server.getPlayerList().getPlayer(uuid));
    }

    public RewardHandler(RewardsClient client, MinecraftServer server, Function<UUID, ServerPlayer> onlinePlayer) {
        this.client = client;
        this.server = server;
        this.delivery = new RewardDelivery(server);
        this.onlinePlayer = onlinePlayer;
    }

    /**
     * Records the daily connection (streak), gives stashed loot and the keys that fit (D5), then tells the player what
     * happened and what is still waiting.
     */
    public CompletableFuture<Void> onPlayerJoined(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return client.recordDailyConnection(uuid)
                .thenCompose(streak -> client.fetchPending(uuid).thenApply(result -> new JoinState(
                        streak != null && streak.firstConnectionToday() ? streak.dayStreak() : null, result)))
                .thenComposeAsync(state -> claimKeysOnJoin(uuid, state.pending())
                        .thenApply(outcome -> new JoinNotice(state.streakDay(), outcome)), server)
                .thenAcceptAsync(notice -> announce(uuid, notice), server)
                .exceptionally(error -> {
                    Arffornia.LOGGER.error("Join rewards for {} failed", uuid, error);
                    return null;
                });
    }

    private record JoinState(@Nullable Integer streakDay, RewardsClient.PendingResult pending) {
    }

    private record JoinNotice(@Nullable Integer streakDay, JoinOutcome outcome) {
    }

    /** What the join did: keys given, slots to free for keys that did not fit, other rewards left for the command. */
    private record JoinOutcome(int keysDelivered, int slotsToFree, boolean othersWaiting) {
        static final JoinOutcome NOTHING = new JoinOutcome(0, 0, false);
    }

    private static boolean onlyKeys(PendingReward reward) {
        return reward.isFullySupported() && reward.contents().stream().allMatch(e -> e instanceof RewardEntry.Key);
    }

    private CompletableFuture<JoinOutcome> claimKeysOnJoin(UUID uuid, RewardsClient.PendingResult result) {
        ServerPlayer player = deliverableTarget(uuid);
        if (player == null || !(result instanceof RewardsClient.PendingResult.Found found)) {
            return CompletableFuture.completedFuture(JoinOutcome.NOTHING);
        }
        int stashMissing = LootStash.get(server).deliver(player);
        boolean othersWaiting = stashMissing > 0 || found.rewards().stream().anyMatch(r -> !onlyKeys(r));
        boolean keysWaiting = found.rewards().stream().anyMatch(RewardHandler::onlyKeys);
        if (!keysWaiting || !claimsInFlight.add(uuid)) {
            return CompletableFuture.completedFuture(new JoinOutcome(0, 0, othersWaiting || keysWaiting));
        }
        return planAndClaim(player, found, RewardHandler::onlyKeys)
                .whenComplete((ignored, error) -> claimsInFlight.remove(uuid))
                .thenApply(summary -> new JoinOutcome(summary.keysDelivered(), summary.plan().slotsToFree(),
                        othersWaiting || !summary.plan().undeliverable().isEmpty() || !summary.plan().tooLarge().isEmpty()));
    }

    // The chat lines stay for the log; the title is what the player actually sees on join.
    private void announce(UUID uuid, JoinNotice notice) {
        JoinOutcome outcome = notice.outcome();
        Integer streakDay = notice.streakDay();
        boolean keysToOpen = outcome.keysDelivered() > 0;
        if (streakDay == null && !keysToOpen && outcome.slotsToFree() == 0 && !outcome.othersWaiting()) {
            return;
        }
        ServerPlayer player = onlinePlayer.apply(uuid);
        if (player == null) {
            return;
        }
        if (streakDay != null) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.streak", streakDay).withStyle(ChatFormatting.GOLD));
        }
        if (keysToOpen) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.keys_delivered", outcome.keysDelivered()).withStyle(ChatFormatting.GREEN));
        }
        if (outcome.slotsToFree() > 0) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.free_slots", outcome.slotsToFree(), claimCommand()).withStyle(ChatFormatting.RED));
        } else if (outcome.othersWaiting()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.pending").withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.claim_hint", claimCommand()).withStyle(ChatFormatting.YELLOW));
        }

        boolean keyWaiting = keysToOpen || outcome.slotsToFree() > 0;
        Component title;
        if (streakDay != null) {
            title = Component.translatable(keyWaiting ? "title.arffornia.rewards.streak_key" : "title.arffornia.rewards.streak", streakDay)
                    .withStyle(ChatFormatting.GOLD);
        } else {
            title = Component.translatable("title.arffornia.rewards.pending").withStyle(ChatFormatting.GREEN);
        }
        Component subtitle = null;
        if (outcome.slotsToFree() > 0) {
            subtitle = Component.translatable("title.arffornia.rewards.free_slots", outcome.slotsToFree()).withStyle(ChatFormatting.RED);
        } else if (keysToOpen) {
            subtitle = Component.translatable("title.arffornia.rewards.open_at_spawn").withStyle(ChatFormatting.YELLOW);
        } else if (outcome.othersWaiting()) {
            subtitle = Component.translatable("title.arffornia.rewards.claim_hint", Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.YELLOW);
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    /** The claim command, runnable by a click in the chat. */
    static Component claimCommand() {
        String command = Arffornia.CLAIM_REWARD_COMMAND.getString();
        return Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.arffornia.rewards.click_to_claim"))));
    }

    /** @return the claim flow, completed when every claim of this attempt has an outcome */
    public CompletableFuture<Void> claimRewardsForPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!claimsInFlight.add(uuid)) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.busy").withStyle(ChatFormatting.YELLOW));
            return CompletableFuture.completedFuture(null);
        }
        int stashMissing = LootStash.get(server).deliver(player);

        return client.fetchPending(uuid)
                .thenComposeAsync(result -> claimAll(uuid, result, stashMissing), server)
                .whenComplete((ignored, error) -> {
                    claimsInFlight.remove(uuid);
                    if (error != null) {
                        Arffornia.LOGGER.error("Reward claim for {} failed", uuid, error);
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.error").withStyle(ChatFormatting.RED));
                    }
                });
    }

    private CompletableFuture<Void> claimAll(UUID uuid, RewardsClient.PendingResult result, int stashMissing) {
        ServerPlayer player = deliverableTarget(uuid);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        switch (result) {
            case RewardsClient.PendingResult.NotLinked ignored -> {
                player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.not_linked").withStyle(ChatFormatting.RED));
                return CompletableFuture.completedFuture(null);
            }
            case RewardsClient.PendingResult.Failed ignored -> {
                player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.error").withStyle(ChatFormatting.RED));
                return CompletableFuture.completedFuture(null);
            }
            case RewardsClient.PendingResult.Found found -> {
                return planAndClaim(player, found, reward -> true).thenAccept(summary -> report(uuid, summary, stashMissing));
            }
        }
    }

    /** The result of one claim attempt. */
    private record ClaimSummary(RewardPlanner.Plan plan, List<Boolean> outcomes, int keysDelivered) {
    }

    /** Server thread, with the player just found online. */
    private CompletableFuture<ClaimSummary> planAndClaim(ServerPlayer player, RewardsClient.PendingResult.Found found, Predicate<PendingReward> eligible) {
        UUID uuid = player.getUUID();
        RewardClaimLedger ledger = RewardClaimLedger.get(server);
        List<Long> toClaim = new ArrayList<>(ledger.openClaimsOf(uuid).stream().map(RewardClaimLedger.Entry::rewardId).toList());
        List<PendingReward> fresh = found.rewards().stream().filter(r -> !toClaim.contains(r.id()) && eligible.test(r)).toList();

        Map<Long, Planned> planned = new HashMap<>();
        RewardPlanner.Plan plan = RewardPlanner.plan(fresh, entry -> delivery.whyUndeliverable(entry) == null,
                new InventoryPlanRoom(player, reward -> planned.computeIfAbsent(reward.id(),
                        id -> new Planned(reward, delivery.prepare(player, reward))).prepared()));
        logUndeliverable(uuid, plan.undeliverable());
        plan.tooLarge().forEach(reward -> Arffornia.LOGGER.warn(
                "Reward {} for {} stays pending: it needs more than the {} inventory slots", reward.id(), uuid, InventoryRoom.MAIN_SLOTS));
        plan.toClaim().forEach(r -> toClaim.add(r.id()));

        if (toClaim.isEmpty()) {
            return CompletableFuture.completedFuture(new ClaimSummary(plan, List.of(), 0));
        }
        List<Integer> keys = new ArrayList<>();
        return claimSequentially(uuid, toClaim, 0, new ArrayList<>(), planned, keys)
                .thenApply(outcomes -> new ClaimSummary(plan, outcomes, keys.stream().mapToInt(Integer::intValue).sum()));
    }

    /** A reward prepared (rolled) for the plan, so the claimed reward gives what was checked to fit. */
    private record Planned(PendingReward reward, RewardDelivery.Prepared prepared) {
    }

    /** The player's inventory, filled reward by reward with the prepared stacks. */
    private static final class InventoryPlanRoom implements RewardPlanner.Room {
        private final Function<PendingReward, RewardDelivery.Prepared> prepare;
        private List<ItemStack> slots;

        InventoryPlanRoom(ServerPlayer player, Function<PendingReward, RewardDelivery.Prepared> prepare) {
            this.prepare = prepare;
            this.slots = InventoryRoom.snapshot(player.getInventory());
        }

        @Override
        public boolean exceedsEmptyInventory(PendingReward reward) {
            return InventoryRoom.insert(InventoryRoom.empty(), prepare.apply(reward).stacks()) > 0;
        }

        @Override
        public int reserve(PendingReward reward) {
            List<ItemStack> trial = InventoryRoom.copy(slots);
            int missing = InventoryRoom.insert(trial, prepare.apply(reward).stacks());
            if (missing == 0) {
                slots = trial;
            }
            return missing;
        }
    }

    private void logUndeliverable(UUID uuid, List<PendingReward> rewards) {
        for (PendingReward reward : rewards) {
            List<String> reasons = reward.contents().stream().map(delivery::whyUndeliverable).filter(r -> r != null).distinct().toList();
            Arffornia.LOGGER.warn("Reward {} for {} stays pending: {}", reward.id(), uuid, reasons.isEmpty() ? "no contents" : reasons);
        }
    }

    /** Runs on the server thread between claims, so the ledger is only touched there. */
    private CompletableFuture<List<Boolean>> claimSequentially(UUID uuid, List<Long> rewardIds, int index, List<Boolean> outcomes,
                                                               Map<Long, Planned> planned, List<Integer> keys) {
        if (index >= rewardIds.size()) {
            return CompletableFuture.completedFuture(outcomes);
        }
        long rewardId = rewardIds.get(index);
        String key = RewardClaimLedger.get(server).keyFor(rewardId, uuid);
        return client.claim(rewardId, uuid, key)
                .thenApplyAsync(result -> settle(uuid, rewardId, result, planned.get(rewardId), keys), server)
                .thenCompose(ok -> {
                    outcomes.add(ok);
                    return claimSequentially(uuid, rewardIds, index + 1, outcomes, planned, keys);
                });
    }

    private boolean settle(UUID uuid, long rewardId, RewardsClient.ClaimResult result, @Nullable Planned planned, List<Integer> keys) {
        RewardClaimLedger ledger = RewardClaimLedger.get(server);
        switch (result) {
            case RewardsClient.ClaimResult.Claimed claimed -> {
                ServerPlayer player = deliverableTarget(uuid);
                if (player == null) {
                    Arffornia.LOGGER.info("Reward {} claimed while {} is away; it will be delivered on their next claim", rewardId, uuid);
                    return false;
                }
                ledger.close(rewardId);
                try {
                    PendingReward reward = claimed.reward();
                    RewardDelivery.Prepared prepared = planned != null && planned.reward().contents().equals(reward.contents())
                            ? planned.prepared() : delivery.prepare(player, reward);
                    deliverClaimed(player, reward, prepared);
                    keys.add(reward.contents().stream().mapToInt(e -> e instanceof RewardEntry.Key k ? k.count() : 0).sum());
                } catch (RuntimeException e) {
                    Arffornia.LOGGER.error("Reward {} for {} was claimed but delivery failed; redo it by hand: {}",
                            rewardId, player.getName().getString(), claimed.reward().contents(), e);
                    return false;
                }
                return true;
            }
            case RewardsClient.ClaimResult.Rejected rejected -> {
                ledger.close(rewardId);
                return false;
            }
            case RewardsClient.ClaimResult.Failed failed -> {
                return false;
            }
        }
    }

    @Nullable
    private ServerPlayer deliverableTarget(UUID uuid) {
        ServerPlayer player = onlinePlayer.apply(uuid);
        return player != null && player.isAlive() && !player.hasDisconnected() ? player : null;
    }

    private void deliverClaimed(ServerPlayer player, PendingReward reward, RewardDelivery.Prepared prepared) {
        List<String> problems = reward.contents().stream().map(delivery::whyUndeliverable).filter(r -> r != null).toList();
        if (!problems.isEmpty()) {
            Arffornia.LOGGER.error("Reward {} for {} was claimed but some entries cannot be delivered: {}", reward.id(), player.getName().getString(), problems);
        }
        delivery.deliver(player, reward, prepared);
    }

    private void report(UUID uuid, ClaimSummary summary, int stashMissing) {
        ServerPlayer player = deliverableTarget(uuid);
        if (player == null) {
            return;
        }
        RewardPlanner.Plan plan = summary.plan();
        long delivered = summary.outcomes().stream().filter(ok -> ok).count();
        boolean anyFailed = delivered < summary.outcomes().size();
        int slotsToFree = Math.max(plan.slotsToFree(), stashMissing);

        if (summary.outcomes().isEmpty() && plan.undeliverable().isEmpty() && plan.tooLarge().isEmpty() && slotsToFree == 0) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.none").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (anyFailed) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.error").withStyle(ChatFormatting.RED));
        }
        if (!plan.undeliverable().isEmpty()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.unsupported").withStyle(ChatFormatting.YELLOW));
        }
        if (!plan.tooLarge().isEmpty()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.too_large").withStyle(ChatFormatting.YELLOW));
        }
        if (summary.keysDelivered() > 0) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.keys_delivered", summary.keysDelivered()).withStyle(ChatFormatting.GREEN));
        }
        if (slotsToFree > 0) {
            if (delivered > 0) {
                player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.partial").withStyle(ChatFormatting.GREEN));
            }
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.free_slots", slotsToFree, claimCommand()).withStyle(ChatFormatting.RED));
        } else if (delivered > 0 && !anyFailed) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.all_claimed").withStyle(ChatFormatting.GREEN));
        }
    }

    private void sendTo(UUID uuid, Component message) {
        server.execute(() -> {
            ServerPlayer player = onlinePlayer.apply(uuid);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        });
    }
}
