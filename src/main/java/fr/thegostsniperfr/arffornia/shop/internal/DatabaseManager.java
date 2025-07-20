package fr.thegostsniperfr.arffornia.shop.internal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.shop.ShopConfig;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.thegostsniperfr.arffornia.Arffornia.LOGGER;

/**
 * Manages all database interactions for the shop reward system.
 * Uses a HikariCP connection pool for efficiency and reliability.
 */
public class DatabaseManager {
    private final HikariDataSource dataSource;
    private final Gson gson = new Gson();

    public DatabaseManager() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + ShopConfig.DB_HOST.get() + ":" + ShopConfig.DB_PORT.get() + "/" + ShopConfig.DB_DATABASE.get());
        config.setUsername(ShopConfig.DB_USERNAME.get());
        config.setPassword(ShopConfig.DB_PASSWORD.get());
        config.setMaximumPoolSize(5);
        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Gets a connection from the pool.
     * @return A database connection.
     * @throws SQLException if a connection cannot be established.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Retrieves the internal user ID from the 'users' table based on a player's Minecraft UUID.
     * @param playerUuid The player's UUID.
     * @return The user ID, or -1 if not found.
     */
    public int getUserId(UUID playerUuid) {

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
     * Fetches and locks the next pending reward for a user within a transaction.
     * The `FOR UPDATE SKIP LOCKED` clause prevent race conditions in a multi-server setup.
     * It makes other servers skip this row if it's already being processed.
     * @param userId The user's ID.
     * @param connection The transactional connection to use.
     * @return A PendingReward object, or null if none are available.
     * @throws SQLException if a database error occurs.
     */
    public PendingReward fetchAndLockNextReward(int userId, Connection connection) throws SQLException {
        final String sql = "SELECT id, commands FROM pending_rewards WHERE user_id = ? AND status = 'pending' ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED;";

        try (PreparedStatement req = connection.prepareStatement(sql)) {
            req.setInt(1, userId);
            ResultSet res = req.executeQuery();

            if (res.next()) {
                int rewardId = res.getInt("id");
                String commandsJson = res.getString("commands");

                Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                List<String> commands = gson.fromJson(commandsJson, listType);

                return new PendingReward(rewardId, commands);
            }
        }

        return null;
    }

    /**
     * Updates the status of a reward to 'claimed' or 'failed'.
     * @param rewardId The ID of the reward to update.
     * @param status The new status.
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
        }
    }
}