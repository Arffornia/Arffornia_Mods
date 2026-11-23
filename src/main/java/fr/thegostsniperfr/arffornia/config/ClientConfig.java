package fr.thegostsniperfr.arffornia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code arffornia-client.toml}: player-side choices, never synced from the server. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue WEB_FPS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("progression");
        WEB_FPS = builder
                .comment("Frame rate of the embedded page while the screen is open; it drops to 1 when closed.")
                .defineInRange("webFps", 30, 1, 60);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
