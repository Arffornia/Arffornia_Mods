package fr.thegostsniperfr.arffornia.client.screen.title;

import net.minecraft.client.gui.GuiGraphics;

/** The tile icons of the mock-up (24x24 line icons), drawn as strokes so they stay sharp at any scale. */
final class TitleIcons {
    private TitleIcons() {}

    static void draw(GuiGraphics g, String id, float cx, float cy, float size, int colour) {
        var pose = g.pose();
        pose.pushPose();
        float s = size / 24f;
        pose.translate(cx - size / 2, cy - size / 2, 0);
        pose.scale(s, s, 1);
        float w = 2f;
        switch (id) {
            case "single" -> {
                TitlePainter.polyline(g, w, colour, 12, 3, 20, 7.5f, 20, 16.5f, 12, 21, 4, 16.5f, 4, 7.5f, 12, 3);
                TitlePainter.polyline(g, w, colour, 4, 7.5f, 12, 12, 20, 7.5f);
                TitlePainter.polyline(g, w, colour, 12, 12, 12, 21);
            }
            case "mods" -> {
                TitlePainter.polyline(g, w, colour, 4, 4, 11, 4, 11, 11, 4, 11, 4, 4);
                TitlePainter.polyline(g, w, colour, 13, 4, 20, 4, 20, 11, 13, 11, 13, 4);
                TitlePainter.polyline(g, w, colour, 4, 13, 11, 13, 11, 20, 4, 20, 4, 13);
                TitlePainter.polyline(g, w, colour, 16.5f, 13, 16.5f, 20);
                TitlePainter.polyline(g, w, colour, 13, 16.5f, 20, 16.5f);
            }
            case "options" -> {
                TitlePainter.polyline(g, w, colour, 4, 7, 14, 7);
                TitlePainter.polyline(g, w, colour, 18, 7, 20, 7);
                TitlePainter.polyline(g, w, colour, 4, 17, 8, 17);
                TitlePainter.polyline(g, w, colour, 12, 17, 20, 17);
                TitlePainter.circle(g, 16, 7, 2, w, colour);
                TitlePainter.circle(g, 10, 17, 2, w, colour);
            }
            case "quit" -> {
                TitlePainter.polyline(g, w, colour, 14, 4, 19, 4, 19, 20, 14, 20);
                TitlePainter.polyline(g, w, colour, 10, 8, 6, 12, 10, 16);
                TitlePainter.polyline(g, w, colour, 6, 12, 16, 12);
            }
            case "news" -> {
                TitlePainter.polyline(g, w, colour, 5, 5, 19, 5, 19, 19, 5, 19, 5, 5);
                TitlePainter.polyline(g, w, colour, 8, 9, 16, 9);
                TitlePainter.polyline(g, w, colour, 8, 12, 16, 12);
                TitlePainter.polyline(g, w, colour, 8, 15, 13, 15);
            }
            case "mile" -> {
                TitlePainter.polyline(g, w, colour, 6, 21, 6, 4);
                TitlePainter.polyline(g, w, colour, 6, 4, 17, 4, 15, 8, 17, 12, 6, 12);
            }
            default -> {}
        }
        pose.popPose();
    }
}
