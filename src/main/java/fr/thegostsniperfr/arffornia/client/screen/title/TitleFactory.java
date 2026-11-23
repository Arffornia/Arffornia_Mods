package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.thegostsniperfr.arffornia.Arffornia;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The title screen's factory, drawn live every frame from the layers Arffornia_Assets {@code early/scene.mjs}
 * bakes (the same scene and timing as the early window): the still, then per group the belt chevrons, the
 * moving sprites, the group's glass and matte, then the darkening overlay. Coordinates are scene px (the
 * still is 3840x2160 for the 1920x1080 design); textures are mipmapped, so any window size stays smooth.
 */
final class TitleFactory {
    private static final String DIR = "textures/gui/title/factory/";

    private FactoryScene scene;
    private final Map<String, Integer> textures = new HashMap<>();
    private String overlay;

    int width() {
        load();
        return scene.width();
    }

    int height() {
        load();
        return scene.height();
    }

    /** Draws at the current pose, in scene px. */
    void draw(GuiGraphics g, float seconds) {
        load();
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f m = g.pose().last().pose();
        rect(m, scene.still(), 0, 0, scene.width(), scene.height(), 1);
        for (FactoryScene.Group group : scene.groups()) {
            if (group.belt() != null) belt(m, group.belt(), seconds);
            for (FactoryScene.Element e : group.elements()) {
                FactoryScene.Sprite s = scene.sprites().get(e.sprite());
                for (float phase : e.phases()) {
                    FactoryScene.Pose p = FactoryScene.pose(e, seconds, phase);
                    if (p.alpha() <= 0.004f || p.scale() <= 0.004f) continue;
                    float x = s.pivotX() + (s.x() - s.pivotX()) * p.scale() + p.dx();
                    float y = s.pivotY() + (s.y() - s.pivotY()) * p.scale() + p.dy();
                    float w = s.w() * p.scale(), h = s.h() * p.scale(), a = Math.min(1, p.alpha());
                    if (s.frames() == 0) {
                        quad(m, s.file(), corners(x, y, w, h), 0, 0, 1, 1, 255, 255, 255, a);
                    } else {
                        int rows = (s.frames() + s.cols() - 1) / s.cols();
                        int frame = Math.floorMod((int) Math.floor(p.frame()), s.frames());
                        float u = (float) (frame % s.cols()) / s.cols(), v = (float) (frame / s.cols()) / rows;
                        quad(m, s.file(), corners(x, y, w, h), u, v, u + 1f / s.cols(), v + 1f / rows, 255, 255, 255, a);
                    }
                }
            }
            image(m, group.glass());
            image(m, group.matte());
        }
        if (overlay != null) rect(m, overlay, 0, 0, scene.width(), scene.height(), 1);
    }

    void close() {
        textures.values().forEach(TextureUtil::releaseTextureId);
        textures.clear();
        scene = null;
    }

    private void belt(Matrix4f m, FactoryScene.Belt b, float seconds) {
        float offset = (float) -((seconds * (double) b.speed()) % 1.0);
        int[] c = b.rgba();
        float[] xy = { b.u0v0()[0], b.u0v0()[1], b.u1v0()[0], b.u1v0()[1], b.u0v1()[0], b.u0v1()[1], b.u1v1()[0], b.u1v1()[1] };
        quad(m, scene.chevron(), xy, offset, 0, offset + b.len(), 1, c[0], c[1], c[2], c[3] / 255f);
    }

    private void image(Matrix4f m, FactoryScene.Image i) {
        if (i != null) rect(m, i.file(), i.x(), i.y(), i.w(), i.h(), 1);
    }

    private void rect(Matrix4f m, String file, float x, float y, float w, float h, float alpha) {
        quad(m, file, corners(x, y, w, h), 0, 0, 1, 1, 255, 255, 255, alpha);
    }

    private static float[] corners(float x, float y, float w, float h) {
        return new float[] { x, y, x + w, y, x, y + h, x + w, y + h };
    }

    private void quad(Matrix4f m, String file, float[] xy, float u0, float v0, float u1, float v1, int r, int gr, int b, float alpha) {
        RenderSystem.setShaderTexture(0, textures.get(file));
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int a = Math.round(alpha * 255);
        buffer.addVertex(m, xy[0], xy[1], 0).setUv(u0, v0).setColor(r, gr, b, a);
        buffer.addVertex(m, xy[4], xy[5], 0).setUv(u0, v1).setColor(r, gr, b, a);
        buffer.addVertex(m, xy[6], xy[7], 0).setUv(u1, v1).setColor(r, gr, b, a);
        buffer.addVertex(m, xy[2], xy[3], 0).setUv(u1, v0).setColor(r, gr, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private void load() {
        if (scene != null) return;
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        scene = FactoryScene.parse(new String(bytes(resources, "scene.json"), StandardCharsets.UTF_8));
        texture(resources, scene.still(), false);
        texture(resources, scene.chevron(), true);
        scene.sprites().values().forEach(s -> texture(resources, s.file(), false));
        for (FactoryScene.Group g : scene.groups()) {
            if (g.glass() != null) texture(resources, g.glass().file(), false);
            if (g.matte() != null) texture(resources, g.matte().file(), false);
        }
        overlay = "overlay.png";
        texture(resources, overlay, false);
    }

    private static byte[] bytes(ResourceManager resources, String file) {
        try (InputStream in = resources.open(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, DIR + file))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PNG or JPEG (the still), decoded by STB: NativeImage only reads PNG. Mipmapped, linear. */
    private void texture(ResourceManager resources, String file, boolean repeat) {
        if (textures.containsKey(file)) return;
        byte[] encoded = bytes(resources, file);
        ByteBuffer in = MemoryUtil.memAlloc(encoded.length).put(encoded).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(in, w, h, c, 4);
            if (pixels == null) throw new IllegalStateException("Cannot decode " + file + ": " + STBImage.stbi_failure_reason());
            int levels = 31 - Integer.numberOfLeadingZeros(Math.max(w.get(0), h.get(0)));
            int id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(id, levels, w.get(0), h.get(0));
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w.get(0), h.get(0), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, MemoryUtil.memAddress(pixels));
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            STBImage.stbi_image_free(pixels);
            textures.put(file, id);
        } finally {
            MemoryUtil.memFree(in);
        }
    }
}
