package fr.thegostsniperfr.arffornia.screen;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.network.ServerboundLaunchElevatorPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public class SpaceElevatorScreen extends AbstractContainerScreen<SpaceElevatorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/space_elevator_gui.png");
    private Button launchButton;

    public SpaceElevatorScreen(SpaceElevatorMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.launchButton = this.addRenderableWidget(new Button.Builder(
                Component.literal("Launch"),
                (button) -> PacketDistributor.sendToServer(new ServerboundLaunchElevatorPacket(this.menu.blockEntity.getBlockPos())))
                .bounds(this.leftPos + 98, this.topPos + 60, 60, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        pGuiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        this.launchButton.active = this.menu.blockEntity.areRequirementsMet(this.menu.initialDetails);

        ArfforniaApiDtos.MilestoneDetails details = this.menu.initialDetails;
        if (details != null) {

            int contentStartY = this.topPos + 18;

            // Requirements
            int reqStartX = this.leftPos + 8;
            pGuiGraphics.drawString(font, "Requirements:", reqStartX, contentStartY - 12, 4210752, false);
            for (int i = 0; i < Math.min(details.requirements().size(), 3); i++) {
                renderRequirementLine(pGuiGraphics, details.requirements().get(i), reqStartX, contentStartY + i * 18, pMouseX, pMouseY);
            }

            // Unlocks
            int unlockStartX = this.leftPos + 98;
            pGuiGraphics.drawString(font, "Unlocks:", unlockStartX, contentStartY - 12, 4210752, false);
            for (int i = 0; i < Math.min(details.unlocks().size(), 8); i++) {
                renderUnlockItem(pGuiGraphics, details.unlocks().get(i), unlockStartX + (i % 4) * 18, contentStartY + (i / 4) * 18, pMouseX, pMouseY);
            }
        }

        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    private void renderRequirementLine(GuiGraphics pGuiGraphics, ArfforniaApiDtos.MilestoneRequirement req, int x, int y, int mouseX, int mouseY) {
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(req.itemId()));
        if (itemOpt.isPresent()) {
            ItemStack ghostStack = new ItemStack(itemOpt.get());

            pGuiGraphics.renderItem(ghostStack, x, y);

            int countInInventory = this.menu.blockEntity.countItems(itemOpt.get());
            double ratio = req.amount() > 0 ? (double) countInInventory / req.amount() : 1.0;

            int color;
            if (ratio >= 1.0) {
                color = 0x55FF55; // Green
            } else if (ratio >= 0.3) {
                color = 0xFFB800; // Orange
            } else {
                color = 0xFF5555; // Red
            }

            Component progressText = Component.literal(String.format("%d / %d", countInInventory, req.amount()));
            pGuiGraphics.drawString(this.font, progressText, x + 20, y + 4, color, false);

            if (this.isMouseOver(mouseX, mouseY, x, y, 16, 16)) {
                pGuiGraphics.renderTooltip(this.font, ghostStack, mouseX, mouseY);
            }
        }
    }

    private void renderUnlockItem(GuiGraphics pGuiGraphics, ArfforniaApiDtos.MilestoneUnlock unlock, int x, int y, int mouseX, int mouseY) {
        Optional<ItemStack> stackOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(unlock.itemId())).map(ItemStack::new);
        if (stackOpt.isPresent()) {
            pGuiGraphics.renderItem(stackOpt.get(), x, y);
            if (this.isMouseOver(mouseX, mouseY, x, y, 16, 16)) {
                pGuiGraphics.renderTooltip(this.font, stackOpt.get(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // pGuiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);

        // Block inventory title
        pGuiGraphics.drawString(this.font, Component.literal("Space Elevator Inventory:"), -181, 6, 0xFFFFFF, true);

        // Player inventory title
        pGuiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.imageHeight - 94, 4210752, false);
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}