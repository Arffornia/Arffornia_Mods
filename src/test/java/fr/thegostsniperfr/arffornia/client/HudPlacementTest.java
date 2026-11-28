package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.config.ClientConfig.HudCorner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class HudPlacementTest {
    @Test
    void topLeftUsesTheOffsetsAsIs() {
        assertArrayEquals(new int[]{5, 7}, HudPlacement.position(HudCorner.TOP_LEFT, 5, 7, 100, 9, 400, 300));
    }

    @Test
    void topRightKeepsTheBoxInsideTheRightEdge() {
        assertArrayEquals(new int[]{295, 5}, HudPlacement.position(HudCorner.TOP_RIGHT, 5, 5, 100, 9, 400, 300));
    }

    @Test
    void bottomLeftKeepsTheBoxInsideTheBottomEdge() {
        assertArrayEquals(new int[]{5, 286}, HudPlacement.position(HudCorner.BOTTOM_LEFT, 5, 5, 100, 9, 400, 300));
    }

    @Test
    void bottomRightKeepsTheBoxInsideBothEdges() {
        assertArrayEquals(new int[]{295, 286}, HudPlacement.position(HudCorner.BOTTOM_RIGHT, 5, 5, 100, 9, 400, 300));
    }
}
