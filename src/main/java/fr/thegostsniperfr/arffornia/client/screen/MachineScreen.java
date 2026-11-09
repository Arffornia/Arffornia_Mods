package fr.thegostsniperfr.arffornia.client.screen;

import fr.thegostsniperfr.arffornia.machine.MachineData;
import fr.thegostsniperfr.arffornia.machine.MachineMenu;
import fr.thegostsniperfr.arffornia.machine.MachineStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The screen every machine shares, drawn without a texture so a new {@link fr.thegostsniperfr.arffornia.machine.SlotLayout}
 * needs no art: slots, an energy gauge, a progress arrow and the machine status.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final ResourceLocation ARROW_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;
    private static final int GAUGE_X = 10;
    private static final int GAUGE_Y = 17;
    private static final int GAUGE_WIDTH = 12;
    private static final int GAUGE_HEIGHT = 52;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int ENERGY = 0xFFD83A1E;
    private static final int TEXT = 0xFF404040;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(GAUGE_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.arffornia.machine.energy",
                    MachineData.energy(menu.data()), MachineData.capacity(menu.data())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        bevel(graphics, x, y, imageWidth, imageHeight, LIGHT, DARK);
        for (Slot slot : menu.slots) {
            bevel(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, DARK, LIGHT);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT);
        }
        renderGauge(graphics, x + GAUGE_X, y + GAUGE_Y);
        renderArrow(graphics, x + (MachineMenu.INPUT_X + MachineMenu.COLUMNS * 18 + MachineMenu.OUTPUT_X) / 2 - ARROW_WIDTH / 2 - 1, y + MachineMenu.SLOTS_Y + 1);
    }

    private void renderGauge(GuiGraphics graphics, int x, int y) {
        bevel(graphics, x - 1, y - 1, GAUGE_WIDTH + 2, GAUGE_HEIGHT + 2, DARK, LIGHT);
        graphics.fill(x, y, x + GAUGE_WIDTH, y + GAUGE_HEIGHT, SLOT);
        int capacity = MachineData.capacity(menu.data());
        int energy = MachineData.energy(menu.data());
        int filled = capacity > 0 ? (int) ((long) GAUGE_HEIGHT * Math.min(energy, capacity) / capacity) : 0;
        graphics.fill(x, y + GAUGE_HEIGHT - filled, x + GAUGE_WIDTH, y + GAUGE_HEIGHT, ENERGY);
    }

    private void renderArrow(GuiGraphics graphics, int x, int y) {
        int max = menu.data().get(MachineData.MAX_PROGRESS);
        int progress = menu.data().get(MachineData.PROGRESS);
        graphics.fill(x, y + 6, x + ARROW_WIDTH - 6, y + 10, SLOT);
        if (max > 0 && progress > 0) {
            int width = Math.min(ARROW_WIDTH, (int) Math.ceil(ARROW_WIDTH * (double) progress / max));
            graphics.blitSprite(ARROW_SPRITE, ARROW_WIDTH, ARROW_HEIGHT, 0, 0, x, y, width, ARROW_HEIGHT);
        }
    }

    private static void bevel(GuiGraphics graphics, int x, int y, int width, int height, int topLeft, int bottomRight) {
        graphics.fill(x, y, x + width, y + 1, topLeft);
        graphics.fill(x, y, x + 1, y + height, topLeft);
        graphics.fill(x, y + height - 1, x + width, y + height, bottomRight);
        graphics.fill(x + width - 1, y, x + width, y + height, bottomRight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        MachineStatus status = MachineData.status(menu.data());
        Component text = Component.translatable(status.translationKey());
        graphics.drawString(font, text, imageWidth - 8 - font.width(text), inventoryLabelY, TEXT, false);
    }
}
