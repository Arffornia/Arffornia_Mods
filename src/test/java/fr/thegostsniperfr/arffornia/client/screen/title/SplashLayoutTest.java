package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SplashLayoutTest {
    private static int sixPixelGlyphs(String s) {
        return s.length() * 6;
    }

    @Test
    void keepsShortMotdsOnOneLine() {
        assertEquals(List.of("Season 2 is live"), SplashLayout.lines("Season 2 is live"));
    }

    @Test
    void splitsLongMotdsAtTheSpaceNearestTheMiddle() {
        assertEquals(List.of("Your factory is not", "big enough. Yet."), SplashLayout.lines("Your factory is not big enough. Yet."));
    }

    @Test
    void neverGetsWiderThan380DesignPixels() {
        var layout = SplashLayout.layout("Welcome to Arffornia, the factory server", SplashLayoutTest::sixPixelGlyphs, 250);

        int widest = layout.lines().stream().mapToInt(SplashLayoutTest::sixPixelGlyphs).max().orElseThrow();
        assertTrue(widest * layout.scale() <= SplashLayout.MAX_WIDTH + 0.01f, () -> "width " + widest * layout.scale());
    }

    @Test
    void pulsesBetweenVanillaBounds() {
        assertEquals(1.8f, SplashLayout.pulse(0), 1e-4);
        assertEquals(1.7f, SplashLayout.pulse(250), 1e-4);
    }
}
