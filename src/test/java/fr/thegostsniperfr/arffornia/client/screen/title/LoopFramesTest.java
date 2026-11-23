package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class LoopFramesTest {
    @Test
    void lastFrameBlendsIntoTheFirst_soTheLoopHasNoSeam() {
        var blend = LoopFrames.at(79.5f / 12f, 12f, 80);

        assertEquals(new LoopFrames(79, 0, 0.5f), blend);
    }

    @Test
    void clipRestartsAfterOneLoop() {
        var blend = LoopFrames.at(80 / 12f + 0.01f, 12f, 80);

        assertEquals(0, blend.current());
    }

    @Test
    void everyFrameOfTheLoopIsPackaged() {
        for (int i = 0; i < 80; i++) {
            String path = "/assets/arffornia/textures/gui/title/background/f%04d.jpg".formatted(i);

            assertNotNull(LoopFramesTest.class.getResource(path), path);
        }
    }
}
