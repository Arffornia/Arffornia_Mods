package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.config.ClientConfig.HudCorner;

public final class HudPlacement {
    private HudPlacement() {
    }

    /** Top-left pixel of a box anchored to {@code corner}, pushed {@code offsetX}/{@code offsetY} inwards. */
    public static int[] position(HudCorner corner, int offsetX, int offsetY, int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
        boolean right = corner == HudCorner.TOP_RIGHT || corner == HudCorner.BOTTOM_RIGHT;
        boolean bottom = corner == HudCorner.BOTTOM_LEFT || corner == HudCorner.BOTTOM_RIGHT;
        int x = right ? screenWidth - boxWidth - offsetX : offsetX;
        int y = bottom ? screenHeight - boxHeight - offsetY : offsetY;
        return new int[]{x, y};
    }
}
