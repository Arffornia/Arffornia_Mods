package fr.thegostsniperfr.arffornia.client.ui.band;

import fr.thegostsniperfr.arffornia.Arffornia;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Web renderer of the stage band: one browser started with the client and kept warm off-screen with
 * the page already loaded, so opening the screen only shows a rendered texture (spec 018, "MCEF
 * without the waiting"). Only touched when WebGUI is installed.
 */
public final class WebBandScreens {
    private static final int HIDDEN_FPS = 1;
    private static final int SHOWN_FPS = 60;

    private static WebBrowser browser;
    private static long bootAt;
    private static boolean loaded;

    private WebBandScreens() {
    }

    public static void warmUp() {
        bootAt = System.nanoTime();
        WebBrowser.addListener(new WebBrowser.Listener() {
            @Override
            public void onLoadEnd(WebBrowser source, String url, int httpStatus) {
                if (source == browser) {
                    loaded = true;
                    Arffornia.LOGGER.info("[band] web page loaded {} ms after client setup (status {})",
                            (System.nanoTime() - bootAt) / 1_000_000, httpStatus);
                }
            }

            @Override
            public void onLoadFailed(WebBrowser source, String url, int errorCode, String description) {
                if (source == browser) {
                    Arffornia.LOGGER.error("[band] web page failed: {} {} {}", url, errorCode, description);
                }
            }
        });
        WebBrowser.whenReady(() -> Minecraft.getInstance().execute(WebBandScreens::createBrowser));
    }

    private static void createBrowser() {
        Arffornia.LOGGER.info("[band] browser runtime ready {} ms after client setup", (System.nanoTime() - bootAt) / 1_000_000);
        try {
            Path page = FMLPaths.GAMEDIR.get().resolve("arffornia-band").resolve("band.html");
            Files.createDirectories(page.getParent());
            try (InputStream in = WebBandScreens.class.getResourceAsStream("/assets/arffornia/ui/band/band.html")) {
                Files.copy(in, page, StandardCopyOption.REPLACE_EXISTING);
            }
            var window = Minecraft.getInstance().getWindow();
            browser = WebBrowser.create(page.toUri().toString(), false);
            browser.resize(window.getWidth(), window.getHeight());
            browser.setWindowlessFrameRate(HIDDEN_FPS);
        } catch (IOException e) {
            Arffornia.LOGGER.error("[band] cannot stage the band page", e);
        }
    }

    public static Screen create() {
        return new WebBandScreen();
    }

    private static final class WebBandScreen extends Screen {
        WebBandScreen() {
            super(Component.translatable("screen.arffornia.band.web"));
        }

        @Override
        protected void init() {
            if (browser != null) {
                var window = Minecraft.getInstance().getWindow();
                browser.resize(window.getWidth(), window.getHeight());
                browser.setWindowlessFrameRate(SHOWN_FPS);
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            long start = System.nanoTime();
            ResourceLocation texture = browser != null && loaded && browser.isTextureReady() ? browser.getTextureIdentifier() : null;
            if (texture == null) {
                return;
            }
            graphics.blit(texture, 0, 0, 0.0f, 0.0f, width, height, width, height);
            graphics.flush();
            BandBench.rendered("web", System.nanoTime() - start);
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void removed() {
            if (browser != null) {
                browser.setWindowlessFrameRate(HIDDEN_FPS);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
