package fr.thegostsniperfr.arffornia.client;

import net.minecraft.network.chat.Component;

public class ClientProgressionData {
    /** Shown in the HUD; a literal milestone name from the API or a translatable fallback. */
    public static Component currentMilestoneTarget = Component.translatable("hud.arffornia.target.none");

    public static boolean graphStateHasBeenSet = false;

    public static double lastCameraX = 0;

    public static double lastCameraY = 0;

    public static float lastZoom = 1.0f;

    public static int lastGraphId = -1;
}