package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import fr.thegostsniperfr.arffornia.Arffornia;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The title screen's animated factory: a seamless loop of 1920x1080 JPEG frames (Arffornia_Assets
 * {@code ui/title/background.mjs}). A texture .mcmeta animation cannot hold it (80 frames stacked in one
 * texture are far past the GL size limit), so the frames are decoded here, the next one on a background
 * thread, into two textures the screen cross-fades between.
 */
final class TitleBackground {
    static final int WIDTH = 1920, HEIGHT = 1080, FRAMES = 80;
    static final float FPS = 12;
    private static final ResourceLocation[] SLOTS = { rl("dynamic/title_background_0"), rl("dynamic/title_background_1") };

    private final int[] shown = { -1, -1 };
    private boolean created;
    private CompletableFuture<Decoded> pending;
    private int pendingFrame = -1;

    /** Textures to draw: {@code current}, then {@code next} at opacity {@code mix} (null when only one is ready). */
    record Layers(ResourceLocation current, ResourceLocation next, float mix) {}

    private record Decoded(int frame, ByteBuffer pixels) {}

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, path);
    }

    Layers at(float seconds) {
        if (!created) create();
        LoopFrames f = LoopFrames.at(seconds, FPS, FRAMES);
        int a = slotOf(f.current());
        if (a < 0) {
            // First frame, or the clock jumped: load both frames now rather than show a stale one.
            dropPending();
            upload(0, decode(f.current()));
            upload(1, decode(f.next()));
            a = 0;
        }
        int b = slotOf(f.next());
        if (b < 0 && pendingFrame == f.next() && pending.isDone()) {
            b = 1 - a;
            upload(b, pending.join());
            pending = null;
            pendingFrame = -1;
        }
        int ahead = (f.next() + 1) % FRAMES;
        if (b >= 0 && pending == null) {
            pendingFrame = ahead;
            pending = CompletableFuture.supplyAsync(() -> decode(ahead), Util.backgroundExecutor());
        }
        return new Layers(SLOTS[a], b >= 0 ? SLOTS[b] : null, f.mix());
    }

    void close() {
        if (!created) return;
        dropPending();
        for (ResourceLocation slot : SLOTS) Minecraft.getInstance().getTextureManager().release(slot);
        shown[0] = shown[1] = -1;
        created = false;
    }

    private void create() {
        for (ResourceLocation slot : SLOTS) Minecraft.getInstance().getTextureManager().register(slot, new ClipTexture());
        created = true;
    }

    private int slotOf(int frame) {
        return shown[0] == frame ? 0 : shown[1] == frame ? 1 : -1;
    }

    private void dropPending() {
        if (pending != null) pending.thenAccept(d -> STBImage.stbi_image_free(d.pixels()));
        pending = null;
        pendingFrame = -1;
    }

    private void upload(int slot, Decoded frame) {
        try {
            ((ClipTexture) Minecraft.getInstance().getTextureManager().getTexture(SLOTS[slot])).upload(frame.pixels());
        } finally {
            STBImage.stbi_image_free(frame.pixels());
        }
        shown[slot] = frame.frame();
    }

    private static Decoded decode(int frame) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation id = rl("textures/gui/title/background/f%04d.jpg".formatted(frame));
        ByteBuffer jpeg = null;
        try (InputStream in = resources.open(id)) {
            jpeg = TextureUtil.readResource(in);
            jpeg.rewind();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1);
                var h = stack.mallocInt(1);
                var c = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(jpeg, w, h, c, 4);
                if (pixels == null) throw new IOException("Cannot decode " + id + ": " + STBImage.stbi_failure_reason());
                if (w.get(0) != WIDTH || h.get(0) != HEIGHT) {
                    STBImage.stbi_image_free(pixels);
                    throw new IOException(id + " is " + w.get(0) + "x" + h.get(0) + ", expected " + WIDTH + "x" + HEIGHT);
                }
                return new Decoded(frame, pixels);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (jpeg != null) MemoryUtil.memFree(jpeg);
        }
    }

    private static final class ClipTexture extends AbstractTexture {
        ClipTexture() {
            TextureUtil.prepareImage(getId(), WIDTH, HEIGHT);
            setFilter(true, false);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        void upload(ByteBuffer rgba) {
            bind();
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, MemoryUtil.memAddress(rgba));
        }

        @Override
        public void load(ResourceManager manager) {}
    }
}
