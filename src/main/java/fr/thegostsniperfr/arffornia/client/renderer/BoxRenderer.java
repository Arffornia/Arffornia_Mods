package fr.thegostsniperfr.arffornia.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.thegostsniperfr.arffornia.block.entity.LootBoxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class BoxRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private final Font font;

    public BoxRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        List<String> lines = null;

        if (blockEntity instanceof LootBoxBlockEntity lootBox) {
            lines = lootBox.getHologram();
        }

        if (lines == null || lines.isEmpty()) return;

        poseStack.pushPose();

        poseStack.translate(0.5D, 1.5D + (lines.size() * 0.25D), 0.5D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        float heightOffset = 0;
        for (String line : lines) {
            Component text = Component.literal(line);
            float xOffset = (float) (-font.width(text) / 2);
            font.drawInBatch(text, xOffset, heightOffset, 0xFFFFFFFF, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0x40000000, packedLight);
            heightOffset += 10;
        }

        poseStack.popPose();
    }
}