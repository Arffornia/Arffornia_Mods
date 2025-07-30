package fr.thegostsniperfr.arffornia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ApiConfig {
    public static ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static ModConfigSpec.ConfigValue<String> API_SVC_ID;
    public static ModConfigSpec.ConfigValue<String> API_SVC_SECRET;

    public static void register(ModConfigSpec.Builder builder) {
        builder.comment("Configuration for the Arffornia API connection").push("api");

        API_BASE_URL = builder
                .comment("The base URL for the Arffornia API.")
                .define("baseUrl", "http://127.0.0.1:8000/api");

        API_SVC_ID = builder
                .comment("The service account ID for API authentication.")
                .define("serviceId", "minecraft-server-svc");

        API_SVC_SECRET = builder
                .comment("The service account secret for API authentication. This should be kept private.")
                .define("serviceSecret", "your-super-secret-key-here");

        builder.pop();
    }
}