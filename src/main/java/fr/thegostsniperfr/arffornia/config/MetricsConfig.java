package fr.thegostsniperfr.arffornia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MetricsConfig {
    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.IntValue PORT;

    public static void register(ModConfigSpec.Builder builder) {
        builder.comment("Prometheus endpoint for the Arffornia counters (dedicated server only)").push("metrics");

        ENABLED = builder
                .comment("Serve /metrics. Keep the port inside the cluster: it has no authentication.")
                .define("enabled", true);

        PORT = builder
                .comment("TCP port of /metrics.")
                .defineInRange("port", 19566, 1024, 65535);

        builder.pop();
    }
}
