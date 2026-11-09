package fr.thegostsniperfr.arffornia.client.ui.band;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vfyjxf.taffy.style.TaffyPosition;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.ui.ArfTokens;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Native renderer of the stage band with LDLib2 (spec 018 prototype). Only touched when LDLib2 is installed. */
public final class NativeBandScreens {
    private static final ResourceLocation STYLESHEET = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "lss/band.lss");
    private static final int LINE = ArfTokens.DARK_SURFACE_RAISED;
    private static final int LINE_SOFT = 0xFF2A2135;
    // The SDF shader drops near-transparent fills, so the 3 % / 7 % white columns and the 60 % teal links
    // of the web page are pre-blended over the canvas colour.
    private static final int COLUMN = 0xFF19151E;
    private static final int COLUMN_CURRENT = 0xFF221F27;
    private static final int LINK_DONE = 0xFF23837D;
    private static final int ON_ACCENT = ArfTokens.DARK_ON_ACCENT;
    private static final float PULSE_SECONDS = 1.6f;

    private NativeBandScreens() {
    }

    public static Screen create() {
        StageBand band = BandBench.band();
        var window = Minecraft.getInstance().getWindow();
        float guiScale = (float) window.getGuiScale();

        UIElement page = new UIElement().addClass("page")
                .layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(window.getWidth()).height(window.getHeight()))
                .transform(t -> t.pivot(0, 0).scale(1 / guiScale));
        page.addChildren(header(band), new UIElement().addClass("separator"), canvas(band));

        UIElement root = new UIElement().layout(l -> l.width(window.getGuiScaledWidth()).height(window.getGuiScaledHeight()));
        root.setOverflowVisible(true);
        root.addChild(page);
        return new TimedScreen(ModularUI.of(UI.of(root, STYLESHEET)));
    }

    private static UIElement header(StageBand band) {
        StageBand.Stage stage = band.header().stage();
        StageBand.Elevator elevator = band.header().elevator();
        int left = stage.needed() - stage.done();

        UIElement brand = new UIElement().addClass("brand").addChildren(
                label("brand-title", tr("brand")),
                label("brand-sub", tr("brand_sub")));

        Component stageHint = left > 0
                ? tr("hint_left", tr(left > 1 ? "milestone_many" : "milestone_one", left).withStyle(s -> s.withColor(ArfTokens.DARK_ACCENT & 0xFFFFFF)),
                        Math.round(stage.threshold() * 100), stage.n() + 1)
                : tr("hint_reached", Math.round(stage.threshold() * 100), stage.n() + 1);
        UIElement stageWidget = new UIElement().addClass("widget").addChildren(
                head(tr("stage", stage.n()), tr("milestones", stage.done(), stage.total())),
                new Bar((float) stage.done() / stage.total(), stage.threshold(), ArfTokens.DARK_ACCENT).addClass("bar"),
                label("hint", stageHint));

        UIElement phases = new UIElement().addClass("phases");
        for (int p = 1; p <= elevator.phases(); p++) {
            phases.addChild(new UIElement().addClasses("phase", p < elevator.phase() ? "phase-done" : p == elevator.phase() ? "phase-current" : "phase-todo"));
        }
        UIElement deliveries = new UIElement().addClass("deliveries");
        for (StageBand.Delivery d : elevator.deliveries()) {
            boolean full = d.have() >= d.need();
            deliveries.addChild(new UIElement().addClass("delivery").addChildren(
                    new UIElement().addClass("delivery-icon").style(s -> s.background(new ItemStackTexture(stack(d.i())))),
                    new UIElement().addClass("delivery-body").addChildren(
                            label("count", raw(number(d.have()) + " / " + number(d.need()))),
                            new Bar(Math.min(1f, (float) d.have() / d.need()), -1, full ? ArfTokens.DARK_SECOND : ArfTokens.DARK_ACCENT).addClass("bar"))));
        }
        UIElement elevatorWidget = new UIElement().addClass("widget").addChildren(
                head(tr("elevator"), tr("phase", elevator.phase(), elevator.phases())),
                phases, deliveries,
                label("hint", tr("elevator_hint", elevator.phase(), elevator.phase() + 1)));

        return new UIElement().addClass("header").addChildren(brand, stageWidget, elevatorWidget);
    }

    private static UIElement head(Component name, Component value) {
        return new UIElement().addClass("widget-head").addChildren(label("widget-label", name), label("widget-value", value));
    }

    private static UIElement canvas(StageBand band) {
        StageBand.Bounds b = band.bounds();
        BandView view = new BandView(b);
        view.graphViewStyle(s -> s.gridTexture(IGuiTexture.EMPTY).minScale(0.2f).maxScale(3f));
        view.addClass("canvas");
        view.addContentChild(new BandLayer(band).layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).width(b.width()).height(b.height())));
        for (StageBand.Column c : band.cols()) {
            view.addContentChild(label(c.current() ? "col-label col-label-current" : "col-label", raw(c.label()))
                    .layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(c.x() - b.x0()).top(c.y() - b.y0() + 13).width(c.w())));
        }
        for (StageBand.Node n : band.nodes()) {
            view.addContentChild(node(n, b));
        }
        UIElement legend = new UIElement().addClass("legend")
                .layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(12).bottom(12));
        for (String state : List.of("done", "available", "locked")) {
            legend.addChild(new UIElement().addClass("legend-item").addChildren(
                    new UIElement().addClasses("legend-swatch", "legend-" + state),
                    label("legend-text", tr("legend." + state))));
        }
        view.addChild(legend);
        return view;
    }

    private static UIElement node(StageBand.Node n, StageBand.Bounds b) {
        NodeElement node = new NodeElement(n.state());
        node.addClasses("node", "node-" + n.state())
                .layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(n.x() - b.x0()).top(n.y() - b.y0()));
        node.setOverflowVisible(true);
        node.style(s -> s.tooltips(raw(n.t())));
        if (n.icon() != null) {
            ItemStackTexture icon = new ItemStackTexture(stack(n.icon()));
            if (n.state().equals("locked")) {
                icon.setColor(0x66A0A0A0);
            }
            node.addChild(new UIElement().addClass("node-icon").layout(l -> l.positionType(TaffyPosition.ABSOLUTE)).style(s -> s.background(icon)));
        }
        if (n.state().equals("done")) {
            node.addChild(new Badge().addClass("badge").layout(l -> l.positionType(TaffyPosition.ABSOLUTE)));
        }
        node.addChild(label("caption", raw(n.caption())).layout(l -> l.positionType(TaffyPosition.ABSOLUTE)));
        return node;
    }

    private static Label label(String classes, Component text) {
        Label label = new Label();
        label.setText(text);
        label.addClasses(classes.split(" "));
        return label;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("screen.arffornia.band." + key, args);
    }

    private static MutableComponent raw(String text) {
        return tr("raw", text);
    }

    private static String number(int value) {
        return String.format(Locale.FRANCE, "%,d", value).replace(' ', ' ').replace(' ', ' ');
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    static void segment(GuiGraphics graphics, float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            return;
        }
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        float ex = dx / len * width / 2, ey = dy / len * width / 2;
        quad(graphics, x1 - ex + nx, y1 - ey + ny, x1 - ex - nx, y1 - ey - ny, x2 + ex - nx, y2 + ey - ny, x2 + ex + nx, y2 + ey + ny, argb);
    }

    static void quad(GuiGraphics graphics, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int argb) {
        Matrix4f m = graphics.pose().last().pose();
        VertexConsumer vc = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        RenderSystem.disableDepthTest();
        vc.addVertex(m, ax, ay, 0).setColor(argb);
        vc.addVertex(m, dx, dy, 0).setColor(argb);
        vc.addVertex(m, cx, cy, 0).setColor(argb);
        vc.addVertex(m, bx, by, 0).setColor(argb);
    }

    private static final class TimedScreen extends ModularUIScreen {
        TimedScreen(ModularUI ui) {
            super(ui, Component.translatable("screen.arffornia.band.native"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            long start = System.nanoTime();
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.flush();
            BandBench.rendered("native", System.nanoTime() - start);
        }
    }

    private static final class BandView extends GraphView {
        private final StageBand.Bounds bounds;

        BandView(StageBand.Bounds bounds) {
            this.bounds = bounds;
        }

        @Override
        protected void onLayoutChanged() {
            super.onLayoutChanged();
            fit(-8, -8, bounds.width() + 8, bounds.height() + 8, 0.2f);
        }
    }

    /** Stage columns, links and link junctions, in band coordinates; the nodes are LDLib2 elements on top. */
    private static final class BandLayer extends UIElement {
        private final StageBand band;
        private final SDFRectTexture column = new SDFRectTexture().setColor(COLUMN).setRadius(10);
        private final SDFRectTexture currentColumn = new SDFRectTexture().setColor(COLUMN_CURRENT).setRadius(10);
        private final SDFRectTexture junction = new SDFRectTexture().setColor(LINE).setRadius(3);
        private final SDFRectTexture junctionDone = new SDFRectTexture().setColor(LINK_DONE).setRadius(3);

        BandLayer(StageBand band) {
            this.band = band;
            setOverflowVisible(true);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            float ox = getPositionX() - band.bounds().x0(), oy = getPositionY() - band.bounds().y0();
            for (StageBand.Column c : band.cols()) {
                ctx.drawTexture(c.current() ? currentColumn : column, ox + c.x(), oy + c.y(), c.w(), c.h());
            }
            ctx.graphics.flush();
            GuiGraphics g = ctx.graphics;
            for (StageBand.Edge e : band.edges().stream().sorted(Comparator.comparing(StageBand.Edge::done)).toList()) {
                int color = e.done() ? LINK_DONE : LINE;
                List<float[]> pts = e.pts();
                for (int i = 1; i < pts.size(); i++) {
                    float[] p = pts.get(i - 1), q = pts.get(i);
                    boolean last = i == pts.size() - 1;
                    float qx = q[0], qy = q[1];
                    if (last) {
                        float dx = q[0] - p[0], dy = q[1] - p[1], len = (float) Math.sqrt(dx * dx + dy * dy);
                        qx -= dx / len * 10;
                        qy -= dy / len * 10;
                    }
                    segment(g, ox + p[0], oy + p[1], ox + qx, oy + qy, 2, color);
                }
                arrow(g, pts.get(pts.size() - 2), pts.get(pts.size() - 1), ox, oy, color);
            }
            g.flush();
            for (StageBand.Junction j : band.junctions()) {
                ctx.drawTexture(j.done() ? junctionDone : junction, ox + j.x() - 3, oy + j.y() - 3, 6, 6);
            }
        }

        private static void arrow(GuiGraphics g, float[] from, float[] tip, float ox, float oy, int color) {
            float dx = tip[0] - from[0], dy = tip[1] - from[1], len = (float) Math.sqrt(dx * dx + dy * dy);
            float ux = dx / len, uy = dy / len;
            float bx = tip[0] - ux * 12.6f, by = tip[1] - uy * 12.6f;
            float px = -uy * 5.6f, py = ux * 5.6f;
            quad(g, ox + bx + px, oy + by + py, ox + bx - px, oy + by - py, ox + tip[0], oy + tip[1], ox + tip[0], oy + tip[1], color);
        }
    }

    /** A milestone frame: LSS draws the frame; this adds the pulsing glow (available) and the dashed border (locked). */
    private static final class NodeElement extends UIElement {
        private final String state;
        private final SDFRectTexture glow = new SDFRectTexture().setRadius(12);

        NodeElement(String state) {
            this.state = state;
        }

        @Override
        public void drawBackgroundTexture(GUIContext ctx) {
            if (state.equals("available")) {
                float t = (System.nanoTime() / 1e9f) % PULSE_SECONDS / PULSE_SECONDS;
                float strength = 0.35f + 0.5f * (0.5f - 0.5f * (float) Math.cos(2 * Math.PI * t));
                float x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
                for (int i = 4; i >= 1; i--) {
                    int alpha = (int) (255 * strength * 0.7f * (5 - i) / 4);
                    glow.setColor(alpha << 24 | (ArfTokens.DARK_ACCENT & 0xFFFFFF)).setRadius(9 + i * 2);
                    ctx.drawTexture(glow, x - i * 2, y - i * 2, w + i * 4, h + i * 4);
                }
            }
            super.drawBackgroundTexture(ctx);
        }

        @Override
        public void drawBackgroundOverlay(GUIContext ctx) {
            super.drawBackgroundOverlay(ctx);
            if (state.equals("locked")) {
                // Item models ignore the tint, so a 60 % veil of the frame colour stands in for the web's 40 % opacity.
                ctx.graphics.flush();
                quad(ctx.graphics, getPositionX() + 8, getPositionY() + 8, getPositionX() + 36, getPositionY() + 8,
                        getPositionX() + 36, getPositionY() + 36, getPositionX() + 8, getPositionY() + 36, 0x991D1626);
                ctx.graphics.flush();
            }
        }

        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            if (!state.equals("locked")) {
                return;
            }
            float x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            GuiGraphics g = ctx.graphics;
            for (float d = 9; d < w - 9; d += 7) {
                float e = Math.min(d + 4, w - 9);
                segment(g, x + d, y + 1, x + e, y + 1, 2, LINE);
                segment(g, x + d, y + h - 1, x + e, y + h - 1, 2, LINE);
                segment(g, x + 1, y + d, x + 1, y + e, 2, LINE);
                segment(g, x + w - 1, y + d, x + w - 1, y + e, 2, LINE);
            }
            g.flush();
        }
    }

    private static final class Badge extends UIElement {
        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            float x = getPositionX() + 4, y = getPositionY() + 8;
            segment(ctx.graphics, x, y, x + 3, y + 3, 2, ON_ACCENT);
            segment(ctx.graphics, x + 3, y + 3, x + 8, y - 3, 2, ON_ACCENT);
            ctx.graphics.flush();
        }
    }

    /** A progress bar with an optional threshold tick, as the web header draws it. */
    private static final class Bar extends UIElement {
        private final float fraction;
        private final float tick;
        private final SDFRectTexture track = new SDFRectTexture().setColor(LINE_SOFT).setRadius(4);
        private final SDFRectTexture fill;

        Bar(float fraction, float tick, int color) {
            this.fraction = fraction;
            this.tick = tick;
            this.fill = new SDFRectTexture().setColor(color).setRadius(4);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            float x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            ctx.drawTexture(track, x, y, w, h);
            if (fraction > 0) {
                ctx.drawTexture(fill, x, y, Math.max(h, w * fraction), h);
            }
            if (tick >= 0) {
                ctx.graphics.flush();
                ctx.graphics.fill(Math.round(x + w * tick), Math.round(y - 4), Math.round(x + w * tick) + 2, Math.round(y + h + 4), ArfTokens.DARK_ACCENT);
            }
        }
    }
}
