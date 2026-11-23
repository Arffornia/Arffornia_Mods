package fr.thegostsniperfr.arffornia.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

/**
 * A looping clip of JPEG frames (Arffornia_Assets `early/line.mjs`), kept encoded and decoded one frame
 * at a time: the 80 frames at 2x would take 170 MB of texture memory while the game is still loading.
 * Two textures hold the frames around the current time, so the clip can cross-fade between them.
 */
final class EarlyClip {
    private final ByteBuffer[] frames;
    private final float fps;
    private final int[] textures = new int[2];
    private final int[] shown = { -1, -1 };
    private final int width, height;

    EarlyClip(String dir, int count, float fps) {
        this.fps = fps;
        frames = new ByteBuffer[count];
        for (int i = 0; i < count; i++) {
            frames[i] = EarlyTexture.resource(dir + "/f%04d.jpg".formatted(i));
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var c = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(frames[0], w, h, c)) {
                throw new IllegalStateException("Cannot read " + dir + "/f0000.jpg: " + STBImage.stbi_failure_reason());
            }
            width = w.get(0);
            height = h.get(0);
        }
        for (int i = 0; i < 2; i++) {
            textures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** Two frames and how far the clip is from the first to the second (0..1). */
    record Blend(int current, int next, float mix) {}

    /** Frame indices at {@code seconds}: the last frame blends into the first, the loop has no seam. */
    static Blend frames(float seconds, float fps, int count) {
        double position = seconds * (double) fps;
        int base = (int) Math.floor(position);
        int current = Math.floorMod(base, count);
        return new Blend(current, (current + 1) % count, (float) (position - base));
    }

    /** Like {@link #frames} but with the GL texture ids holding those frames. */
    Blend at(float seconds) {
        Blend f = frames(seconds, fps, frames.length);
        int a = textureFor(f.current(), f.next()), b = textureFor(f.next(), f.current());
        return new Blend(textures[a], textures[b], f.mix());
    }

    private int textureFor(int frame, int keep) {
        for (int i = 0; i < 2; i++) {
            if (shown[i] == frame) return i;
        }
        int slot = shown[0] == keep ? 1 : 0;
        upload(slot, frame);
        return slot;
    }

    private void upload(int slot, int frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(frames[frame], w, h, c, 4);
            if (pixels == null) {
                throw new IllegalStateException("Cannot decode clip frame " + frame + ": " + STBImage.stbi_failure_reason());
            }
            glBindTexture(GL_TEXTURE_2D, textures[slot]);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w.get(0), h.get(0), GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glBindTexture(GL_TEXTURE_2D, 0);
            STBImage.stbi_image_free(pixels);
        }
        shown[slot] = frame;
    }
}
