package fr.thegostsniperfr.arffornia.client.screen.title;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class TitleScreenHook {
    private TitleScreenHook() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // Exact class: another mod's TitleScreen subclass is a deliberate replacement, leave it alone.
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == TitleScreen.class) {
            event.setNewScreen(new ArfTitleScreen());
        }
    }
}
