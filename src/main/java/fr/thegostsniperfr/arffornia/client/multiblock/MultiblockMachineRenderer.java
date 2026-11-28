package fr.thegostsniperfr.arffornia.client.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMachineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws a formed structure as its GeckoLib model, exported by Arffornia_Assets {@code tools/pix/geckolib.mjs}. The model
 * stands on the structure's floor at its horizontal centre with its front towards +z (Blockbench south), so it turns
 * with the master's facing and is mirrored when the structure was built mirrored.
 */
public class MultiblockMachineRenderer extends GeoBlockRenderer<MultiblockMachineBlockEntity> {
    public MultiblockMachineRenderer(ResourceLocation model) {
        super(new DefaultedBlockGeoModel<>(model));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(MultiblockMachineBlockEntity machine, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!machine.parts().isEmpty()) {
            super.render(machine, partialTick, poseStack, buffers, packedLight, packedOverlay);
        }
    }

    /** Called with the pose at the master block's bottom centre. */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        MultiblockMachineBlockEntity machine = getAnimatable();
        AABB bounds = machine.bounds();
        Vec3 centre = bounds.getCenter();
        BlockPos origin = machine.getBlockPos();
        poseStack.translate(centre.x - origin.getX() - 0.5, bounds.minY - origin.getY(), centre.z - origin.getZ() - 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        if (machine.mirrored()) {
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }
    }

    /** No culling: a mirrored structure turns every face inside out. */
    @Override
    public RenderType getRenderType(MultiblockMachineBlockEntity machine, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
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
