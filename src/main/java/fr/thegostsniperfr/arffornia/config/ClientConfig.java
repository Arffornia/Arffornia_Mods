package fr.thegostsniperfr.arffornia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code arffornia-client.toml}: player-side choices, never synced from the server. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue WEB_FPS;
    public static final ModConfigSpec.EnumValue<HudCorner> TARGET_HUD_CORNER;
    public static final ModConfigSpec.IntValue TARGET_HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue TARGET_HUD_OFFSET_Y;

    public enum HudCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("progression");
        WEB_FPS = builder
                .comment("Frame rate of the embedded page while the screen is open; it drops to 1 when closed.")
                .defineInRange("webFps", 30, 1, 60);
        builder.pop();
        builder.push("hud");
        TARGET_HUD_CORNER = builder
                .comment("Screen corner of the target milestone label. Move it if it collides with a minimap.")
                .defineEnum("targetCorner", HudCorner.BOTTOM_RIGHT);
        TARGET_HUD_OFFSET_X = builder
                .comment("Horizontal distance from that corner, in GUI pixels.")
                .defineInRange("targetOffsetX", 5, 0, 2000);
        TARGET_HUD_OFFSET_Y = builder
                .comment("Vertical distance from that corner, in GUI pixels.")
                .defineInRange("targetOffsetY", 5, 0, 2000);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
