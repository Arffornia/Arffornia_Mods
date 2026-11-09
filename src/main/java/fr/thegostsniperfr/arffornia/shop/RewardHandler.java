package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers website rewards: list the player's pending rewards, claim each one (idempotent key), then run the fixed
 * action of each typed entry. A reward is delivered only after its claim succeeded, so it is never delivered twice.
 */
public class RewardHandler {
    private final RewardsClient client;
    private final MinecraftServer server;
    private final RewardDelivery delivery;

    private final Set<UUID> claimsInFlight = ConcurrentHashMap.newKeySet();
    /** Reused across attempts so that a claim whose response was lost is answered as a replay, not a conflict. */
    private final Map<Long, String> claimKeys = new ConcurrentHashMap<>();
    /** Claimed while the player was offline; delivered on their next claim. Lost on restart. */
    private final Map<UUID, List<PendingReward>> claimedNotDelivered = new ConcurrentHashMap<>();

    public RewardHandler(RewardsClient client, MinecraftServer server) {
        this.client = client;
        this.server = server;
        this.delivery = new RewardDelivery(server);
    }

    /** Records the daily connection (streak), then tells the player about pending rewards. */
    public void onPlayerJoined(ServerPlayer player) {
        UUID uuid = player.getUUID();
        client.recordDailyConnection(uuid)
                .thenCompose(streak -> {
                    if (streak != null && streak.firstConnectionToday()) {
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.streak", streak.dayStreak()).withStyle(ChatFormatting.GOLD));
                    }
                    return client.fetchPending(uuid);
                })
                .thenAccept(result -> {
                    boolean hasRewards = result instanceof RewardsClient.PendingResult.Found found && !found.rewards().isEmpty();
                    if (hasRewards || claimedNotDelivered.containsKey(uuid)) {
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.pending").withStyle(ChatFormatting.GREEN));
                        sendTo(uuid, Component.translatable("chat.arffornia.rewards.claim_hint", Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.YELLOW));
                    }
                });
    }

    public void claimRewardsForPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!claimsInFlight.add(uuid)) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }

        client.fetchPending(uuid)
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
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<PendingReward> carriedOver = claimedNotDelivered.remove(uuid);
        if (carriedOver != null) {
            carriedOver.forEach(reward -> deliverClaimed(player, reward));
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
                if (found.rewards().isEmpty()) {
                    if (carriedOver == null) {
                        player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.none").withStyle(ChatFormatting.YELLOW));
                    }
                    return CompletableFuture.completedFuture(null);
                }
                RewardPlanner.Plan plan = RewardPlanner.plan(found.rewards(), RewardDelivery.freeSlots(player),
                        entry -> delivery.whyUndeliverable(entry) == null, delivery::slotsNeeded);
                logUndeliverable(uuid, plan.undeliverable());
                return claimSequentially(uuid, plan.toClaim(), 0, new ArrayList<>())
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

    private CompletableFuture<List<Boolean>> claimSequentially(UUID uuid, List<PendingReward> rewards, int index, List<Boolean> outcomes) {
        if (index >= rewards.size()) {
            return CompletableFuture.completedFuture(outcomes);
        }
        PendingReward reward = rewards.get(index);
        String key = claimKeys.computeIfAbsent(reward.id(), id -> UUID.randomUUID().toString());
        return client.claim(reward.id(), uuid, key)
                .thenApplyAsync(result -> {
                    if (result instanceof RewardsClient.ClaimResult.Claimed claimed) {
                        claimKeys.remove(reward.id());
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null) {
                            deliverClaimed(player, claimed.reward());
                        } else {
                            claimedNotDelivered.computeIfAbsent(uuid, u -> new ArrayList<>()).add(claimed.reward());
                        }
                        return true;
                    }
                    if (result instanceof RewardsClient.ClaimResult.Rejected) {
                        claimKeys.remove(reward.id());
                    }
                    return false;
                }, server)
                .thenCompose(ok -> {
                    outcomes.add(ok);
                    return claimSequentially(uuid, rewards, index + 1, outcomes);
                });
    }

    private void deliverClaimed(ServerPlayer player, PendingReward reward) {
        List<String> problems = reward.contents().stream().map(delivery::whyUndeliverable).filter(r -> r != null).toList();
        if (!problems.isEmpty()) {
            Arffornia.LOGGER.error("Reward {} for {} was claimed but some entries cannot be delivered: {}", reward.id(), player.getName().getString(), problems);
        }
        delivery.deliver(player, reward);
    }

    private void report(UUID uuid, RewardPlanner.Plan plan, List<Boolean> outcomes) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }
        long delivered = outcomes.stream().filter(ok -> ok).count();
        boolean anyFailed = delivered < outcomes.size();

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
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        });
    }
}
