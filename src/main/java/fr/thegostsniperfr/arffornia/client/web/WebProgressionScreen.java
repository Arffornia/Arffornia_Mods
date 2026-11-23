package fr.thegostsniperfr.arffornia.client.web;

import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Shows the warm progression page full screen and forwards mouse and keyboard to it, or an error when the browser runtime failed. */
final class WebProgressionScreen extends Screen {
    WebProgressionScreen() {
        super(Component.translatable("screen.arffornia.progression"));
    }

    @Override
    protected void init() {
        if (!WebProgression.usable()) {
            return;
        }
        var window = Minecraft.getInstance().getWindow();
        WebProgression.shown(Math.max(1, window.getWidth()), Math.max(1, window.getHeight()));
    }

    @Override
    public void removed() {
        WebProgression.hidden();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!WebProgression.usable()) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            drawWrapped(graphics, Component.translatable("gui.arffornia.progression.browser_failed"), height / 2 - 20, 0xFF5555);
            drawWrapped(graphics, Component.translatable("gui.arffornia.progression.browser_failed.hint"), height / 2 + 6, 0xAAAAAA);
            return;
        }
        WebBrowser browser = WebProgression.browser();
        ResourceLocation texture = browser != null && browser.isTextureReady() ? browser.getTextureIdentifier() : null;
        if (texture == null) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, Component.translatable("gui.arffornia.loading"), width / 2, height / 2, 0xFFFFFF);
            return;
        }
        graphics.blit(texture, 0, 0, 0.0f, 0.0f, width, height, width, height);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int top, int color) {
        int y = top;
        for (FormattedCharSequence line : font.split(text, Math.max(40, width - 40))) {
            graphics.drawCenteredString(font, line, width / 2, y, color);
            y += font.lineHeight + 2;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private int pixelX(double guiX) {
        return (int) Math.round(guiX * Minecraft.getInstance().getWindow().getWidth() / Math.max(1, width));
    }

    private int pixelY(double guiY) {
        return (int) Math.round(guiY * Minecraft.getInstance().getWindow().getHeight() / Math.max(1, height));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendMouseMove(pixelX(mouseX), pixelY(mouseY));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendMousePress(pixelX(mouseX), pixelY(mouseY), button);
            browser.setFocus(true);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendMouseRelease(pixelX(mouseX), pixelY(mouseY), button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseMoved(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendMouseWheel(pixelX(mouseX), pixelY(mouseY), scrollY, 0);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        WebBrowser browser = WebProgression.browser();
        if (browser != null) {
            browser.sendKeyTyped(codePoint, modifiers);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
