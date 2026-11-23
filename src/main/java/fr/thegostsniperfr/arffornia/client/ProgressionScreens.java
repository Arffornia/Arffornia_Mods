package fr.thegostsniperfr.arffornia.client;

import fr.thegostsniperfr.arffornia.client.screen.ProgressionGraphScreen;
import fr.thegostsniperfr.arffornia.client.web.WebProgression;
import fr.thegostsniperfr.arffornia.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** The progression key opens the website's viewer in the embedded browser, or the native screen (config, no WebGUI). */
public final class ProgressionScreens {
    public static final String WEBGUI = "webgui";

    private ProgressionScreens() {
    }

    /** WebGUI classes are touched only behind this check, so the mod runs without it. */
    public static boolean webEnabled() {
        return ClientConfig.PROGRESSION_SCREEN.get() == ClientConfig.ProgressionScreen.WEB && ModList.get().isLoaded(WEBGUI);
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (webEnabled() && WebProgression.usable()) {
            minecraft.setScreen(WebProgression.screen());
        } else {
            minecraft.setScreen(new ProgressionGraphScreen());
        }
    }
}
