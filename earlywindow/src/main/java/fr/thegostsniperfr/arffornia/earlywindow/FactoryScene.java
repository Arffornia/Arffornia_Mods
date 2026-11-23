package fr.thegostsniperfr.arffornia.earlywindow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The procedural factory baked by Arffornia_Assets {@code early/scene.mjs}: a still, sprites of what moves
 * with their motion sampled at 60 Hz, and mattes. Every element loops on its own period, so the scene has
 * no seam. Same format and same code as the title screen's copy ({@code client/screen/title/FactoryScene}).
 */
record FactoryScene(int width, int height, String still, String chevron, Map<String, Sprite> sprites, List<Group> groups) {
    record Sprite(String file, int x, int y, int w, int h, float pivotX, float pivotY, int frames, int cols) {}

    record Image(String file, int x, int y, int w, int h) {}

    /** Belt top corners in scene px (u along the item motion, v across), chevrons scroll at {@code speed} blocks/s. */
    record Belt(float[] u0v0, float[] u1v0, float[] u0v1, float[] u1v1, float len, float speed, int[] rgba) {}

    record Element(String sprite, float period, int rate, float[] phases, float[][] samples) {}

    record Group(String id, Belt belt, List<Element> elements, Image glass, Image matte) {}

    /** Where an element instance is at time t: offset in scene px, scale about the sprite's pivot, alpha, frame. */
    record Pose(float dx, float dy, float scale, float alpha, float frame) {}

    static Pose pose(Element e, float seconds, float phase) {
        double x = (((seconds + phase) % e.period()) + e.period()) % e.period() * e.rate();
        int n = e.samples().length;
        int i = (int) Math.floor(x) % n;
        int j = (i + 1) % n;
        float f = (float) (x - Math.floor(x));
        float[] a = e.samples()[i], b = e.samples()[j];
        float frame = a[4] + (b[4] - a[4]) * f;
        // frame tracks wrap (n-1 -> 0): no blend across the wrap, the next frame is the first one
        if (b[4] < a[4]) frame = a[4] + f;
        return new Pose(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f, frame);
    }

    static FactoryScene parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonArray size = o.getAsJsonArray("size");
        Map<String, Sprite> sprites = new LinkedHashMap<>();
        for (var e : o.getAsJsonObject("sprites").entrySet()) {
            JsonObject s = e.getValue().getAsJsonObject();
            JsonArray pivot = s.getAsJsonArray("pivot");
            JsonObject frames = s.has("frames") ? s.getAsJsonObject("frames") : null;
            sprites.put(e.getKey(), new Sprite(s.get("file").getAsString(), s.get("x").getAsInt(), s.get("y").getAsInt(), s.get("w").getAsInt(),
                    s.get("h").getAsInt(), pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(),
                    frames == null ? 0 : frames.get("count").getAsInt(), frames == null ? 0 : frames.get("cols").getAsInt()));
        }
        List<Group> groups = new ArrayList<>();
        for (var ge : o.getAsJsonArray("groups")) {
            JsonObject g = ge.getAsJsonObject();
            Belt belt = null;
            if (g.has("belt")) {
                JsonObject b = g.getAsJsonObject("belt");
                JsonArray c = b.getAsJsonArray("corners");
                JsonArray t = b.getAsJsonArray("tint");
                belt = new Belt(floats(c.get(0).getAsJsonArray()), floats(c.get(1).getAsJsonArray()), floats(c.get(2).getAsJsonArray()),
                        floats(c.get(3).getAsJsonArray()), b.get("len").getAsFloat(), b.get("speed").getAsFloat(),
                        new int[] { t.get(0).getAsInt(), t.get(1).getAsInt(), t.get(2).getAsInt(), t.get(3).getAsInt() });
            }
            List<Element> elements = new ArrayList<>();
            for (var ee : g.getAsJsonArray("elements")) {
                JsonObject e = ee.getAsJsonObject();
                JsonArray samples = e.getAsJsonArray("samples");
                float[][] s = new float[samples.size()][];
                for (int i = 0; i < s.length; i++) s[i] = floats(samples.get(i).getAsJsonArray());
                elements.add(new Element(e.get("sprite").getAsString(), e.get("period").getAsFloat(), e.get("rate").getAsInt(),
                        floats(e.getAsJsonArray("phases")), s));
            }
            groups.add(new Group(g.get("id").getAsString(), belt, elements, image(g, "glass"), image(g, "matte")));
        }
        return new FactoryScene(size.get(0).getAsInt(), size.get(1).getAsInt(), o.get("static").getAsString(), o.get("chevron").getAsString(),
                sprites, groups);
    }

    private static Image image(JsonObject g, String key) {
        if (!g.has(key)) return null;
        JsonObject m = g.getAsJsonObject(key);
        return new Image(m.get("file").getAsString(), m.get("x").getAsInt(), m.get("y").getAsInt(), m.get("w").getAsInt(), m.get("h").getAsInt());
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }
}
