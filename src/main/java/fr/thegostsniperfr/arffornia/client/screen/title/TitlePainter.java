package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Flat-colour shapes the GUI API lacks: hexagons and discs with vertical or radial gradients, and
 * thick line segments for the tile icons. Coordinates are in the current pose (design pixels).
 */
final class TitlePainter {
    private static final float[][] HEX = { { -0.5f, 0f }, { -0.25f, -0.5f }, { 0.25f, -0.5f }, { 0.5f, 0f }, { 0.25f, 0.5f }, { -0.25f, 0.5f } };

    private TitlePainter() {}

    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void end(GuiGraphics g, BufferBuilder buffer) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
    }

    /** Flat-top hexagon of {@code w} x {@code h} centred on (cx, cy), {@code top} to {@code bottom} gradient. */
    static void hex(GuiGraphics g, float cx, float cy, float w, float h, int top, int bottom) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin();
        int mid = lerp(top, bottom, 0.5f);
        for (int i = 0; i < 6; i++) {
            float[] p = HEX[i], q = HEX[(i + 1) % 6];
            b.addVertex(m, cx, cy, 0).setColor(mid);
            b.addVertex(m, cx + p[0] * w, cy + p[1] * h, 0).setColor(lerp(top, bottom, p[1] + 0.5f));
            b.addVertex(m, cx + q[0] * w, cy + q[1] * h, 0).setColor(lerp(top, bottom, q[1] + 0.5f));
        }
        end(g, b);
    }

    /** Hexagonal ring between the outer size and an inset of {@code inset} pixels. */
    static void hexRing(GuiGraphics g, float cx, float cy, float w, float h, float inset, int colour) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin();
        float iw = w - 2 * inset * 1.15f, ih = h - 2 * inset;
        for (int i = 0; i < 6; i++) {
            float[] p = HEX[i], q = HEX[(i + 1) % 6];
            float ox0 = cx + p[0] * w, oy0 = cy + p[1] * h, ox1 = cx + q[0] * w, oy1 = cy + q[1] * h;
            float ix0 = cx + p[0] * iw, iy0 = cy + p[1] * ih, ix1 = cx + q[0] * iw, iy1 = cy + q[1] * ih;
            b.addVertex(m, ox0, oy0, 0).setColor(colour);
            b.addVertex(m, ox1, oy1, 0).setColor(colour);
            b.addVertex(m, ix1, iy1, 0).setColor(colour);
            b.addVertex(m, ox0, oy0, 0).setColor(colour);
            b.addVertex(m, ix1, iy1, 0).setColor(colour);
            b.addVertex(m, ix0, iy0, 0).setColor(colour);
        }
        end(g, b);
    }

    /** Disc fading from {@code inner} at the centre to {@code outer} at radius r. */
    static void disc(GuiGraphics g, float cx, float cy, float r, int inner, int outer) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin();
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            b.addVertex(m, cx, cy, 0).setColor(inner);
            b.addVertex(m, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, 0).setColor(outer);
            b.addVertex(m, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 0).setColor(outer);
        }
        end(g, b);
    }

    /** Polyline of thickness {@code width}; points are x,y pairs. */
    static void polyline(GuiGraphics g, float width, int colour, float... xy) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin();
        for (int i = 0; i + 3 < xy.length; i += 2) {
            float x0 = xy[i], y0 = xy[i + 1], x1 = xy[i + 2], y1 = xy[i + 3];
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
            if (len == 0) continue;
            // Extend each segment by half the width so joints and corners stay closed.
            float ux = dx / len * width / 2, uy = dy / len * width / 2, nx = -uy, ny = ux;
            x0 -= ux; y0 -= uy; x1 += ux; y1 += uy;
            b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(colour);
            b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(colour);
            b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(colour);
            b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(colour);
            b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(colour);
            b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(colour);
        }
        end(g, b);
    }

    static void circle(GuiGraphics g, float cx, float cy, float r, float width, int colour) {
        int n = 24;
        float[] xy = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            xy[2 * i] = cx + (float) Math.cos(Math.PI * 2 * i / n) * r;
            xy[2 * i + 1] = cy + (float) Math.sin(Math.PI * 2 * i / n) * r;
        }
        polyline(g, width, colour, xy);
    }

    static int lerp(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xff, cb = (b >>> shift) & 0xff;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }

    static int argb(int a, int rgb) {
        return (a & 0xff) << 24 | (rgb & 0xffffff);
    }

    static int alpha(float a, int rgb) {
        return argb(Math.round(Math.max(0, Math.min(1, a)) * 255), rgb);
    }

    static boolean insideHex(double px, double py, double cx, double cy, double w, double h) {
        double dx = Math.abs(px - cx), dy = Math.abs(py - cy);
        return dy <= h / 2 && dx <= w / 2 - dy * (w / 4) / (h / 2);
    }
}
