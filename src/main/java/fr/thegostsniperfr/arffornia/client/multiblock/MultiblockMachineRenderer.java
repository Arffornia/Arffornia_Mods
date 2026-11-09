package fr.thegostsniperfr.arffornia.client.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.thegostsniperfr.arffornia.machine.MachineBlock;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a formed structure as one model. The model is authored facing north around the structure's centre block, so it
 * spans at most 3x3x3 blocks (block models are limited to -16..32); larger machines need an animated model renderer.
 */
public class MultiblockMachineRenderer implements BlockEntityRenderer<MultiblockMachineBlockEntity> {
    private final ModelResourceLocation model;

    public MultiblockMachineRenderer(BlockEntityRendererProvider.Context context, ModelResourceLocation model) {
        this.model = model;
    }

    @Override
    public void render(MultiblockMachineBlockEntity machine, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (machine.parts().isEmpty()) {
            return;
        }
        BakedModel baked = Minecraft.getInstance().getModelManager().getModel(model);
        Vec3 centre = machine.bounds().getCenter();
        BlockPos origin = machine.getBlockPos();
        Direction facing = machine.getBlockState().getValue(MachineBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(centre.x - origin.getX(), centre.y - origin.getY(), centre.z - origin.getZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        if (machine.mirrored()) {
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(),
                buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), machine.getBlockState(), baked,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay, ModelData.EMPTY, null);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MultiblockMachineBlockEntity machine) {
        return machine.bounds();
    }

    @Override
    public boolean shouldRenderOffScreen(MultiblockMachineBlockEntity machine) {
        return true;
    }
}
