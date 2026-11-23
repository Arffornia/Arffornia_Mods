package fr.thegostsniperfr.arffornia.earlywindow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * Layer 1 of the spec 007 screen tests: the real provider, outside Minecraft, with a frozen clock and
 * a scripted progress feed. Writes {@code loading.<locale>.<t>.png} for each scripted moment.
 * usage: EarlyWindowShots <outDir> <locale>
 */
public final class EarlyWindowShots {
    private static final float[] TIMES = { 0.5f, 3f, 6f, 9f, 12f, 13.5f };

    public static void main(String[] args) throws Exception {
        Path out = Files.createDirectories(Path.of(args[0]));
        String locale = args[1];
        Path game = Files.createTempDirectory("arffornia-early-");
        Files.writeString(game.resolve("options.txt"), "lang:" + locale + "\n");
        FMLPaths.loadAbsolutePaths(game);
        FMLConfig.load();
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_FBSCALE, 2);
        System.setProperty(EarlyClock.TEST_CLOCK, "0");

        Runnable tick = new ArfforniaEarlyWindow().initialize(new String[] {
                "--fml.mcVersion", "1.21.1", "--fml.neoForgeVersion", "21.1.172", "--width", "854", "--height", "480" });
        ProgressMeter early = StartupNotificationManager.addProgressBar("EARLY", 0);
        ProgressMeter construction = null, setup = null, minecraft = null;
        int failures = 0;
        for (float t : TIMES) {
            if (t >= 3 && construction == null) construction = StartupNotificationManager.addProgressBar("Mod Construction", 331);
            if (t == 3) construction.setAbsolute(40);
            if (t == 6) construction.setAbsolute(200);
            if (t == 9) {
                StartupNotificationManager.popBar(construction);
                setup = StartupNotificationManager.addProgressBar("Common setup", 331);
                setup.setAbsolute(150);
            }
            if (t == 12) {
                StartupNotificationManager.popBar(setup);
                StartupNotificationManager.popBar(early);
                minecraft = StartupNotificationManager.prependProgressBar("Minecraft Progress", 1000);
                minecraft.setAbsolute(500);
            }
            if (t == 13.5f) minecraft.setAbsolute(1000);
            System.setProperty(EarlyClock.TEST_CLOCK, Float.toString(t));
            Path file = out.resolve("loading.%s.%s.png".formatted(locale, Float.toString(t)));
            var done = EarlyScene.requestShot(file);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!done.isDone() && System.nanoTime() < deadline) {
                tick.run();
                Thread.sleep(20);
            }
            if (done.isDone() && !done.isCompletedExceptionally()) {
                System.out.println("wrote " + file);
            } else {
                System.err.println("no frame for t=" + t);
                failures++;
            }
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
