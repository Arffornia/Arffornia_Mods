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
 * (the {@code clientScreens} dev run): screenshots the title screen in each language and GUI scale
 * once it has rendered for a while, writes the GL renderer next to them, then quits.
 */
@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class ScreenTestRun {
    static final String PROPERTY = "arffornia.screens.test";
    private static final int FRAMES = 60;
    private static final long SETTLE_MS = 4000;
    private record Shot(String language, int guiScale) {}

    private static final List<Shot> SHOTS = List.of(new Shot("en_us", 2), new Shot("en_us", 4), new Shot("fr_fr", 2), new Shot("fr_fr", 4));
    private static int next;
    private static int frames;
    private static long shownAt;
    private static boolean waitingForReload;
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
        if (next >= SHOTS.size()) {
            Arffornia.LOGGER.info("Screen test done: {} shots in {}", SHOTS.size(), dir);
            mc.stop();
            return;
        }
        Shot shot = SHOTS.get(next);
        if (waitingForReload) {
            waitingForReload = false;
            show(mc, shot);
            return;
        }
        if (!mc.options.languageCode.equals(shot.language())) {
            mc.options.languageCode = shot.language();
            mc.getLanguageManager().setSelected(shot.language());
            waitingForReload = true;
            mc.reloadResourcePacks();
            return;
        }
        if (mc.options.guiScale().get() != shot.guiScale() || shownAt == 0) {
            show(mc, shot);
            return;
        }
        if (++frames < FRAMES || Util.getMillis() - shownAt < SETTLE_MS) return;
        Path file = dir.resolve("title.%s.gui%d.png".formatted(shot.language(), shot.guiScale()));
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(file);
        }
        Arffornia.LOGGER.info("Screen test wrote {}", file);
        next++;
        shownAt = 0;
    }

    private static void show(Minecraft mc, Shot shot) {
        mc.options.guiScale().set(shot.guiScale());
        mc.resizeDisplay();
        mc.setScreen(new ArfTitleScreen());
        frames = 0;
        shownAt = Util.getMillis();
    }
}
