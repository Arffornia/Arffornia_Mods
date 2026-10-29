package fr.thegostsniperfr.arffornia.config;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-only configuration: service-account credentials.
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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Arffornia API service account (dedicated server only)").push("api");
        API_CLIENT_ID = builder
                .comment("The service account Client ID for API authentication.")
                .define("clientId", "");
        API_CLIENT_SECRET = builder
                .comment("The service account Client Secret for API authentication. Keep this file private.")
                .define("clientSecret", "");
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

    /** True when both service-account values are set. */
    public static boolean hasServiceCredentials() {
        return !apiClientId().isBlank() && !apiClientSecret().isBlank();
    }
}
