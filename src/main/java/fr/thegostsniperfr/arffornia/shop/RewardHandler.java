package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.shop.internal.DatabaseManager;
import fr.thegostsniperfr.arffornia.shop.internal.PendingReward;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles the core logic of checking for and delivering rewards to players.
 */
public class RewardHandler {
    private final DatabaseManager dbManager;
    private final MinecraftServer server;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RewardHandler(DatabaseManager dbManager, MinecraftServer server) {
        this.dbManager = dbManager;
        this.server = server;
    }

    /**
     * Asynchronously processes all pending rewards for a specific player.
     * @param player The player to check rewards for.
     */
    public void processRewardsForPlayer(ServerPlayer player) {
        executor.submit(() -> {
            try {
                int userId = dbManager.getUserId(player.getUUID());
                if (userId == -1) {
                    Arffornia.LOGGER.info("Player {} not found in web database, skipping reward check.", player.getName().getString());
                    return;
                }

                while (processSingleReward(player, userId));

            } catch (Exception e) {
                Arffornia.LOGGER.error("An unexpected error occurred while processing rewards for player {}", player.getName().getString(), e);
            }
        });
    }

    /**
     * Attempts to process a single pending reward in a safe, transactional manner.
     * @return true if a reward was processed (or attempted), false if none were found.
     */
    private boolean processSingleReward(ServerPlayer player, int userId) {
        Arffornia.LOGGER.info("Processing reward for player:{}, with uuid: {} and userId: {}", player.getName(), player.getUUID(),  userId);
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            PendingReward reward = dbManager.fetchAndLockNextReward(userId, conn);

            if (reward == null) {
                Arffornia.LOGGER.info("No reward found for player:{}, with uuid: {}", player.getName(), player.getUUID());
                conn.rollback(); // Nothing to do, just close the transaction
                return false; // No more rewards for this player at the moment
            }

            if (hasEnoughInventorySpace(player, reward.commands())) {
                try {
                    // Schedule command execution on the main server thread
                    server.execute(() -> {
                        reward.commands().forEach(command -> {
                            // Replace {player} placeholder with the player's current name
                            String finalCommand = command.replace("{player}", player.getName().getString());
                            Arffornia.LOGGER.info("After exec replacement: {}", finalCommand);
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), finalCommand);
                        });
                    });

                    dbManager.updateRewardStatus(reward.id(), "claimed", conn);
                    conn.commit(); // Commit transaction on success

                    Arffornia.LOGGER.info("Successfully executed reward for player:{}", player.getName());

                    player.sendSystemMessage(Component.literal("§aYou have received your items from the shop!"));
                } catch (Exception e) {
                    Arffornia.LOGGER.error("Error executing reward commands for reward ID {}", reward.id(), e);
                    dbManager.updateRewardStatus(reward.id(), "failed", conn);
                    conn.commit(); // Commit even on failure to prevent retrying a broken command
                }
            } else {
                player.sendSystemMessage(Component.literal("§cYour inventory is full. Please make space to receive your pending items."));
                conn.rollback(); // Rollback to release the lock, will retry later
            }

            return true; // A reward was handled, check for more
        } catch (SQLException e) {
            Arffornia.LOGGER.error("Database transaction failed for player {}", player.getName().getString(), e);
            return false;
        }
    }

    /**
     * A simple check for inventory space. Counts 'give' commands and checks for empty slots.
     * This can be improved later to be more precise if needed.
     * @return true if there is likely enough space, false otherwise.
     */
    private boolean hasEnoughInventorySpace(ServerPlayer player, List<String> commands) {
        long itemsToGive = commands.stream().filter(cmd -> cmd.toLowerCase().startsWith("give")).count();
        if (itemsToGive == 0) return true;

        int emptySlots = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }

        return emptySlots >= itemsToGive;
    }

    /**
     * Shuts down the executor service when the mod is stopping.
     */
    public void shutdown() {
        executor.shutdown();
    }
}