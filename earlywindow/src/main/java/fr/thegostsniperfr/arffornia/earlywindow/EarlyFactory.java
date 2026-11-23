package fr.thegostsniperfr.arffornia.earlywindow;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the {@link FactoryScene} in the early window: the still, then per group (back to front) the
 * belt chevrons, the moving sprites and the group's glass and matte. Everything is a function of the
 * time, redrawn every frame, so the motion is as smooth as the window's frame rate and never loops back.
 */
final class EarlyFactory {
    /** Draws a textured quad given its four corners in scene px (top-left, top-right, bottom-left, bottom-right). */
    interface Quads {
        void quad(int texture, float[] xy, float u0, float v0, float u1, float v1, int rgba);
    }

    private final FactoryScene scene;
    private final Map<String, EarlyTexture> textures = new HashMap<>();

    EarlyFactory(String dir) {
        scene = FactoryScene.parse(new String(bytes(EarlyTexture.resource(dir + "/scene.json")), StandardCharsets.UTF_8));
        texture(dir, scene.still(), false);
        texture(dir, scene.chevron(), true);
        scene.sprites().values().forEach(s -> texture(dir, s.file(), false));
        for (FactoryScene.Group g : scene.groups()) {
            if (g.glass() != null) texture(dir, g.glass().file(), false);
            if (g.matte() != null) texture(dir, g.matte().file(), false);
        }
    }

    int width() {
        return scene.width();
    }

    int height() {
        return scene.height();
    }

    void draw(Quads out, float seconds, int alpha) {
        int white = EarlyScene.rgba(255, 255, 255, alpha);
        rect(out, scene.still(), 0, 0, scene.width(), scene.height(), white);
        for (FactoryScene.Group g : scene.groups()) {
            if (g.belt() != null) belt(out, g.belt(), seconds, alpha);
            for (FactoryScene.Element e : g.elements()) {
                FactoryScene.Sprite s = scene.sprites().get(e.sprite());
                EarlyTexture tex = textures.get(s.file());
                for (float phase : e.phases()) {
                    FactoryScene.Pose p = FactoryScene.pose(e, seconds, phase);
                    if (p.alpha() <= 0.004f || p.scale() <= 0.004f) continue;
                    float x = s.pivotX() + (s.x() - s.pivotX()) * p.scale() + p.dx();
                    float y = s.pivotY() + (s.y() - s.pivotY()) * p.scale() + p.dy();
                    float w = s.w() * p.scale(), h = s.h() * p.scale();
                    int colour = EarlyScene.rgba(255, 255, 255, Math.round(alpha * Math.min(1, p.alpha())));
                    if (s.frames() == 0) {
                        out.quad(tex.id(), corners(x, y, w, h), 0, 0, 1, 1, colour);
                    } else {
                        int rows = (s.frames() + s.cols() - 1) / s.cols();
                        int frame = Math.floorMod((int) Math.floor(p.frame()), s.frames());
                        float u = (float) (frame % s.cols()) / s.cols(), v = (float) (frame / s.cols()) / rows;
                        out.quad(tex.id(), corners(x, y, w, h), u, v, u + 1f / s.cols(), v + 1f / rows, colour);
                    }
                }
            }
            if (g.glass() != null) rect(out, g.glass().file(), g.glass().x(), g.glass().y(), g.glass().w(), g.glass().h(), white);
            if (g.matte() != null) rect(out, g.matte().file(), g.matte().x(), g.matte().y(), g.matte().w(), g.matte().h(), white);
        }
    }

    private void belt(Quads out, FactoryScene.Belt b, float seconds, int alpha) {
        float offset = (float) -((seconds * (double) b.speed()) % 1.0);
        int[] c = b.rgba();
        float[] xy = { b.u0v0()[0], b.u0v0()[1], b.u1v0()[0], b.u1v0()[1], b.u0v1()[0], b.u0v1()[1], b.u1v1()[0], b.u1v1()[1] };
        out.quad(textures.get(scene.chevron()).id(), xy, offset, 0, offset + b.len(), 1, EarlyScene.rgba(c[0], c[1], c[2], c[3] * alpha / 255));
    }

    private void rect(Quads out, String file, float x, float y, float w, float h, int colour) {
        out.quad(textures.get(file).id(), corners(x, y, w, h), 0, 0, 1, 1, colour);
    }

    private static float[] corners(float x, float y, float w, float h) {
        return new float[] { x, y, x + w, y, x, y + h, x + w, y + h };
    }

    private void texture(String dir, String file, boolean repeat) {
        textures.computeIfAbsent(file, f -> EarlyTexture.load(dir + "/" + f, true, repeat));
    }

    private static byte[] bytes(java.nio.ByteBuffer b) {
        byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }
}
