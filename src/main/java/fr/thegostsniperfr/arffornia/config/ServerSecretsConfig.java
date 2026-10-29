package fr.thegostsniperfr.arffornia.config;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-only configuration: service-account credentials, website database access and the recipe migration flag.
 * <p>
 * Registered only on a dedicated server ({@link #shouldRegister(Dist)}), as a COMMON-type file, so it is never
 * created on, written to or synced to game clients. Do not move these values to a SERVER-type config: those are
 * sent to clients when they join.
 * <p>
 * On a client (including singleplayer) the spec is never loaded and every accessor returns its empty/default value,
 * so features that need these values stay disabled instead of crashing.
 */
public final class ServerSecretsConfig {
    public static final String FILE_NAME = "arffornia-server.toml";

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<String> API_CLIENT_ID;
    private static final ModConfigSpec.ConfigValue<String> API_CLIENT_SECRET;
    private static final ModConfigSpec.BooleanValue MIGRATE_ON_STARTUP;

    private static final ModConfigSpec.ConfigValue<String> DB_HOST;
    private static final ModConfigSpec.ConfigValue<Integer> DB_PORT;
    private static final ModConfigSpec.ConfigValue<String> DB_DATABASE;
    private static final ModConfigSpec.ConfigValue<String> DB_USERNAME;
    private static final ModConfigSpec.ConfigValue<String> DB_PASSWORD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Arffornia API service account (dedicated server only)").push("api");
        API_CLIENT_ID = builder
                .comment("The service account Client ID for API authentication.")
                .define("clientId", "");
        API_CLIENT_SECRET = builder
                .comment("The service account Client Secret for API authentication. Keep this file private.")
                .define("clientSecret", "");
        MIGRATE_ON_STARTUP = builder
                .comment("If true, the mod will attempt to migrate vanilla recipes for existing items on the next server startup. This should be manually set to false after a successful run.")
                .define("migrateOnStartup", false);
        builder.pop();

        builder.comment("Database configuration for the Arffornia web shop integration (dedicated server only)").push("database");
        DB_HOST = builder.define("host", "127.0.0.1");
        DB_PORT = builder.defineInRange("port", 5432, 1, 65535);
        DB_DATABASE = builder.define("database", "arffornia");
        DB_USERNAME = builder.define("username", "");
        DB_PASSWORD = builder.define("password", "");
        builder.pop();

        SPEC = builder.build();
    }

    private ServerSecretsConfig() {
    }

    /** Whether the server-only config is registered on this side. */
    public static boolean shouldRegister(Dist dist) {
        return dist == Dist.DEDICATED_SERVER;
    }

    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    public static String apiClientId() {
        return isLoaded() ? API_CLIENT_ID.get() : "";
    }

    public static String apiClientSecret() {
        return isLoaded() ? API_CLIENT_SECRET.get() : "";
    }

    public static boolean migrateOnStartup() {
        return isLoaded() && MIGRATE_ON_STARTUP.get();
    }

    /** True when both service-account values are set. */
    public static boolean hasServiceCredentials() {
        return !apiClientId().isBlank() && !apiClientSecret().isBlank();
    }

    public static String dbHost() {
        return isLoaded() ? DB_HOST.get() : "";
    }

    public static int dbPort() {
        return isLoaded() ? DB_PORT.get() : 0;
    }

    public static String dbDatabase() {
        return isLoaded() ? DB_DATABASE.get() : "";
    }

    public static String dbUsername() {
        return isLoaded() ? DB_USERNAME.get() : "";
    }

    public static String dbPassword() {
        return isLoaded() ? DB_PASSWORD.get() : "";
    }

    /** True when the database connection can be attempted (server-only config loaded with a host and user). */
    public static boolean hasDatabaseSettings() {
        return !dbHost().isBlank() && !dbUsername().isBlank();
    }
}
