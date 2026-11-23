package fr.thegostsniperfr.arffornia.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EarlyClipTest {
    @Test
    void lastFrameBlendsIntoTheFirst_soTheLoopHasNoSeam() {
        var blend = EarlyClip.frames(79.5f / 12f, 12f, 80);

        assertEquals(new EarlyClip.Blend(79, 0, 0.5f), blend);
    }

    @Test
    void clipRestartsAfterOneLoop() {
        var blend = EarlyClip.frames(80.25f / 12f + 20f / 3f, 12f, 80);

        assertEquals(0, blend.current());
    }

    @Test
    void mixIsTheFractionBetweenTwoFrames() {
        var blend = EarlyClip.frames(0.125f, 12f, 80);

        assertEquals(1, blend.current());
        assertEquals(0.5f, blend.mix(), 1e-4f);
    }
}
