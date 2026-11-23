package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.blaze3d.platform.NativeImage;
import fr.thegostsniperfr.arffornia.Arffornia;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The title screen in motion, inert unless {@code -Darffornia.screens.motion=<dir>} is set (the
 * {@code clientMotion} dev run): at GUI scale {@code arffornia.screens.motionGui}, logs frame intervals over
 * {@code seconds} of real time, then writes the same span frame by frame at {@code fps} (frozen clock), so the
 * motion can be looked at across every element's loop. Then halts.
 */
@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class ScreenMotionRun {
    private static final float START = 3f;
    private static int warmup;
    private static long timingUntil;
    private static final List<Long> frameTimes = new ArrayList<>();
    private static int captured = -1;

    private ScreenMotionRun() {}

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        String dir = System.getProperty("arffornia.screens.motion");
        if (dir == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || !(mc.screen instanceof ArfTitleScreen)) return;
        try {
            step(mc, Path.of(dir));
        } catch (Exception e) {
            Arffornia.LOGGER.error("Motion run failed", e);
            Runtime.getRuntime().halt(1);
        }
    }

    private static void step(Minecraft mc, Path dir) throws IOException {
        float fps = Float.parseFloat(System.getProperty("arffornia.screens.motionFps", "30"));
        float seconds = Float.parseFloat(System.getProperty("arffornia.screens.motionSeconds", "7"));
        int gui = Integer.getInteger("arffornia.screens.motionGui", 5);
        if (warmup == 0) {
            Files.createDirectories(dir);
            mc.options.guiScale().set(gui);
            mc.resizeDisplay();
            mc.setScreen(new ArfTitleScreen());
            System.clearProperty(ArfTitleScreen.TEST_CLOCK);
        }
        if (++warmup < 120) return;
        if (timingUntil == 0) timingUntil = System.nanoTime() + (long) (seconds * 1e9);
        if (System.nanoTime() < timingUntil) {
            frameTimes.add(System.nanoTime());
            return;
        }
        if (captured < 0) {
            writeTiming(dir, seconds);
            captured = 0;
            System.setProperty(ArfTitleScreen.TEST_CLOCK, Long.toString(Math.round(START * 1000)));
            return;
        }
        // the frame just drawn used the clock set after the previous capture
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(dir.resolve("m%04d.png".formatted(captured)));
        }
        captured++;
        if (captured >= Math.round(seconds * fps)) {
            Arffornia.LOGGER.info("Motion run done: {} frames in {}", captured, dir);
            Runtime.getRuntime().halt(0);
        }
        System.setProperty(ArfTitleScreen.TEST_CLOCK, Long.toString(Math.round((START + captured / fps) * 1000)));
    }

    private static void writeTiming(Path dir, float seconds) throws IOException {
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < frameTimes.size(); i++) intervals.add((frameTimes.get(i) - frameTimes.get(i - 1)) / 1e6);
        intervals.sort(null);
        int n = intervals.size();
        String stats = n == 0 ? "no frames\n" : "%s: %d frames in %.1f s at %dx%d: frame ms p50 %.1f p95 %.1f p99 %.1f max %.1f%n".formatted(
                com.mojang.blaze3d.platform.GlUtil.getRenderer(), n + 1, seconds, Minecraft.getInstance().getWindow().getWidth(),
                Minecraft.getInstance().getWindow().getHeight(), intervals.get(n / 2), intervals.get((int) (n * .95)), intervals.get((int) (n * .99)), intervals.get(n - 1));
        Arffornia.LOGGER.info("Motion run timing: {}", stats.strip());
        Files.writeString(dir.resolve("timing.txt"), stats, StandardCharsets.UTF_8);
    }
}
