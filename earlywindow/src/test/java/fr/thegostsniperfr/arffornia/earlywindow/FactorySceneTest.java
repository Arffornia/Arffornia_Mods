package fr.thegostsniperfr.arffornia.earlywindow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FactorySceneTest {
    private static FactoryScene baked() throws IOException {
        try (InputStream in = FactorySceneTest.class.getResourceAsStream("/arffornia_earlywindow/factory/scene.json")) {
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
    void framesAdvanceOneAtATimeAndWrapToTheFirst() throws IOException {
        FactoryScene scene = baked();
        for (FactoryScene.Group g : scene.groups()) {
            for (FactoryScene.Element e : g.elements()) {
                int count = scene.sprites().get(e.sprite()).frames();
                if (count == 0) continue;
                for (float t = 0; t < e.period() * 2; t += 1 / 120f) {
                    int a = Math.floorMod((int) FactoryScene.pose(e, t, 0).frame(), count);
                    int b = Math.floorMod((int) FactoryScene.pose(e, t + 1 / 120f, 0).frame(), count);
                    int step = Math.floorMod(b - a, count);
                    assertTrue(step <= 1, g.id() + " skips " + step + " frames");
                }
            }
        }
    }
}
