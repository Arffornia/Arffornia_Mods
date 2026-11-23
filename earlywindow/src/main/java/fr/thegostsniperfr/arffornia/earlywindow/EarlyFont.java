package fr.thegostsniperfr.arffornia.earlywindow;

import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.stb.STBTruetype.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.MemoryStack;

/**
 * One font size in one texture. FML's SimpleFont only covers ASCII, which cannot write French.
 * Sizes are CSS pixels (em size), as in the Assets mock-ups.
 */
final class EarlyFont implements AutoCloseable {
    private static final int[] CODEPOINTS = codepoints();
    private final int texture;
    private final int atlasSize;
    private final STBTTPackedchar.Buffer chars;
    private final float ascent;
    private final float scale;

    EarlyFont(ByteBuffer ttf, float cssPixels, int scale) {
        this.scale = scale;
        float pixels = cssPixels * scale;
        atlasSize = pixels > 20 ? 1024 : 512;
        STBTTFontinfo info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, ttf)) {
            throw new IllegalStateException("Bad font");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer a = stack.mallocInt(1), d = stack.mallocInt(1), g = stack.mallocInt(1);
            stbtt_GetFontVMetrics(info, a, d, g);
            ascent = a.get(0) * stbtt_ScaleForMappingEmToPixels(info, pixels);
        }
        chars = STBTTPackedchar.malloc(CODEPOINTS.length);
        ByteBuffer bitmap = BufferUtils.createByteBuffer(atlasSize * atlasSize);
        IntBuffer cps = BufferUtils.createIntBuffer(CODEPOINTS.length).put(CODEPOINTS).flip();
        try (STBTTPackContext pc = STBTTPackContext.malloc(); STBTTPackRange.Buffer ranges = STBTTPackRange.malloc(1)) {
            // A negative size is stb_truetype's STBTT_POINT_SIZE: em size, like CSS font-size.
            ranges.get(0).font_size(-pixels).first_unicode_codepoint_in_range(0)
                    .array_of_unicode_codepoints(cps).num_chars(CODEPOINTS.length).chardata_for_range(chars);
            stbtt_PackBegin(pc, bitmap, atlasSize, atlasSize, 0, 1, 0L);
            stbtt_PackSetOversampling(pc, 2, 2);
            stbtt_PackFontRanges(pc, ttf, 0, ranges);
            stbtt_PackEnd(pc);
        }
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        int alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, atlasSize, atlasSize, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private static int[] codepoints() {
        int[] extra = { 0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2026, 0x202F, 0x0152, 0x0153 };
        int[] out = new int[(0xFF - 0x20 + 1) + extra.length];
        int i = 0;
        for (int c = 0x20; c <= 0xFF; c++) out[i++] = c;
        for (int c : extra) out[i++] = c;
        return out;
    }

    private static int index(int codepoint) {
        if (codepoint >= 0x20 && codepoint <= 0xFF) return codepoint - 0x20;
        for (int i = 0xFF - 0x20 + 1; i < CODEPOINTS.length; i++) {
            if (CODEPOINTS[i] == codepoint) return i;
        }
        return -1;
    }

    int texture() {
        return texture;
    }

    /** Width in CSS pixels. */
    float width(String text, float tracking) {
        float[] x = { 0 };
        float[] y = { 0 };
        try (STBTTAlignedQuad q = STBTTAlignedQuad.malloc()) {
            text.codePoints().forEach(cp -> {
                int i = index(cp == 0xA0 ? ' ' : cp);
                if (i >= 0) {
                    stbtt_GetPackedQuad(chars, atlasSize, atlasSize, i, x, y, q, false);
                    x[0] += tracking * scale;
                }
            });
        }
        return Math.max(0, x[0] - tracking * scale) / scale;
    }

    /** Emits quads with the top of the CSS line box at {@code top}; positions in CSS pixels. */
    void draw(SimpleBufferBuilder bb, String text, float left, float top, float tracking, int colour) {
        float[] x = { left * scale };
        float[] y = { top * scale + ascent };
        try (STBTTAlignedQuad q = STBTTAlignedQuad.malloc()) {
            text.codePoints().forEach(cp -> {
                int i = index(cp == 0xA0 ? ' ' : cp);
                if (i < 0) return;
                stbtt_GetPackedQuad(chars, atlasSize, atlasSize, i, x, y, q, false);
                bb.pos(q.x0(), q.y0()).tex(q.s0(), q.t0()).colour(colour).endVertex();
                bb.pos(q.x1(), q.y0()).tex(q.s1(), q.t0()).colour(colour).endVertex();
                bb.pos(q.x0(), q.y1()).tex(q.s0(), q.t1()).colour(colour).endVertex();
                bb.pos(q.x1(), q.y1()).tex(q.s1(), q.t1()).colour(colour).endVertex();
                x[0] += tracking * scale;
            });
        }
    }

    @Override
    public void close() {
        glDeleteTextures(texture);
        chars.free();
    }
}
