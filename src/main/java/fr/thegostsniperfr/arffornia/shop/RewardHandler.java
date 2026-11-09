package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Delivers website rewards: list the player's pending rewards, claim each one with a key recorded in
 * {@link RewardClaimLedger}, then run the fixed action of each typed entry. A reward is delivered only after its claim
 * succeeded. A claim whose outcome is unknown, or whose player left, is claimed again with the same key on the next
 * attempt; the website answers with a replay.
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

    /** Records the daily connection (streak), then tells the player about rewards waiting for them. */
    public void onPlayerJoined(ServerPlayer player) {
        UUID uuid = player.getUUID();
        client.recordDailyConnection(uuid)
                .thenCompose(streak -> {
                    if (streak != null && streak.firstConnectionToday()) {
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.streak", streak.dayStreak()).withStyle(ChatFormatting.GOLD));
                    }
                    return client.fetchPending(uuid);
                })
                .thenAcceptAsync(result -> {
                    boolean hasRewards = result instanceof RewardsClient.PendingResult.Found found && !found.rewards().isEmpty();
                    if (hasRewards || !RewardClaimLedger.get(server).openClaimsOf(uuid).isEmpty()) {
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.pending").withStyle(ChatFormatting.GREEN));
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.claim_hint", Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.YELLOW));
                    }
                }, server);
    }

    /** @return the claim flow, completed when every claim of this attempt has an outcome */
    public CompletableFuture<Void> claimRewardsForPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!claimsInFlight.add(uuid)) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.busy").withStyle(ChatFormatting.YELLOW));
            return CompletableFuture.completedFuture(null);
        }

        return client.fetchPending(uuid)
                .thenComposeAsync(result -> planAndClaim(uuid, result), server)
                .whenComplete((ignored, error) -> {
                    claimsInFlight.remove(uuid);
                    if (error != null) {
                        Arffornia.LOGGER.error("Reward claim for {} failed", uuid, error);
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.error").withStyle(ChatFormatting.RED));
                    }
                });
    }

    private CompletableFuture<Void> planAndClaim(UUID uuid, RewardsClient.PendingResult result) {
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
                RewardClaimLedger ledger = RewardClaimLedger.get(server);
                List<Long> toClaim = new ArrayList<>(ledger.openClaimsOf(uuid).stream().map(RewardClaimLedger.Entry::rewardId).toList());
                List<PendingReward> fresh = found.rewards().stream().filter(r -> !toClaim.contains(r.id())).toList();

                RewardPlanner.Plan plan = RewardPlanner.plan(fresh, RewardDelivery.freeSlots(player),
                        entry -> delivery.whyUndeliverable(entry) == null, delivery::slotsNeeded);
                logUndeliverable(uuid, plan.undeliverable());
                plan.toClaim().forEach(r -> toClaim.add(r.id()));

                if (toClaim.isEmpty()) {
                    report(uuid, plan, List.of());
                    return CompletableFuture.completedFuture(null);
                }
                return claimSequentially(uuid, toClaim, 0, new ArrayList<>())
                        .thenAcceptAsync(outcomes -> report(uuid, plan, outcomes), server);
            }
        }
    }

    private void logUndeliverable(UUID uuid, List<PendingReward> rewards) {
        for (PendingReward reward : rewards) {
            List<String> reasons = reward.contents().stream().map(delivery::whyUndeliverable).filter(r -> r != null).distinct().toList();
            Arffornia.LOGGER.warn("Reward {} for {} stays pending: {}", reward.id(), uuid, reasons.isEmpty() ? "no contents" : reasons);
        }
    }

    /** Runs on the server thread between claims, so the ledger is only touched there. */
    private CompletableFuture<List<Boolean>> claimSequentially(UUID uuid, List<Long> rewardIds, int index, List<Boolean> outcomes) {
        if (index >= rewardIds.size()) {
            return CompletableFuture.completedFuture(outcomes);
        }
        long rewardId = rewardIds.get(index);
        String key = RewardClaimLedger.get(server).keyFor(rewardId, uuid);
        return client.claim(rewardId, uuid, key)
                .thenApplyAsync(result -> settle(uuid, rewardId, result), server)
                .thenCompose(ok -> {
                    outcomes.add(ok);
                    return claimSequentially(uuid, rewardIds, index + 1, outcomes);
                });
    }

    private boolean settle(UUID uuid, long rewardId, RewardsClient.ClaimResult result) {
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
                    deliverClaimed(player, claimed.reward());
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

    private void deliverClaimed(ServerPlayer player, PendingReward reward) {
        List<String> problems = reward.contents().stream().map(delivery::whyUndeliverable).filter(r -> r != null).toList();
        if (!problems.isEmpty()) {
            Arffornia.LOGGER.error("Reward {} for {} was claimed but some entries cannot be delivered: {}", reward.id(), player.getName().getString(), problems);
        }
        delivery.deliver(player, reward);
    }

    private void report(UUID uuid, RewardPlanner.Plan plan, List<Boolean> outcomes) {
        ServerPlayer player = deliverableTarget(uuid);
        if (player == null) {
            return;
        }
        long delivered = outcomes.stream().filter(ok -> ok).count();
        boolean anyFailed = delivered < outcomes.size();

        if (outcomes.isEmpty() && plan.undeliverable().isEmpty() && !plan.leftForLater()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.none").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (anyFailed) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.error").withStyle(ChatFormatting.RED));
        }
        if (!plan.undeliverable().isEmpty()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.unsupported").withStyle(ChatFormatting.YELLOW));
        }
        if (plan.leftForLater()) {
            if (delivered > 0) {
                player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.partial").withStyle(ChatFormatting.GREEN));
                player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.partial_hint", Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.YELLOW));
            } else {
                player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.inventory_full").withStyle(ChatFormatting.RED));
            }
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
