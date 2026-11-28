package fr.thegostsniperfr.arffornia.client;

import net.minecraft.network.chat.Component;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.config.ClientConfig;
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
            Component textToDisplay = Component.translatable("hud.arffornia.target", ClientProgressionData.currentMilestoneTarget);

            int[] position = HudPlacement.position(
                    ClientConfig.TARGET_HUD_CORNER.get(), ClientConfig.TARGET_HUD_OFFSET_X.get(), ClientConfig.TARGET_HUD_OFFSET_Y.get(),
                    mc.font.width(textToDisplay), mc.font.lineHeight, guiGraphics.guiWidth(), guiGraphics.guiHeight());

            guiGraphics.drawString(mc.font, textToDisplay, position[0], position[1], 0xFFFFFF, true);
        }
    }
}