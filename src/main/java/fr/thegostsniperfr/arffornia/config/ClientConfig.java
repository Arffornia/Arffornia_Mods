package fr.thegostsniperfr.arffornia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code arffornia-client.toml}: player-side choices, never synced from the server. */
public final class ClientConfig {
    public enum ProgressionScreen { WEB, NATIVE }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<ProgressionScreen> PROGRESSION_SCREEN;
    public static final ModConfigSpec.IntValue WEB_FPS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("progression");
        PROGRESSION_SCREEN = builder
                .comment("WEB: the website's progression viewer in the embedded browser (needs WebGUI).",
                        "NATIVE: the old in-game graph screen, kept as a fallback (spec 018 D2 c). WEB falls back to it without WebGUI.")
                .defineEnum("screen", ProgressionScreen.WEB);
        WEB_FPS = builder
                .comment("Frame rate of the embedded page while the screen is open; it drops to 1 when closed.")
                .defineInRange("webFps", 30, 1, 60);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
