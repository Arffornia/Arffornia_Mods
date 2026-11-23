package fr.thegostsniperfr.arffornia.client.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageZoomTest {
    // "auto" GUI scale is the largest scale keeping 320x240 GUI px: 4 at 1080p, 6 at 1440p, 9 at 4K.
    @ParameterizedTest(name = "{1}x{2} at GUI scale {0} zooms {3}")
    @CsvSource({
            "2, 1920, 1080, 1.0",
            "3, 1920, 1080, 1.5",
            "4, 1920, 1080, 1.5",
            "2, 2560, 1440, 1.0",
            "3, 2560, 1440, 1.5",
            "4, 2560, 1440, 2.0",
            "6, 2560, 1440, 2.0",
            "2, 3840, 2160, 1.0",
            "3, 3840, 2160, 1.5",
            "4, 3840, 2160, 2.0",
            "9, 3840, 2160, 3.0",
    })
    void zoomFollowsHalfTheGuiScaleWithinTheRoomOfTheScreen(double guiScale, int width, int height, double expected) {
        assertEquals(expected, PageZoom.factor(guiScale, width, height), 1e-9);
    }

    @ParameterizedTest(name = "{1}x{2} at GUI scale {0}")
    @CsvSource({"9, 3840, 2160", "6, 2560, 1440", "4, 1920, 1080", "4, 3840, 1600"})
    void thePageAlwaysKeepsADesktopLayoutSpace(double guiScale, int width, int height) {
        double factor = PageZoom.factor(guiScale, width, height);

        assertTrue(width / factor >= PageZoom.MIN_CSS_WIDTH - 1e-6 && height / factor >= PageZoom.MIN_CSS_HEIGHT - 1e-6);
    }

    @Test
    void aSmallWindowIsNeverZoomedOut() {
        assertEquals(1.0, PageZoom.factor(4, 1280, 600));
    }

    @Test
    void theLevelIsChromiumsLogBase12() {
        assertEquals(2.0, Math.pow(1.2, PageZoom.level(2.0)), 1e-9);
    }
}
