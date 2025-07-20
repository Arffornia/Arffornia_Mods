package fr.thegostsniperfr.arffornia.shop;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Handles all shop-related configurations. This is registered within the main Config class.
 */
public class ShopConfig {
    public static ModConfigSpec.ConfigValue<String> DB_HOST;
    public static ModConfigSpec.ConfigValue<Integer> DB_PORT;
    public static ModConfigSpec.ConfigValue<String> DB_DATABASE;
    public static ModConfigSpec.ConfigValue<String> DB_USERNAME;
    public static ModConfigSpec.ConfigValue<String> DB_PASSWORD;

    public static ModConfigSpec.IntValue REWARD_CHECK_INTERVAL;

    public static void register(ModConfigSpec.Builder builder) {
        builder.comment("Database configuration for the Arffornia web shop integration").push("database");

        DB_HOST = builder.define("host", "127.0.0.1");
        DB_PORT = builder.define("port", 5432);
        DB_DATABASE = builder.define("database", "arffornia");
        DB_USERNAME = builder.define("username", "laravel");
        DB_PASSWORD = builder.define("password", "laravel");

        builder.pop();

        builder.comment("Configuration du système de récompenses").push("rewards");

        REWARD_CHECK_INTERVAL = builder
                .comment("Interval (in seconds) at which the server checks for pending rewards for online players.")
                .defineInRange("checkIntervalSeconds", 30, 5, 300); // Default: 30s, Min: 5s, Max: 5min

        builder.pop();
    }
}