package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.shop.internal.DatabaseManager;
import fr.thegostsniperfr.arffornia.shop.internal.PendingReward;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles the core logic of checking for and delivering rewards to players.
 */
public class RewardHandler {
    private final DatabaseManager dbManager;
    private final MinecraftServer server;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<UUID, Integer> userIdCache = new ConcurrentHashMap<>();

    public RewardHandler(DatabaseManager dbManager, MinecraftServer server) {
        this.dbManager = dbManager;
        this.server = server;
    }

    /**
     * Fetches a player's user ID from the database and adds it to the cache.
     * This is typically called when a player joins the server.
     * The operation is submitted to an executor to avoid blocking the main server thread.
     *
     * @param player The player to add.
     */
    public void addPlayerToCache(ServerPlayer player) {
        executor.submit(() -> {
            try {
                int userId = dbManager.getUserId(player.getUUID());

                if (userId != -1) {
                    userIdCache.put(player.getUUID(), userId);
                    Arffornia.LOGGER.info("Cached user ID {} for player {}", userId, player.getName().getString());
                } else {
                    Arffornia.LOGGER.error("Player {} not found in web database, could not cache user ID.", player.getName().getString());
                }
            } catch (Exception e) {
                Arffornia.LOGGER.error("Failed to cache user ID for player {}", player.getName().getString(), e);
            }
        });
    }

    /**
     * Removes a player from the user ID cache.
     * This is typically called when a player leaves the server to free up memory.
     *
     * @param player The player to remove.
     */
    public void removePlayerFromCache(ServerPlayer player) {
        userIdCache.remove(player.getUUID());
        Arffornia.LOGGER.info("Removed player {} from user ID cache.", player.getName().getString());
    }

    /**
     * Checks if a player has any pending rewards.
     *
     * @param player The player to check.
     * @return A CompletableFuture that resolves to true if rewards are pending.
     */
    public CompletableFuture<Boolean> hasPendingRewards(ServerPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            Integer userId = userIdCache.get(player.getUUID());
            if (userId == null) {
                userId = dbManager.getUserId(player.getUUID());
                if (userId != -1) userIdCache.put(player.getUUID(), userId);
            }

            return userId != -1 && dbManager.hasPendingRewards(userId);
        }, executor);
    }

    /**
     * Processes pending rewards for a player, filling their inventory with as many items as possible.
     * This performs the entire claim process transactionally and handles partial claims gracefully.
     *
     * @param player The player who is claiming their rewards.
     */
    public void claimRewardsForPlayer(ServerPlayer player) {
        executor.submit(() -> {
            Integer userId = userIdCache.get(player.getUUID());
            if (userId == null || userId == -1) {
                player.sendSystemMessage(Component.literal("§cYour account is not linked to the web database."));
                return;
            }

            try (Connection conn = dbManager.getConnection()) {
                conn.setAutoCommit(false);

                List<PendingReward> allPendingRewards = dbManager.fetchAndLockAllRewardsForUser(userId, conn);

                if (allPendingRewards.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§eYou have no pending rewards to claim."));
                    conn.rollback();
                    return;
                }

                int availableSlots = countEmptySlots(player);
                List<Integer> claimedRewardIds = new ArrayList<>();
                List<Runnable> commandsToExecute = new ArrayList<>();
                boolean rewardsLeftOver = false;

                for (PendingReward reward : allPendingRewards) {
                    int slotsNeeded = countItemsToGive(reward.commands());

                    if (slotsNeeded <= availableSlots) {
                        availableSlots -= slotsNeeded;
                        claimedRewardIds.add(reward.id());

                        commandsToExecute.add(() -> {
                            reward.commands().forEach(command -> {
                                String finalCommand = command.replace("{player}", player.getName().getString());
                                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), finalCommand);
                            });
                        });
                    } else if (slotsNeeded > 0) {
                        rewardsLeftOver = true;
                        break;
                    }
                }

                if (claimedRewardIds.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§cYour inventory is full. Please make space to claim your items."));
                    conn.rollback();
                    return;
                }

                server.execute(() -> commandsToExecute.forEach(Runnable::run));
                dbManager.updateMultipleRewardStatuses(claimedRewardIds, "claimed", conn);
                conn.commit();

                Arffornia.LOGGER.info("Successfully claimed {} rewards for player {}", claimedRewardIds.size(), player.getName().getString());

                if (rewardsLeftOver) {
                    player.sendSystemMessage(Component.literal("§aYou have claimed some of your rewards!"));
                    player.sendSystemMessage(Component.literal("§eYour inventory is now full. Make more space and type §b/claim_reward §eagain."));
                } else {
                    player.sendSystemMessage(Component.literal("§aAll your pending rewards have been claimed successfully!"));
                }

            } catch (SQLException e) {
                Arffornia.LOGGER.error("Database transaction failed for player {}", player.getName().getString(), e);
                player.sendSystemMessage(Component.literal("§cAn error occurred while claiming your rewards. Please try again later."));
            }
        });
    }

    /**
     * Counts the number of empty slots in a player's main inventory.
     * This excludes armor and off-hand slots.
     *
     * @param player The player to check.
     * @return The number of empty slots.
     */
    private int countEmptySlots(ServerPlayer player) {
        int emptySlots = 0;
        // The main inventory has 36 slots (indices 0-35).
        // getContainerSize() includes armor and offhand, so we iterate up to 36.
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    /**
     * Counts how many items are being given by a list of commands.
     * This is a simple approximation that assumes one "give" command requires one inventory slot.
     *
     * @param commands The list of commands for a single reward.
     * @return The number of slots estimated to be required.
     */
    private int countItemsToGive(List<String> commands) {
        return (int) commands.stream().filter(cmd -> cmd.toLowerCase().startsWith("give ")).count();
    }

    /**
     * Shuts down the executor service when the mod is stopping.
     */
    public void shutdown() {
        executor.shutdown();
    }
}