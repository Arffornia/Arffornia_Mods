package fr.thegostsniperfr.arffornia.client.ui.band;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One stage band of the progression graph (spec 018 prototype), as laid out once by the web tool.
 * Both renderers read this snapshot; neither recomputes the layout.
 */
public record StageBand(
        Header header,
        int node,
        Bounds bounds,
        List<Column> cols,
        List<Node> nodes,
        List<Edge> edges,
        List<Junction> junctions) {

    public static final String RESOURCE = "/assets/arffornia/ui/band/band.json";

    public record Header(Stage stage, Elevator elevator) {
    }

    public record Stage(int n, String name, int done, int total, float threshold) {
        public int needed() {
            return (int) Math.ceil(total * threshold);
        }
    }

    public record Elevator(int phase, int phases, List<Delivery> deliveries) {
    }

    public record Delivery(String i, int have, int need) {
    }

    public record Bounds(float x0, float y0, float x1, float y1) {
        public float width() {
            return x1 - x0;
        }

        public float height() {
            return y1 - y0;
        }
    }

    public record Column(float x, float y, float w, float h, boolean current, String label) {
    }

    public record Node(String k, String t, String caption, String icon, String state, float x, float y) {
    }

    public record Edge(String from, String to, boolean done, List<float[]> pts) {
    }

    public record Junction(float x, float y, boolean done) {
    }

    public static StageBand load() {
        try (InputStream in = StageBand.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), StageBand.class);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }
}
