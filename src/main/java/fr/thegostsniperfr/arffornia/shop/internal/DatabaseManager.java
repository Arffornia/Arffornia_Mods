package fr.thegostsniperfr.arffornia.shop.internal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.thegostsniperfr.arffornia.shop.ShopConfig;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static fr.thegostsniperfr.arffornia.Arffornia.LOGGER;

public class DatabaseManager {
    private final HikariDataSource dataSource;
    private final Gson gson = new Gson();

    public DatabaseManager() {
        HikariDataSource tempDataSource;
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://" + ShopConfig.DB_HOST.get() + ":" + ShopConfig.DB_PORT.get() + "/" + ShopConfig.DB_DATABASE.get());
            config.setUsername(ShopConfig.DB_USERNAME.get());
            config.setPassword(ShopConfig.DB_PASSWORD.get());
            config.setMaximumPoolSize(5);

            tempDataSource = new HikariDataSource(config);
            LOGGER.info("Database connection pool initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("!!! FAILED TO INITIALIZE DATABASE CONNECTION POOL !!!");
            LOGGER.error("The shop and reward system will be disabled. Please check your database credentials and connectivity.");
            LOGGER.error("Connection error: {}", e.getMessage());
            tempDataSource = null;
        }
        this.dataSource = tempDataSource;
    }

    /**
     * Gets a connection from the pool.
     *
     * @return A database connection.
     * @throws SQLException if a connection cannot be established or the pool is unavailable.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database connection is not available.");
        }

        return dataSource.getConnection();
    }

    /**
     * Retrieves the internal user ID from the 'users' table based on a player's Minecraft UUID.
     *
     * @param playerUuid The player's UUID.
     * @return The user ID, or -1 if not found or if DB is down.
     */
    public int getUserId(UUID playerUuid) {
        if (dataSource == null) return -1;

        LOGGER.info("getUserId Player uuid: {}", playerUuid);

        final String sql = "SELECT id FROM users WHERE uuid = ? LIMIT 1;";

        try (Connection conn = getConnection(); PreparedStatement req = conn.prepareStatement(sql)) {
            req.setString(1, playerUuid.toString().replace("-", ""));
            ResultSet res = req.executeQuery();

            if (res.next()) {
                LOGGER.info("getUserId Player uuid after req: {}", res.getInt("id"));

                return res.getInt("id");
            }
        } catch (SQLException e) {
            LOGGER.error("Could not fetch user ID for UUID {}", playerUuid, e);
        }

        return -1;
    }


    /**
     * Checks if a user has any pending rewards without fetching them.
     *
     * @param userId The user's ID.
     * @return true if there is at least one pending reward, false otherwise or if DB is down.
     */
    public boolean hasPendingRewards(int userId) {
        if (dataSource == null) return false;

        final String sql = "SELECT 1 FROM pending_rewards WHERE user_id = ? AND status = 'pending' LIMIT 1;";

        try (Connection conn = getConnection(); PreparedStatement req = conn.prepareStatement(sql)) {
            req.setInt(1, userId);
            try (ResultSet res = req.executeQuery()) {
                return res.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Could not check for pending rewards for user ID {}", userId, e);
            return false;
        }
    }

    /**
     * Fetches and locks all pending rewards for a single user within a transaction.
     *
     * @param userId     The user's ID.
     * @param connection The transactional connection to use.
     * @return A List of all PendingReward objects for that user.
     * @throws SQLException if a database error occurs.
     */
    public List<PendingReward> fetchAndLockAllRewardsForUser(int userId, Connection connection) throws SQLException {
        final String sql = "SELECT id, user_id, commands FROM pending_rewards WHERE user_id = ? AND status = 'pending' ORDER BY created_at ASC FOR UPDATE SKIP LOCKED;";
        List<PendingReward> rewards = new ArrayList<>();

        try (PreparedStatement req = connection.prepareStatement(sql)) {
            req.setInt(1, userId);
            ResultSet res = req.executeQuery();

            while (res.next()) {
                int rewardId = res.getInt("id");
                String commandsJson = res.getString("commands");
                Type listType = new TypeToken<ArrayList<String>>() {
                }.getType();
                List<String> commands = gson.fromJson(commandsJson, listType);

                rewards.add(new PendingReward(rewardId, userId, commands));
            }
        }

        return rewards;
    }

    /**
     * Updates the status of multiple rewards in a single, efficient query.
     *
     * @param rewardIds  A list of reward IDs to update.
     * @param status     The new status (e.g., "claimed").
     * @param connection The transactional connection to use.
     * @throws SQLException if a database error occurs.
     */
    public void updateMultipleRewardStatuses(List<Integer> rewardIds, String status, Connection connection) throws SQLException {
        if (rewardIds.isEmpty()) {
            return;
        }

        String inSql = String.join(",", Collections.nCopies(rewardIds.size(), "?"));
        final String sql = String.format("UPDATE pending_rewards SET status = ? WHERE id IN (%s);", inSql);

        try (PreparedStatement req = connection.prepareStatement(sql)) {
            req.setString(1, status);
            int i = 2;
            for (Integer rewardId : rewardIds) {
                req.setInt(i++, rewardId);
            }

            req.executeUpdate();
        }
    }

    /**
     * Updates the status of a reward to 'claimed' or 'failed'.
     *
     * @param rewardId   The ID of the reward to update.
     * @param status     The new status.
     * @param connection The transactional connection to use.
     * @throws SQLException if a database error occurs.
     */
    public void updateRewardStatus(int rewardId, String status, Connection connection) throws SQLException {
        final String sql = "UPDATE pending_rewards SET status = ? WHERE id = ?;";
        try (PreparedStatement req = connection.prepareStatement(sql)) {
            req.setString(1, status);
            req.setInt(2, rewardId);
            req.executeUpdate();
        }
    }

    /**
     * Closes the connection pool. Called when the server shuts down.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Database connection pool closed.");
        }
    }
}