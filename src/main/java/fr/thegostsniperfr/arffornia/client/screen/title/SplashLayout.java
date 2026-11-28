package fr.thegostsniperfr.arffornia.client.screen.title;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The MOTD drawn as vanilla's yellow splash (spec 007 D14, D15): vanilla's pulse and length scale,
 * capped at 380 px wide at 1080p, two lines past 28 characters, cap height never under 15 px.
 * Sizes are in the screen's 1920x1080 design pixels.
 */
public final class SplashLayout {
    static final int MAX_WIDTH = 380;
    static final int MIN_CAP = 15;
    static final int SPLIT_OVER = 28;
    /** The GUI scale Minecraft picks at 1920x1080; vanilla's splash scale is in GUI pixels. */
    static final int DESIGN_GUI_SCALE = 4;

    public record Layout(List<String> lines, float scale) {}

    private SplashLayout() {}

    public static float pulse(long millis) {
        return 1.8f - Math.abs((float) Math.sin((millis % 1000L) / 1000f * Math.PI * 2)) * 0.1f;
    }

    public static List<String> lines(String motd) {
        if (motd.length() <= SPLIT_OVER) return List.of(motd);
        double mid = motd.length() / 2.0;
        int best = -1;
        for (int i = 0; i < motd.length(); i++) {
            if (motd.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i;
        }
        return best < 0 ? List.of(motd) : List.of(motd.substring(0, best), motd.substring(best + 1));
    }

    /** {@code scale} is design pixels per font pixel. */
    public static Layout layout(String motd, ToIntFunction<String> fontWidth, long millis) {
        List<String> lines = lines(motd);
        int widest = lines.stream().mapToInt(fontWidth).max().orElse(1);
        float pulse = pulse(millis);
        float vanilla = pulse * 100f / (widest + 32) * DESIGN_GUI_SCALE;
        float capped = Math.max(MIN_CAP / 7f, (float) MAX_WIDTH / widest) * (pulse / 1.8f);
        return new Layout(lines, Math.min(vanilla, capped));
    }
}
