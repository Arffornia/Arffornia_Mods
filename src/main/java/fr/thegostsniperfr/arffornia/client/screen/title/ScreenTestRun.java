package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.NativeImage;
import fr.thegostsniperfr.arffornia.Arffornia;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Layer 2 of the spec 007 screen tests, inert unless {@code -Darffornia.screens.test=<dir>} is set
 * (the {@code clientScreens} dev run): screenshots the title screen at each GUI scale in the language
 * of {@code options.txt} once it has rendered for a while, writes the GL renderer next to them, then
 * quits. One run per language: with WebGUI loaded, the resource reload of a language switch crashed
 * the JVM in libharfbuzz on the Ubuntu CI runner.
 */
@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class ScreenTestRun {
    static final String PROPERTY = "arffornia.screens.test";
    private static final int FRAMES = 60;
    private static final long SETTLE_MS = 4000;
    private static final List<Integer> GUI_SCALES = List.of(2, 4);
    private static int next;
    private static int frames;
    private static long shownAt;
    private static boolean started;

    private ScreenTestRun() {}

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        String dir = System.getProperty(PROPERTY);
        if (dir == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || !(mc.screen instanceof ArfTitleScreen)) return;
        try {
            step(mc, Path.of(dir));
        } catch (Exception e) {
            Arffornia.LOGGER.error("Screen test failed", e);
            Runtime.getRuntime().halt(1);
        }
    }

    private static void step(Minecraft mc, Path dir) throws IOException {
        if (!started) {
            started = true;
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("gl.txt"), GlUtil.getRenderer() + "\n" + GlUtil.getOpenGLVersion() + "\n", StandardCharsets.UTF_8);
        }
        if (next >= GUI_SCALES.size()) {
            Arffornia.LOGGER.info("Screen test done: {} shots in {}", GUI_SCALES.size(), dir);
            mc.stop();
            return;
        }
        int guiScale = GUI_SCALES.get(next);
        if (mc.options.guiScale().get() != guiScale || shownAt == 0) {
            show(mc, guiScale);
            return;
        }
        if (++frames < FRAMES || Util.getMillis() - shownAt < SETTLE_MS) return;
        Path file = dir.resolve("title.%s.gui%d.png".formatted(mc.options.languageCode, guiScale));
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(file);
        }
        Arffornia.LOGGER.info("Screen test wrote {}", file);
        next++;
        shownAt = 0;
    }

    private static void show(Minecraft mc, int guiScale) {
        mc.options.guiScale().set(guiScale);
        mc.resizeDisplay();
        mc.setScreen(new ArfTitleScreen());
        frames = 0;
        shownAt = Util.getMillis();
    }
}
