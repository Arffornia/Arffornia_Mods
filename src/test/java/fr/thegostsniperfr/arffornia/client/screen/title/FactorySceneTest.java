package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FactorySceneTest {
    private static final String DIR = "/assets/arffornia/textures/gui/title/factory/";

    private static FactoryScene baked() throws IOException {
        try (InputStream in = FactorySceneTest.class.getResourceAsStream(DIR + "scene.json")) {
            return FactoryScene.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Brian's playtest: things teleported back to the start at the loop. A visible part may only glide. */
    @Test
    void noVisiblePartJumpsBetweenTwoFramesAtSixtyFps_acrossItsLoopToo() throws IOException {
        FactoryScene scene = baked();
        float maxStep = scene.width() * 0.004f;
        for (FactoryScene.Group g : scene.groups()) {
            for (FactoryScene.Element e : g.elements()) {
                for (float t = 0; t < e.period() * 2; t += 1 / 60f) {
                    var a = FactoryScene.pose(e, t, 0);
                    var b = FactoryScene.pose(e, t + 1 / 60f, 0);
                    if (a.alpha() <= 0.01f || b.alpha() <= 0.01f || a.scale() <= 0.01f || b.scale() <= 0.01f) continue;
                    float step = (float) Math.hypot(b.dx() - a.dx(), b.dy() - a.dy());
                    float at = t;
                    assertTrue(step <= maxStep, () -> g.id() + "/" + e.sprite() + " jumps " + step + " px at t=" + at);
                }
            }
        }
    }

    @Test
    void everyLayerTheSceneNamesIsPackaged() throws IOException {
        FactoryScene scene = baked();
        assertNotNull(FactorySceneTest.class.getResource(DIR + scene.still()));
        assertNotNull(FactorySceneTest.class.getResource(DIR + "overlay.png"));
        scene.sprites().values().forEach(s -> assertNotNull(FactorySceneTest.class.getResource(DIR + s.file()), s.file()));
        for (FactoryScene.Group g : scene.groups()) {
            if (g.matte() != null) assertNotNull(FactorySceneTest.class.getResource(DIR + g.matte().file()), g.matte().file());
            if (g.glass() != null) assertNotNull(FactorySceneTest.class.getResource(DIR + g.glass().file()), g.glass().file());
        }
    }
}
