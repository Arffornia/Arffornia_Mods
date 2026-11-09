package fr.thegostsniperfr.arffornia.client.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.item.ModItems;
import fr.thegostsniperfr.arffornia.multiblock.BlockPlacement;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

/**
 * While the player holds the Wrench and looks at the trigger block of an unformed structure, draws the blocks still to
 * place as ghosts and outlines the wrong ones in red.
 */
@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class MultiblockHologram {
    private static final float GHOST_ALPHA = 0.45F;
    private static final float GHOST_SCALE = 0.8F;
    private static final int REFRESH_TICKS = 10;

    private static BlockPos cachedTrigger;
    private static long cachedAt = Long.MIN_VALUE;
    private static List<BlockPlacement> cachedMissing = List.of();

    private MultiblockHologram() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !(player.getMainHandItem().is(ModItems.WRENCH.get()) || player.getOffhandItem().is(ModItems.WRENCH.get()))) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos trigger = hit.getBlockPos();
        List<BlockPlacement> missing = missing(level, trigger, player);
        if (missing.isEmpty()) {
            return;
        }
        render(event.getPoseStack(), event.getCamera().getPosition(), minecraft, level, missing);
    }

    private static List<BlockPlacement> missing(ClientLevel level, BlockPos trigger, LocalPlayer player) {
        long now = level.getGameTime();
        if (!trigger.equals(cachedTrigger) || now - cachedAt >= REFRESH_TICKS || now < cachedAt) {
            cachedTrigger = trigger;
            cachedAt = now;
            cachedMissing = MultiblockDefinitions.forTrigger(level, level.getBlockState(trigger).getBlock())
                    .map(definition -> definition.shape().missing(level, trigger, player.getDirection().getOpposite()))
                    .orElse(List.of());
        }
        return cachedMissing;
    }

    private static void render(PoseStack poseStack, Vec3 camera, Minecraft minecraft, ClientLevel level, List<BlockPlacement> missing) {
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        VertexConsumer ghosts = new GhostVertexConsumer(buffers.getBuffer(Sheets.translucentCullBlockSheet()), GHOST_ALPHA);
        RandomSource random = RandomSource.create();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (BlockPlacement placement : missing) {
            BlockPos pos = placement.pos();
            poseStack.pushPose();
            poseStack.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            poseStack.scale(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE);
            poseStack.translate(-0.5, -0.5, -0.5);
            BakedModel model = dispatcher.getBlockModel(placement.state());
            for (RenderType renderType : model.getRenderTypes(placement.state(), random, ModelData.EMPTY)) {
                dispatcher.getModelRenderer().renderModel(poseStack.last(), ghosts, placement.state(), model, 1.0F, 1.0F, 1.0F,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);
            }
            poseStack.popPose();
        }
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (BlockPlacement placement : missing) {
            if (!level.getBlockState(placement.pos()).canBeReplaced()) {
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(placement.pos()).inflate(0.002), 1.0F, 0.2F, 0.2F, 1.0F);
            }
        }
        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
