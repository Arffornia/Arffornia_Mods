package fr.thegostsniperfr.arffornia.client.screen.title;

import java.util.List;

/**
 * Spatial keyboard navigation between hex tiles (spec 007 D17): the arrow goes to the nearest tile
 * centre within +-60 degrees of its direction, the upper one on a tie.
 */
public final class TitleNavigation {
    public enum Arrow {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        final int dx;
        final int dy;

        Arrow(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    public record Node(String id, double x, double y) {}

    private TitleNavigation() {}

    public static String neighbour(List<Node> nodes, String fromId, Arrow arrow) {
        Node from = nodes.stream().filter(n -> n.id().equals(fromId)).findFirst().orElseThrow();
        String best = fromId;
        double bestScore = Double.MAX_VALUE;
        for (Node n : nodes) {
            if (n.id().equals(fromId)) continue;
            double vx = n.x() - from.x(), vy = n.y() - from.y(), d = Math.hypot(vx, vy);
            double cos = (vx * arrow.dx + vy * arrow.dy) / d;
            if (cos < 0.49) continue;
            double score = d * (2 - cos) - (vy < 0 && arrow.dx != 0 ? 1 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = n.id();
            }
        }
        return best;
    }
}
