package fr.thegostsniperfr.arffornia.earlywindow;

import java.util.List;

/**
 * Maps FML's progress bars to one monotonic bar and the stage keys of spec 007. The launcher splash
 * owns 0-4 %, so the window starts there and never moves backwards.
 */
public final class LoadingProgress {
    public enum Stage { BOOT, DISCOVERY, MODS, REGISTRIES, RESOURCES, READY;
        public String key() {
            return "gui.arffornia.loading.stage." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Bar(String name, int steps, int current) {
        float progress() {
            return steps <= 0 ? 0f : Math.min(1f, (float) current / steps);
        }
    }

    public record Snapshot(Stage stage, float fraction, int current, int total) {}

    static final String EARLY = "EARLY";
    static final String MOD_CONSTRUCTION = "Mod Construction";
    static final String MINECRAFT = "Minecraft Progress";
    static final float LAUNCHER_SHARE = 0.04f;

    private Stage stage = Stage.BOOT;
    private float fraction = LAUNCHER_SHARE;
    private int current;
    private int total;

    public Snapshot update(List<Bar> bars, float secondsSinceStart) {
        Stage seen = Stage.BOOT;
        float seenFraction = LAUNCHER_SHARE;
        int seenCurrent = 0;
        int seenTotal = 0;
        Bar minecraft = find(bars, MINECRAFT);
        Bar construction = find(bars, MOD_CONSTRUCTION);
        Bar setup = bars.stream().filter(b -> b.steps() > 0 && !b.name().equals(EARLY) && !b.name().equals(MINECRAFT) && !b.name().equals(MOD_CONSTRUCTION)).findFirst().orElse(null);
        if (minecraft != null) {
            float p = minecraft.progress();
            seen = p >= 0.999f ? Stage.READY : Stage.RESOURCES;
            seenFraction = 0.70f + 0.30f * p;
        } else if (construction != null && construction.steps() > 0) {
            seen = Stage.MODS;
            seenFraction = 0.15f + 0.35f * construction.progress();
            seenCurrent = construction.current();
            seenTotal = construction.steps();
        } else if (setup != null) {
            seen = Stage.REGISTRIES;
            seenFraction = 0.50f + 0.20f * setup.progress();
            seenCurrent = setup.current();
            seenTotal = setup.steps();
        } else if (!bars.isEmpty()) {
            seen = Stage.DISCOVERY;
            // FML gives no count while it scans mods/: ease towards 15 % so the bar is not frozen.
            seenFraction = 0.05f + 0.10f * (1f - (float) Math.exp(-secondsSinceStart / 8f));
        }
        if (seen.ordinal() >= stage.ordinal()) {
            if (seen != stage) {
                current = 0;
                total = 0;
            }
            stage = seen;
            if (seenTotal > 0) {
                current = seenCurrent;
                total = seenTotal;
            }
        }
        fraction = Math.max(fraction, Math.min(1f, seenFraction));
        return new Snapshot(stage, fraction, current, total);
    }

    private static Bar find(List<Bar> bars, String name) {
        return bars.stream().filter(b -> b.name().equals(name)).findFirst().orElse(null);
    }
}
