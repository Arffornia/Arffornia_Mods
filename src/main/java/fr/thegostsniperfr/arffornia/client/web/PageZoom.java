package fr.thegostsniperfr.arffornia.client.web;

/**
 * Page zoom for the progression viewer. The browser surface is the framebuffer size, so without zoom the page renders
 * at one CSS px per screen pixel and is tiny on high resolution screens. Zoom keeps text crisp: Chromium lays out
 * larger and still rasterizes at the full framebuffer resolution.
 * <p>
 * The factor follows the GUI scale at half rate (GUI scale 2, the 1080p default, is the page's native size), and is
 * capped so the page always keeps at least {@link #MIN_CSS_WIDTH} x {@link #MIN_CSS_HEIGHT} CSS px: the viewer is a
 * desktop layout (header, stage cards, graph, legend) and one CSS px per GUI px left it a few nodes on screen.
 */
final class PageZoom {
    static final int MIN_CSS_WIDTH = 1280;
    static final int MIN_CSS_HEIGHT = 720;

    private PageZoom() {
    }

    static double factor(double guiScale, int framebufferWidth, int framebufferHeight) {
        double roomCap = Math.min((double) framebufferWidth / MIN_CSS_WIDTH, (double) framebufferHeight / MIN_CSS_HEIGHT);
        return Math.max(1.0, Math.min(guiScale / 2.0, roomCap));
    }

    /** Chromium's zoom level is log base 1.2 of the factor. */
    static double level(double factor) {
        return Math.log(factor) / Math.log(1.2);
    }
}
