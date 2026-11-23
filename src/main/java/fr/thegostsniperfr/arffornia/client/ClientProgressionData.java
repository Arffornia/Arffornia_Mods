package fr.thegostsniperfr.arffornia.client;

import net.minecraft.network.chat.Component;

public class ClientProgressionData {
    /** Shown in the HUD; a literal milestone name from the API or a translatable fallback. */
    public static Component currentMilestoneTarget = Component.translatable("hud.arffornia.target.none");
}
