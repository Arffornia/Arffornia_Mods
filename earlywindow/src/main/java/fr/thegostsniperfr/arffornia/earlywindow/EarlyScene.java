package fr.thegostsniperfr.arffornia.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.fml.earlydisplay.ElementShader;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImageWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The round 4b early window of spec 007 (Assets `early/window-line.html`): night backdrop, the
 * assembly line as a seamless 12 fps clip cross-faded between frames, the baked logo and wordmark, then every line a player reads
 * drawn live in their language. Coordinates are the mock-up's 854x480 CSS pixels.
 */
final class EarlyScene {
    static final String FIRST_FRAME_MARKER = "[arffornia-earlywindow] FIRST_FRAME";
    private static final Logger LOGGER = LoggerFactory.getLogger("ArfforniaEarlyWindow");
    private static final AtomicReference<Shot> SHOT = new AtomicReference<>();
    // Above the 12 units Minecraft's GlStateManager tracks, so the overlay phase cannot clobber them.
    private static final int UNIT = 20;
    private static final int LINE_FRAMES = 80;
    private static final float LINE_FPS = 12, LINE_X = 187, LINE_Y = 20, LINE_W = 480, LINE_H = 288;
    private static final float BAR_X = 252, BAR_Y = 416, BAR_W = 350, BAR_H = 4;
    private static final float MOTD_MAX_WIDTH = 760;

    private record Shot(Path file, CompletableFuture<Path> done) {}

    private final EarlyStrings strings;
    private final String motd;
    private final String eyebrow;
    private final EarlyClock clock = new EarlyClock();
    private final LoadingProgress progress = new LoadingProgress();
    private final SimpleBufferBuilder bb = new SimpleBufferBuilder(1);
    private EarlyTexture backdrop, overlay;
    private EarlyClip line;
    private EarlyFont small, legal, motdFont, motdFontSmall;
    private boolean firstFrameAnnounced;

    EarlyScene(EarlyStrings strings, String motd, String mcVersion) {
        this.strings = strings;
        this.motd = motd;
        this.eyebrow = strings.get("gui.arffornia.brand.eyebrow", mcVersion);
    }

    /** Test hook: the next painted frame is written to {@code file} as PNG. */
    static CompletableFuture<Path> requestShot(Path file) {
        var shot = new Shot(file, new CompletableFuture<>());
        SHOT.set(shot);
        return shot.done();
    }

    private void init(int scale) {
        String suffix = scale >= 2 ? "@2x" : "";
        backdrop = EarlyTexture.load("backdrop" + suffix + ".png");
        overlay = EarlyTexture.load("overlay" + suffix + ".png");
        line = new EarlyClip("line", LINE_FRAMES, LINE_FPS);
        ByteBuffer ttf = EarlyTexture.resource("ChakraPetch-Medium.ttf");
        small = new EarlyFont(ttf, 10.5f, scale);
        legal = new EarlyFont(ttf, 10f, scale);
        motdFont = new EarlyFont(ttf, 14f, scale);
        motdFontSmall = new EarlyFont(ttf, 12f, scale);
    }

    void paint(RenderElement.DisplayContext ctx) {
        int s = ctx.scale();
        if (backdrop == null) {
            init(s);
        }
        float t = clock.seconds();
        var snap = progress.update(bars(), t);
        ElementShader shader = ctx.elementShader();
        int alpha = 255;

        image(shader, backdrop, 0, 0, 854, 480, 0, 0, 1, 1, s, white(alpha));
        var clip = line.at(t);
        image(shader, clip.current(), LINE_X, LINE_Y, LINE_W, LINE_H, 0, 0, 1, 1, s, white(alpha));
        image(shader, clip.next(), LINE_X, LINE_Y, LINE_W, LINE_H, 0, 0, 1, 1, s, white(Math.round(alpha * clip.mix())));
        image(shader, overlay, 0, 0, 854, 480, 0, 0, 1, 1, s, white(alpha));

        bar(shader, snap.fraction(), s, alpha);

        text(shader, small, eyebrow, centred(small, eyebrow, 1.9f), 318, 1.9f, rgba(0xff, 0xb3, 0x6b, alpha));
        EarlyFont mf = motdFont.width(motd, 0) <= MOTD_MAX_WIDTH ? motdFont : motdFontSmall;
        text(shader, mf, motd, centred(mf, motd, 0), 385, 0, rgba(0xe6, 0xec, 0xef, (int) (alpha * 0.9f)));
        int statusColour = rgba(0x8d, 0xa0, 0xab, alpha);
        text(shader, small, strings.get(snap.stage().key()), BAR_X, 427, 0.2f, statusColour);
        if (snap.total() > 0) {
            String count = snap.current() + " / " + snap.total();
            text(shader, small, count, BAR_X + BAR_W - small.width(count, 0.2f), 427, 0.2f, statusColour);
        }
        String disclaimer = strings.get("gui.arffornia.brand.disclaimer");
        text(shader, legal, disclaimer, centred(legal, disclaimer, 0.4f), 457, 0.4f, rgba(0xff, 0xff, 0xff, (int) (alpha * 0.55f)));
        glActiveTexture(GL_TEXTURE0);

        if (!firstFrameAnnounced) {
            firstFrameAnnounced = true;
            // The launcher splash watches the game's stdout for this exact line (spec 007 item 9).
            System.out.println(FIRST_FRAME_MARKER);
            System.out.flush();
            LOGGER.info("First frame drawn ({}x scale, language {})", s, strings.language());
        }
        Shot shot = SHOT.getAndSet(null);
        if (shot != null) {
            writeShot(shot, ctx);
        }
    }

    private static List<LoadingProgress.Bar> bars() {
        return StartupNotificationManager.getCurrentProgress().stream()
                .map(pm -> new LoadingProgress.Bar(pm.name(), pm.steps(), pm.current()))
                .toList();
    }

    private void image(ElementShader shader, EarlyTexture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int s, int colour) {
        image(shader, tex.id(), x, y, w, h, u0, v0, u1, v1, s, colour);
    }

    private void image(ElementShader shader, int texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int s, int colour) {
        glActiveTexture(GL_TEXTURE0 + UNIT);
        glBindTexture(GL_TEXTURE_2D, texture);
        shader.updateTextureUniform(UNIT);
        shader.updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
        bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        bb.pos(x * s, y * s).tex(u0, v0).colour(colour).endVertex();
        bb.pos((x + w) * s, y * s).tex(u1, v0).colour(colour).endVertex();
        bb.pos(x * s, (y + h) * s).tex(u0, v1).colour(colour).endVertex();
        bb.pos((x + w) * s, (y + h) * s).tex(u1, v1).colour(colour).endVertex();
        bb.draw();
    }

    private void bar(ElementShader shader, float fraction, int s, int alpha) {
        float w = BAR_W * fraction;
        if (w < 1) return;
        int from = rgba(0xff, 0xb3, 0x6b, alpha);
        int to = lerp(from, rgba(0xe0, 0x70, 0x3c, alpha), fraction);
        shader.updateTextureUniform(0);
        shader.updateRenderTypeUniform(ElementShader.RenderType.BAR);
        bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        bb.pos(BAR_X * s, BAR_Y * s).tex(0, 0).colour(from).endVertex();
        bb.pos((BAR_X + w) * s, BAR_Y * s).tex(0, 0).colour(to).endVertex();
        bb.pos(BAR_X * s, (BAR_Y + BAR_H) * s).tex(0, 0).colour(from).endVertex();
        bb.pos((BAR_X + w) * s, (BAR_Y + BAR_H) * s).tex(0, 0).colour(to).endVertex();
        bb.draw();
    }

    private void text(ElementShader shader, EarlyFont font, String text, float x, float top, float tracking, int colour) {
        glActiveTexture(GL_TEXTURE0 + UNIT);
        glBindTexture(GL_TEXTURE_2D, font.texture());
        shader.updateTextureUniform(UNIT);
        shader.updateRenderTypeUniform(ElementShader.RenderType.FONT);
        bb.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        font.draw(bb, text, x, top, tracking, colour);
        bb.draw();
    }

    private static float centred(EarlyFont font, String text, float tracking) {
        return (854 - font.width(text, tracking)) / 2f;
    }

    /** FML's vertex colour is packed little-endian RGBA. */
    static int rgba(int r, int g, int b, int a) {
        return (a & 0xff) << 24 | (b & 0xff) << 16 | (g & 0xff) << 8 | (r & 0xff);
    }

    private static int white(int alpha) {
        return rgba(255, 255, 255, alpha);
    }

    private static int lerp(int a, int b, float k) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xff, cb = (b >>> shift) & 0xff;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }

    private static void writeShot(Shot shot, RenderElement.DisplayContext ctx) {
        int w = ctx.scaledWidth(), h = ctx.scaledHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        // The early framebuffer is presented flipped, so its first row is the top of the picture.
        if (STBImageWrite.stbi_write_png(shot.file().toString(), w, h, 4, pixels, w * 4)) {
            shot.done().complete(shot.file());
        } else {
            shot.done().completeExceptionally(new IllegalStateException("stbi_write_png failed for " + shot.file()));
        }
    }
}
