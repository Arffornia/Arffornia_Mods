package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public class HudRenderHandler {
    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            String textToDisplay = "Milestone: " + ClientProgressionData.currentMilestoneTarget;

            int screenWidth = guiGraphics.guiWidth();
            int textWidth = mc.font.width(textToDisplay);

            int x = screenWidth - textWidth - 5;
            int y = 5;

            guiGraphics.drawString(mc.font, textToDisplay, x, y, 0xFFFFFF, true);
        }
    }
}