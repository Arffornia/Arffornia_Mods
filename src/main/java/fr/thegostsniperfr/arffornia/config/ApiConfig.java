package fr.thegostsniperfr.arffornia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Non-sensitive API settings, shared by client and server (arffornia-common.toml).
 * Service-account credentials live in {@link ServerSecretsConfig}.
 */
public class ApiConfig {
    public static ModConfigSpec.ConfigValue<String> API_BASE_URL;

    public static void register(ModConfigSpec.Builder builder) {
        builder.comment("Configuration for the Arffornia API connection").push("api");

        API_BASE_URL = builder
                .comment("The base URL for the Arffornia API.")
                .define("baseUrl", "http://127.0.0.1:8000/api");

        builder.pop();
    }
}
