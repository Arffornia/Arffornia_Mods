package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.config.ApiConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Arffornia's title screen (spec 007 D1b): the honeycomb menu in the launcher's colours over the baked
 * night factory, the banner wordmark, the MOTD as vanilla's splash and keyboard navigation. Laid out in
 * the mock-up's 1920x1080 design pixels and scaled to fit.
 */
public class ArfTitleScreen extends Screen {
    static final ResourceLocation LOGO = rl("textures/gui/title/logo.png");
    static final ResourceLocation ELEVATOR = rl("textures/gui/title/space_elevator.png");
    static final String DISPLAY_FONT = "display", UI_FONT = "ui", LABEL_FONT = "label", BODY_FONT = "body", STRONG_FONT = "strong";
    private static final int DESIGN_W = 1920, DESIGN_H = 1080;
    private static final float HEX_W = 250, HEX_H = 216.5f, CX = 1440, CY = 560;
    private static final int INK = 0xffffff, MUTED = 0xb3a7c2, TEAL = 0x4fd1c5, GREEN = 0x42d392, HOT = 0xff7300, T1 = 0xffb36b, T2 = 0xe0703c;
    private static final float SUBTITLE_MAX_W = 150, SUBTITLE_LINE = 19, BAR_W = 96, BAR_H = 4, BAR_GAP = 6;

    record Tile(String id, int q, int r, float x, float y) {}

    private final List<Tile> tiles = new ArrayList<>();
    private final List<float[]> ghosts = new ArrayList<>();
    private final long openedAt = Util.getMillis();
    private final TitleBackground background = new TitleBackground();
    private final MotdStore motdStore = new MotdStore(FMLPaths.GAMEDIR.get());
    private final NewsStore newsStore = new NewsStore(FMLPaths.GAMEDIR.get());
    private TitleStatus status;
    private String current = "play", previous = "play";
    private long movedAt = -10_000, pressedAt = -10_000;
    private float k = 1, ox, oy;
    private boolean cursorHidden;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;

    public ArfTitleScreen() {
        super(Component.translatable("gui.arffornia.title.narration"));
        int[][] layout = { { 0, 0 }, { 0, -1 }, { 1, -1 }, { 1, 0 }, { 0, 1 }, { -1, 1 }, { -1, 0 } };
        String[] ids = { "play", "single", "mile", "mods", "options", "quit", "news" };
        for (int i = 0; i < ids.length; i++) {
            float[] p = axial(layout[i][0], layout[i][1]);
            tiles.add(new Tile(ids[i], layout[i][0], layout[i][1], p[0], p[1]));
        }
        for (int n = 4; n >= 2; n--) {
            for (int[] qr : ring(n)) {
                float[] p = axial(qr[0], qr[1]);
                if (p[0] >= 980) ghosts.add(p);
            }
        }
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, path);
    }

    private static float[] axial(int q, int r) {
        return new float[] { CX + q * 0.75f * HEX_W, CY + (r + q / 2f) * HEX_H };
    }

    private static List<int[]> ring(int n) {
        int[][] dirs = { { 1, 0 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { 0, -1 }, { 1, -1 } };
        List<int[]> out = new ArrayList<>();
        int q = 0, r = -n;
        for (int[] d : dirs) {
            for (int i = 0; i < n; i++) {
                out.add(new int[] { q, r });
                q += d[0];
                r += d[1];
            }
        }
        return out;
    }

    @Override
    protected void init() {
        if (status == null) status = new TitleStatus(minecraft, motdStore, newsStore);
        k = Math.min(width / (float) DESIGN_W, height / (float) DESIGN_H);
        ox = (width - DESIGN_W * k) / 2f;
        oy = (height - DESIGN_H * k) / 2f;
    }

    @Override
    public void tick() {
        status.tick();
    }

    @Override
    public void removed() {
        status.close();
        background.close();
        showCursor();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- input

    private double designX(double mouseX) {
        return (mouseX - ox) / k;
    }

    private double designY(double mouseY) {
        return (mouseY - oy) / k;
    }

    @Nullable
    private Tile tileAt(double mouseX, double mouseY) {
        double x = designX(mouseX), y = designY(mouseY);
        return tiles.stream().filter(t -> TitlePainter.insideHex(x, y, t.x(), t.y(), HEX_W, HEX_H)).findFirst().orElse(null);
    }

    private void select(String id) {
        if (!id.equals(current)) {
            previous = current;
            current = id;
            movedAt = Util.getMillis();
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            if (!Double.isNaN(lastMouseX)) showCursor();
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            Tile t = tileAt(mouseX, mouseY);
            if (t != null && !cursorHidden) select(t.id());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Tile t = tileAt(mouseX, mouseY);
            if (t != null) {
                select(t.id());
                activate(t.id());
                return true;
            }
            if (designY(mouseY) > 1020 && designX(mouseX) > 1500) {
                minecraft.setScreen(new CreditsAndAttributionScreen(this));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        TitleNavigation.Arrow arrow = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> TitleNavigation.Arrow.UP;
            case GLFW.GLFW_KEY_DOWN -> TitleNavigation.Arrow.DOWN;
            case GLFW.GLFW_KEY_LEFT -> TitleNavigation.Arrow.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> TitleNavigation.Arrow.RIGHT;
            default -> null;
        };
        if (arrow != null) {
            hideCursor();
            select(TitleNavigation.neighbour(tiles.stream().map(t -> new TitleNavigation.Node(t.id(), t.x(), t.y())).toList(), current, arrow));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            hideCursor();
            activate(current);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void hideCursor() {
        if (!cursorHidden) {
            cursorHidden = true;
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    private void showCursor() {
        if (cursorHidden) {
            cursorHidden = false;
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    private void activate(String id) {
        pressedAt = Util.getMillis();
        switch (id) {
            case "play" -> {
                ServerData server = status.server();
                if (server == null) {
                    minecraft.setScreen(new JoinMultiplayerScreen(this));
                } else {
                    ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(server.ip), server, false, null);
                }
            }
            case "single" -> minecraft.setScreen(new SelectWorldScreen(this));
            case "mile" -> ConfirmLinkScreen.confirmLinkNow(this, websiteRoot() + "/progression/" + minecraft.getUser().getName());
            case "mods" -> minecraft.setScreen(new ModListScreen(this));
            case "options" -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case "quit" -> minecraft.stop();
            case "news" -> {
                ArfforniaApiDtos.NewsHeadline headline = status.news();
                ConfirmLinkScreen.confirmLinkNow(this, headline != null ? headline.url() : websiteRoot() + "/news");
            }
            default -> {}
        }
    }

    /** The website serves the API under /api: its pages are the same origin without it. */
    static String websiteRoot() {
        String api = ApiConfig.API_BASE_URL.get().replaceAll("/+$", "");
        return api.endsWith("/api") ? api.substring(0, api.length() - 4) : api;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float t = (now - openedAt) / 1000f;
        Tile target = tile(current), from = tile(previous);
        float slide = easeOutBack(clamp((now - movedAt) / 240f));
        float sx = from.x() + (target.x() - from.x()) * slide, sy = from.y() + (target.y() - from.y()) * slide;

        drawBackground(g, sx, sy, t);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ox, oy, 0);
        pose.scale(k, k, 1);
        for (float[] p : ghosts) {
            float a = clamp((t - 0.2f) / 0.4f);
            TitlePainter.hex(g, p[0], p[1], HEX_W, HEX_H, TitlePainter.alpha(0.10f * a, MUTED), TitlePainter.alpha(0.10f * a, MUTED));
            TitlePainter.hex(g, p[0], p[1], HEX_W - 4.6f, HEX_H - 4, TitlePainter.alpha(0.25f * a, 0x160020), TitlePainter.alpha(0.25f * a, 0x160020));
        }
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            float appear = clamp((t - 0.2f - (tile.id().equals("play") ? 0 : 0.12f) - i * 0.04f) / 0.2f);
            drawPlate(g, tile, appear);
        }
        drawSelector(g, sx, sy, now, t);
        for (Tile tile : tiles) drawLabel(g, tile, tile.id().equals(current));
        drawBrand(g, t);
        drawFooter(g);
        pose.popPose();
    }

    private Tile tile(String id) {
        return tiles.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    private void drawBackground(GuiGraphics g, float sx, float sy, float t) {
        int bw = TitleBackground.WIDTH, bh = TitleBackground.HEIGHT;
        float cover = Math.max(width / (float) bw, height / (float) bh) * 1.02f;
        float w = bw * cover, h = bh * cover;
        // The factory slides a little against the selection: depth without a camera move.
        float px = -(sx - CX) / 190f * 10f * k, py = -(sy - CY) / 216f * 6f * k;
        var pose = g.pose();
        pose.pushPose();
        pose.translate((width - w) / 2f + px, (height - h) / 2f + py, 0);
        pose.scale(cover, cover, 1);
        var layers = background.at(t);
        g.blit(layers.current(), 0, 0, bw, bh, 0, 0, bw, bh, bw, bh);
        if (layers.next() != null && layers.mix() > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1, 1, 1, layers.mix());
            g.blit(layers.next(), 0, 0, bw, bh, 0, 0, bw, bh, bw, bh);
            g.setColor(1, 1, 1, 1);
        }
        pose.popPose();
    }

    private void drawPlate(GuiGraphics g, Tile tile, float appear) {
        if (appear <= 0) return;
        boolean play = tile.id().equals("play");
        var pose = g.pose();
        pose.pushPose();
        float s = 0.86f + 0.14f * appear;
        pose.translate(tile.x(), tile.y(), 0);
        pose.scale(s, s, 1);
        TitlePainter.hex(g, 0, 18, HEX_W, HEX_H, TitlePainter.alpha(0.35f * appear, 0), TitlePainter.alpha(0.0f, 0));
        TitlePainter.hex(g, 0, 10, HEX_W, HEX_H, TitlePainter.alpha(appear, 0x120a19), TitlePainter.alpha(appear, 0x07040a));
        if (play) {
            TitlePainter.hex(g, 0, 0, HEX_W, HEX_H, TitlePainter.alpha(appear, T1), TitlePainter.alpha(appear, 0xa34400));
        } else {
            TitlePainter.hex(g, 0, 0, HEX_W, HEX_H, TitlePainter.alpha(0.62f * appear, 0xd6c4ec), TitlePainter.alpha(0.85f * appear, 0x0a0410));
        }
        TitlePainter.hex(g, 0, 0, HEX_W - 7, HEX_H - 6, TitlePainter.alpha(appear, 0x2f2140), TitlePainter.alpha(appear, 0x1a1025));
        TitlePainter.hexRing(g, 0, 0, HEX_W - 20.8f, HEX_H - 18, 1, play ? TitlePainter.alpha(0.7f * appear, T1) : TitlePainter.alpha(0.10f * appear, INK));
        pose.popPose();
    }

    private void drawSelector(GuiGraphics g, float x, float y, long now, float t) {
        float intro = clamp((t - 0.2f) / 0.25f);
        if (intro <= 0) return;
        float pressK = clamp((now - pressedAt) / 160f);
        boolean pressing = pressK < 1;
        float squash = pressing ? 1 - 0.03f * (float) Math.sin(pressK * Math.PI) : 1;
        float grow = 1 + 0.04f * (1 - (float) Math.pow(1 - clamp((now - movedAt - 120) / 120f), 3));
        float lift = -6 * (1 - (pressing ? (float) Math.sin(pressK * Math.PI) : 0));
        float s = (0.86f + 0.14f * easeOutBack(intro)) * squash * grow;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + lift, 0);
        pose.scale(s, s, 1);
        float glow = 0.75f + 0.25f * (float) Math.sin(t * 3);
        TitlePainter.disc(g, 0, 0, 170, TitlePainter.alpha(0.35f * glow * intro, HOT), TitlePainter.alpha(0, HOT));
        TitlePainter.hex(g, 0, 18, HEX_W, HEX_H, TitlePainter.alpha(0.45f * intro, 0), TitlePainter.alpha(0, 0));
        TitlePainter.hex(g, 0, 10 - lift, HEX_W, HEX_H, TitlePainter.alpha(intro, 0x9a3c00), TitlePainter.alpha(intro, 0x5a2300));
        TitlePainter.hex(g, 0, 0, HEX_W, HEX_H, TitlePainter.alpha(intro, 0xffc790), TitlePainter.alpha(intro, 0xb04800));
        TitlePainter.hex(g, 0, 0, HEX_W - 7, HEX_H - 6, TitlePainter.alpha(intro, 0xff8f2e), TitlePainter.alpha(intro, 0xe86400));
        TitlePainter.hexRing(g, 0, 0, HEX_W - 20.8f, HEX_H - 18, 1, TitlePainter.alpha(0.45f * intro, 0xffe6c8));
        TitlePainter.hex(g, 0, -HEX_H / 4, HEX_W * 0.75f, HEX_H / 2, TitlePainter.alpha(0.18f * intro, INK), TitlePainter.alpha(0, INK));
        pose.popPose();
    }

    private void drawLabel(GuiGraphics g, Tile tile, boolean on) {
        float x = tile.x(), y = tile.y();
        int small = on ? TitlePainter.alpha(0.88f, INK) : TitlePainter.argb(255, MUTED);
        switch (tile.id()) {
            case "play" -> {
                TitlePainter.disc(g, x, y - 34, 48, TitlePainter.alpha(0.78f, 0x14081e), TitlePainter.alpha(0, 0x14081e));
                g.blit(ELEVATOR, (int) x - 50, (int) y - 84, 100, 100, 0, 0, 200, 200, 200, 200);
                text(g, tr("gui.arffornia.title.play"), STRONG_FONT, 30, x, y + 22, TitlePainter.argb(255, INK), 0, Align.CENTER);
                text(g, playSubtitle(), BODY_FONT, 15, x, y + 58, TitlePainter.alpha(0.88f, INK), 0, Align.CENTER);
            }
            case "single" -> iconAndTitle(g, tile, "menu.singleplayer", List.of(), 0, small);
            case "mile" -> drawMilestones(g, tile, small);
            case "mods" -> iconAndTitle(g, tile, "fml.menu.mods", List.of(Component.translatable("gui.arffornia.title.mods.loaded", ModList.get().size())), 0, small);
            case "options" -> iconAndTitle(g, tile, "menu.options", List.of(), 0, small);
            case "quit" -> iconAndTitle(g, tile, "menu.quit", List.of(), 0, small);
            case "news" -> {
                ArfforniaApiDtos.NewsHeadline headline = status.news();
                List<FormattedText> lines = headline == null
                        ? List.of(Component.translatable("gui.arffornia.title.news.open"))
                        : TextWrap.wrap(headline.title(), s -> textWidth(s, BODY_FONT, 15, 0), SUBTITLE_MAX_W, 2).stream().map(FormattedText::of).toList();
                iconAndTitle(g, tile, "gui.arffornia.title.news", lines, 0, small);
            }
            default -> {}
        }
    }

    private void drawMilestones(GuiGraphics g, Tile tile, int smallColour) {
        ArfforniaApiDtos.ProgressionState p = status.progression();
        StageProgress stage = p == null ? null : StageProgress.of(p).orElse(null);
        if (stage == null) {
            List<FormattedText> lines = p == null ? List.of() : List.of(Component.translatable("gui.arffornia.title.milestones.done", p.completed().size()));
            iconAndTitle(g, tile, "gui.arffornia.title.milestones", lines, 0, smallColour);
            return;
        }
        Component count = Component.translatable("gui.arffornia.title.milestones.count", stage.done()).withColor(TEAL);
        float bottom = iconAndTitle(g, tile, "gui.arffornia.title.milestones",
                List.of(Component.translatable("gui.arffornia.title.milestones.stage", count, stage.required())), BAR_GAP + BAR_H, smallColour);
        float left = tile.x() - BAR_W / 2, top = bottom + BAR_GAP;
        TitlePainter.roundedBar(g, left, top, BAR_W, BAR_H, TitlePainter.alpha(0.25f, MUTED));
        if (stage.fraction() > 0) TitlePainter.roundedBar(g, left, top, BAR_W * stage.fraction(), BAR_H, TitlePainter.argb(255, TEAL));
    }

    /**
     * Icon, title and subtitle lines centred as one block on the tile, {@code extra} design pixels kept
     * free below them; returns the bottom of the last line.
     */
    private float iconAndTitle(GuiGraphics g, Tile tile, String titleKey, List<? extends FormattedText> subtitle, float extra, int smallColour) {
        float height = 28 + (subtitle.isEmpty() ? 0 : 24 + SUBTITLE_LINE * (subtitle.size() - 1)) + extra;
        float y = tile.y() - height / 2;
        TitleIcons.draw(g, tile.id(), tile.x(), y - 22, 34, TitlePainter.alpha(0.9f, INK));
        text(g, tr(titleKey), LABEL_FONT, 23, tile.x(), y + 4, TitlePainter.argb(255, INK), 0, Align.CENTER);
        float lineTop = y + 38;
        for (FormattedText line : subtitle) {
            text(g, line, BODY_FONT, 15, tile.x(), lineTop, smallColour, Align.CENTER);
            lineTop += SUBTITLE_LINE;
        }
        return lineTop - SUBTITLE_LINE + 15;
    }

    private String playSubtitle() {
        return switch (status.serverState()) {
            case ONLINE, CHECKING -> tr("gui.arffornia.title.play.on", status.server().name);
            case OFFLINE -> tr("gui.arffornia.title.server.offline");
            case NONE -> tr("gui.arffornia.title.play.choose");
        };
    }

    private void drawBrand(GuiGraphics g, float t) {
        float lift = 16 * (1 - easeOut(clamp((t - 0.1f) / 0.8f)));
        float a = clamp((t - 0.1f) / 0.5f);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, lift, 0);
        text(g, tr("gui.arffornia.brand.eyebrow", SharedConstants.getCurrentVersion().getName()), UI_FONT, 20, 120, 196, TitlePainter.alpha(a, T1), 3, Align.LEFT);
        g.setColor(1, 1, 1, a);
        g.blit(LOGO, 120, 236, 104, 104, 0, 0, 208, 208, 208, 208);
        g.setColor(1, 1, 1, 1);
        float wordRight = wordmark(g, 250, 246, a);
        text(g, tr("gui.arffornia.title.tagline"), UI_FONT, 32, 120, 386, TitlePainter.alpha(a, 0xe6ecef), 0, Align.LEFT);
        pose.popPose();
        drawSplash(g, wordRight, 246 + 104, t);
    }

    /** "ARFFORNIA" in the banner's gradient, letter by letter; returns its right edge. */
    private float wordmark(GuiGraphics g, float x, float top, float alpha) {
        String word = tr("gui.arffornia.brand.wordmark");
        float size = 120, tracking = 5, total = textWidth(word, DISPLAY_FONT, size, tracking);
        float cursor = x;
        for (int i = 0; i < word.length(); i++) {
            String letter = word.substring(i, i + 1);
            float w = textWidth(letter, DISPLAY_FONT, size, 0);
            int colour = TitlePainter.lerp(TitlePainter.alpha(alpha, T1), TitlePainter.alpha(alpha, T2), (cursor - x + w / 2) / total);
            text(g, letter, DISPLAY_FONT, size, cursor, top, colour, 0, Align.LEFT);
            cursor += w + tracking;
        }
        return cursor - tracking;
    }

    private void drawSplash(GuiGraphics g, float wordRight, float wordBottom, float t) {
        String motd = motdStore.read().orElseGet(() -> tr("gui.arffornia.title.tagline"));
        var layout = SplashLayout.layout(motd, font::width, Util.getMillis());
        float scale = layout.scale();
        int widest = layout.lines().stream().mapToInt(font::width).max().orElse(0);
        float half = widest * scale / 2f;
        double rad = Math.toRadians(20);
        float ax = wordRight - 6 + half * (float) Math.cos(rad), ay = wordBottom - 24 - half * (float) Math.sin(rad);
        int alpha = Math.round(clamp((t - 0.9f) / 0.3f) * 255);
        if (alpha < 4) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ax, ay, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-20));
        pose.scale(scale, scale, 1);
        float lineY = -layout.lines().size() * 9 / 2f;
        for (String line : layout.lines()) {
            g.drawString(font, line, -font.width(line) / 2, Math.round(lineY), alpha << 24 | 0xffff00, true);
            lineY += 9.5f;
        }
        pose.popPose();
    }

    private void drawFooter(GuiGraphics g) {
        int dotY = 917;
        switch (status.serverState()) {
            case ONLINE -> statusLine(g, dotY, GREEN, tr("gui.arffornia.title.server.online", status.onlinePlayers()));
            case CHECKING -> statusLine(g, dotY, MUTED, tr("gui.arffornia.title.server.checking"));
            case OFFLINE -> statusLine(g, dotY, 0xe05555, tr("gui.arffornia.title.server.offline"));
            case NONE -> {}
        }
        ArfforniaApiDtos.ProgressionState p = status.progression();
        if (p != null) {
            statusLine(g, dotY + 39, HOT, tr("gui.arffornia.title.progress", p.openStage(), p.completed().size()));
        }
        keyHints(g);
        int legal = TitlePainter.alpha(0.6f, MUTED), foot = TitlePainter.alpha(0.75f, MUTED);
        text(g, tr("gui.arffornia.brand.disclaimer"), LABEL_FONT, 12, 120, 1008, legal, 0.5f, Align.LEFT);
        String arffornia = ModList.get().getModContainerById(Arffornia.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        text(g, tr("gui.arffornia.title.version", arffornia, FMLLoader.versionInfo().neoForgeVersion()), BODY_FONT, 15, 120, 1034, foot, 0, Align.LEFT);
        text(g, tr("title.credits"), BODY_FONT, 15, 1880, 1034, foot, 0, Align.RIGHT);
    }

    private void statusLine(GuiGraphics g, float centreY, int colour, String label) {
        TitlePainter.disc(g, 126, centreY, 10, TitlePainter.alpha(0.18f, colour), TitlePainter.alpha(0.18f, colour));
        TitlePainter.disc(g, 126, centreY, 6, TitlePainter.argb(255, colour), TitlePainter.argb(255, colour));
        text(g, label, BODY_FONT, 22, 146, centreY - 13, TitlePainter.argb(255, INK), 0, Align.LEFT);
    }

    private void keyHints(GuiGraphics g) {
        float y = 924, x = 1440 - 172;
        for (String key : new String[] { "↑", "↓", "←", "→" }) x = keycap(g, key, x, y) + 10;
        text(g, tr("gui.arffornia.title.keys.move"), BODY_FONT, 16, x, y - 10, TitlePainter.alpha(0.8f, MUTED), 0, Align.LEFT);
        x += textWidth(tr("gui.arffornia.title.keys.move"), BODY_FONT, 16, 0) + 20;
        x = keycap(g, tr("key.keyboard.enter"), x, y) + 10;
        text(g, tr("gui.arffornia.title.keys.select"), BODY_FONT, 16, x, y - 10, TitlePainter.alpha(0.8f, MUTED), 0, Align.LEFT);
    }

    private float keycap(GuiGraphics g, String label, float x, float centreY) {
        float w = Math.max(30, textWidth(label, STRONG_FONT, 15, 0) + 14);
        int left = Math.round(x), top = Math.round(centreY - 15), right = Math.round(x + w), bottom = Math.round(centreY + 15);
        g.fill(left, top, right, bottom, TitlePainter.alpha(0.5f, MUTED));
        g.fill(left + 1, top + 1, right - 1, bottom - 3, TitlePainter.alpha(0.9f, 0x16001f));
        text(g, label, STRONG_FONT, 15, x + w / 2, centreY - 10, TitlePainter.argb(255, INK), 0, Align.CENTER);
        return x + w;
    }

    // ---------------------------------------------------------------- text

    enum Align { LEFT, CENTER, RIGHT }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** The family's tier for text {@code size} design pixels tall, at the window's current physical scale. */
    private ResourceLocation fontFor(String family, float size) {
        return TitleFonts.font(family, size * k * (float) minecraft.getWindow().getGuiScale());
    }

    /**
     * Width in GUI units from the glyph advances, unrounded. Not {@code Font.width}: it rounds up, and mods
     * such as Emojiful replace it with the width of the plain string in Minecraft's default font.
     */
    private float advance(FormattedCharSequence text) {
        return font.getSplitter().stringWidth(text);
    }

    private FormattedCharSequence sequence(String text, ResourceLocation fontId) {
        return Language.getInstance().getVisualOrder(FormattedText.of(text, Style.EMPTY.withFont(fontId)));
    }

    private float textWidth(String text, String family, float size, float tracking) {
        ResourceLocation fontId = fontFor(family, size);
        float scale = size / TitleFonts.EM;
        if (tracking == 0) return advance(sequence(text, fontId)) * scale;
        float w = 0;
        for (int i = 0; i < text.length(); i++) w += advance(sequence(text.substring(i, i + 1), fontId)) * scale + tracking;
        return text.isEmpty() ? 0 : w - tracking;
    }

    /** Draws {@code text} with the top of its em box at {@code top}, {@code size} design pixels tall. */
    private void text(GuiGraphics g, String text, String family, float size, float x, float top, int argb, float tracking, Align align) {
        if ((argb >>> 24) < 4) return;
        ResourceLocation fontId = fontFor(family, size);
        float width = textWidth(text, family, size, tracking);
        float left = switch (align) {
            case LEFT -> x;
            case CENTER -> x - width / 2;
            case RIGHT -> x - width;
        };
        float scale = size / TitleFonts.EM;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        if (tracking == 0) {
            g.drawString(font, sequence(text, fontId), 0, 0, argb, false);
        } else {
            float cursor = 0;
            for (int i = 0; i < text.length(); i++) {
                FormattedCharSequence c = sequence(text.substring(i, i + 1), fontId);
                pose.pushPose();
                pose.translate(cursor, 0, 0);
                g.drawString(font, c, 0, 0, argb, false);
                pose.popPose();
                cursor += advance(c) + tracking / scale;
            }
        }
        pose.popPose();
    }

    /** A line that may carry its own colours (no tracking): unstyled parts take {@code argb}. */
    private void text(GuiGraphics g, FormattedText line, String family, float size, float x, float top, int argb, Align align) {
        if ((argb >>> 24) < 4) return;
        ResourceLocation id = fontFor(family, size);
        FormattedText styled = line instanceof Component c
                ? Component.empty().append(c).withStyle(s -> s.withFont(id))
                : FormattedText.of(line.getString(), Style.EMPTY.withFont(id));
        float scale = size / TitleFonts.EM;
        float width = font.getSplitter().stringWidth(styled) * scale;
        float left = switch (align) {
            case LEFT -> x;
            case CENTER -> x - width / 2;
            case RIGHT -> x - width;
        };
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, Language.getInstance().getVisualOrder(styled), 0, 0, argb, false);
        pose.popPose();
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static float easeOut(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.4f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }
}
