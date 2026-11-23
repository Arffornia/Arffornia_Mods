package fr.thegostsniperfr.arffornia.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

record EarlyTexture(int id, int width, int height) implements AutoCloseable {
    static ByteBuffer resource(String name) {
        try (InputStream in = EarlyTexture.class.getResourceAsStream("/arffornia_earlywindow/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + name);
            }
            byte[] bytes = in.readAllBytes();
            return BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static EarlyTexture load(String name) {
        return load(name, false, false);
    }

    /** Mipmapped for art drawn smaller than baked (a 4x still in a 2x framebuffer); repeat for scrolling strips. */
    static EarlyTexture load(String name, boolean mipmaps, boolean repeat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(resource(name), w, h, c, 4);
            if (pixels == null) {
                throw new IllegalStateException("Cannot decode " + name + ": " + STBImage.stbi_failure_reason());
            }
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mipmaps ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            if (mipmaps) glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
            STBImage.stbi_image_free(pixels);
            return new EarlyTexture(id, w.get(0), h.get(0));
        }
    }

    @Override
    public void close() {
        glDeleteTextures(id);
    }
}
