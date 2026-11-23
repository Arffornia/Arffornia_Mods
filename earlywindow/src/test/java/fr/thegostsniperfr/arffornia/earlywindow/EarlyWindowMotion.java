package fr.thegostsniperfr.arffornia.earlywindow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * The early window as a player sees it in motion: the real provider at the pack's FBScale in a window of
 * the given size, first {@code seconds} of real time with paint intervals and paint cost logged, then the
 * same span frame by frame at {@code fps} (frozen clock) written as PNGs to look at, loop boundaries included.
 * usage: EarlyWindowMotion <outDir> <width> <height> <fps> <seconds>
 */
public final class EarlyWindowMotion {
    public static void main(String[] args) throws Exception {
        Path out = Files.createDirectories(Path.of(args[0]));
        int width = Integer.parseInt(args[1]), height = Integer.parseInt(args[2]);
        float fps = Float.parseFloat(args[3]), seconds = Float.parseFloat(args[4]);
        Path game = Files.createTempDirectory("arffornia-early-");
        Files.writeString(game.resolve("options.txt"), "lang:en_us\n");
        FMLPaths.loadAbsolutePaths(game);
        FMLConfig.load();
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_FBSCALE, 2);
        Runnable tick = new ArfforniaEarlyWindow().initialize(new String[] {
                "--fml.mcVersion", "1.21.1", "--fml.neoForgeVersion", "21.1.172", "--width", Integer.toString(width), "--height", Integer.toString(height) });
        StartupNotificationManager.addProgressBar("Mod Construction", 331).setAbsolute(120);

        List<long[]> paints = new ArrayList<>();
        EarlyScene.paintProbe = (start, end) -> {
            synchronized (paints) {
                paints.add(new long[] { start, end });
            }
        };
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos((long) (seconds * 1000));
        while (System.nanoTime() < until) {
            tick.run();
            Thread.sleep(5);
        }
        EarlyScene.paintProbe = null;
        List<Double> intervals = new ArrayList<>(), costs = new ArrayList<>();
        synchronized (paints) {
            for (int i = 1; i < paints.size(); i++) intervals.add((paints.get(i)[0] - paints.get(i - 1)[0]) / 1e6);
            for (long[] p : paints) costs.add((p[1] - p[0]) / 1e6);
        }
        intervals.sort(null);
        costs.sort(null);
        String stats = "paints %d in %.1f s: interval ms p50 %.1f p95 %.1f p99 %.1f max %.1f; paint cost ms p50 %.2f p95 %.2f max %.2f%n".formatted(
                paints.size(), seconds, q(intervals, .5), q(intervals, .95), q(intervals, .99), q(intervals, 1), q(costs, .5), q(costs, .95), q(costs, 1));
        System.out.print(stats);
        Files.writeString(out.resolve("timing.txt"), stats);

        int frames = Math.round(seconds * fps);
        for (int k = 0; k < frames; k++) {
            System.setProperty(EarlyClock.TEST_CLOCK, Float.toString(k / fps));
            var done = EarlyScene.requestShot(out.resolve("m%04d.png".formatted(k)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!done.isDone() && System.nanoTime() < deadline) {
                tick.run();
                Thread.sleep(2);
            }
        }
        System.exit(0);
    }

    private static double q(List<Double> sorted, double quantile) {
        return sorted.isEmpty() ? 0 : sorted.get((int) Math.min(sorted.size() - 1, Math.floor(quantile * sorted.size())));
    }
}
