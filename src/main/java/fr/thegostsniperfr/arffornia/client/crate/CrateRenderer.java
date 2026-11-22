package fr.thegostsniperfr.arffornia.client.crate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateBlock;
import fr.thegostsniperfr.arffornia.crate.CrateBlockEntity;
import fr.thegostsniperfr.arffornia.crate.CrateDefinition;
import fr.thegostsniperfr.arffornia.crate.CrateDefinitions;
import fr.thegostsniperfr.arffornia.crate.CrateRarity;
import fr.thegostsniperfr.arffornia.crate.CrateSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Draws a crate and its opening show: key into the lock, shaking crate with glowing seams, a beam in the rarity colour,
 * a slot-machine reel of 3D items, the reveal and the item flying to the opener. Everything follows
 * {@link CrateSequence}'s timeline, so every viewer sees the same thing.
 */
public class CrateRenderer implements BlockEntityRenderer<CrateBlockEntity> {
    public static final ModelResourceLocation BASE = model("block/crate/base");
    public static final ModelResourceLocation LID = model("block/crate/lid");
    public static final ModelResourceLocation SEAMS = model("block/crate/seams");
    private static final int SKY_BEAM = 320;

    private final Font font;

    public CrateRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, path));
    }

    @Override
    public void render(CrateBlockEntity crate, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CrateSequence.Show show = crate.show();
        float t = show == null ? -1 : crate.showTick() + partialTick;
        CrateDefinition.Client definition = crate.crate() == null ? null : CrateDefinitions.find(crate.crate());
        CrateRarity crateRarity = show != null ? show.crateRarity() : definition != null ? definition.rarity() : CrateRarity.COMMON;
        long gameTime = crate.getLevel() == null ? 0 : crate.getLevel().getGameTime();
        float facingDegrees = crate.getBlockState().getValue(CrateBlock.FACING).toYRot();

        pose.pushPose();
        shake(pose, t, gameTime + partialTick);
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facingDegrees));
        pose.translate(-0.5, 0, -0.5);
        renderModel(pose, buffers, crate, BASE, light, overlay, 1, 1, 1);
        renderSeams(pose, buffers, crate, show, t, gameTime + partialTick, crateRarity);
        pose.pushPose();
        lid(pose, t);
        renderModel(pose, buffers, crate, LID, light, overlay, 1, 1, 1);
        pose.popPose();
        if (show != null && t < CrateSequence.ROULETTE) {
            keyInLock(pose, buffers, crate, show, t, partialTick, light);
        }
        pose.popPose();

        if (show == null) {
            label(pose, buffers, definition, light);
            return;
        }
        beam(pose, buffers, show, t, partialTick, gameTime);
        if (t >= CrateSequence.ROULETTE && t < CrateSequence.REVEAL) {
            reel(pose, buffers, crate, show, t, light);
        } else if (t >= CrateSequence.REVEAL && t < CrateSequence.END) {
            reveal(pose, buffers, crate, show, t, partialTick);
        }
    }

    private static void shake(PoseStack pose, float t, float time) {
        if (t < CrateSequence.CLICK || t >= CrateSequence.REVEAL + 4) {
            return;
        }
        float strength = t < CrateSequence.ROULETTE ? 0.025F * Math.min(1, (t - CrateSequence.CLICK) / 6F)
                : t >= CrateSequence.REVEAL ? 0.06F * (1 - (t - CrateSequence.REVEAL) / 4F) : 0.008F;
        pose.translate(Mth.sin(time * 2.7F) * strength, Math.abs(Mth.sin(time * 3.1F)) * strength, Mth.cos(time * 2.3F) * strength);
    }

    /** The lid lifts for the roulette, bursts open at the reveal, then settles back. Hinge at the back top edge. */
    private static void lid(PoseStack pose, float t) {
        if (t < CrateSequence.ROULETTE) {
            return;
        }
        float lift;
        float angle;
        if (t < CrateSequence.REVEAL) {
            float in = Math.min(1, (t - CrateSequence.ROULETTE) / 6F);
            lift = 0.12F * in + Mth.sin(t * 0.9F) * 0.01F;
            angle = 8 * in;
        } else if (t < CrateSequence.FLY) {
            float burst = Math.min(1, (t - CrateSequence.REVEAL) / 3F);
            lift = 0.12F + 0.1F * burst;
            angle = 8 + 97 * (1 - (1 - burst) * (1 - burst));
        } else {
            float close = Math.min(1, (t - CrateSequence.FLY) / (CrateSequence.CLIENT_END - CrateSequence.FLY));
            lift = 0.22F * (1 - close);
            angle = 105 * (1 - close * close);
        }
        pose.translate(0, lift, 0);
        pose.translate(0, 14 / 16F, 15.5F / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(-angle));
        pose.translate(0, -14 / 16F, -15.5F / 16F);
    }

    private static void renderModel(PoseStack pose, MultiBufferSource buffers, CrateBlockEntity crate, ModelResourceLocation location,
                                    int light, int overlay, float r, float g, float b) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), crate.getBlockState(),
                Minecraft.getInstance().getModelManager().getModel(location), r, g, b, light, overlay, ModelData.EMPTY, null);
    }

    private static void renderSeams(PoseStack pose, MultiBufferSource buffers, CrateBlockEntity crate, CrateSequence.Show show, float t,
                                    float time, CrateRarity crateRarity) {
        float intensity = 0.35F + 0.15F * Mth.sin(time * 0.08F);
        int colour = crateRarity.colour();
        if (show != null) {
            if (t >= CrateSequence.CLICK && t < CrateSequence.ROULETTE) {
                intensity = 0.5F + 0.5F * (t - CrateSequence.CLICK) / (CrateSequence.ROULETTE - CrateSequence.CLICK);
            } else if (t >= CrateSequence.ROULETTE && t < CrateSequence.FLY) {
                intensity = 0.85F + 0.15F * Mth.sin(t * 1.3F);
                colour = t >= CrateSequence.REVEAL ? show.rarity().colour() : colour;
            }
        }
        renderModel(pose, buffers, crate, SEAMS, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.red(colour) / 255F * intensity, FastColor.ARGB32.green(colour) / 255F * intensity,
                FastColor.ARGB32.blue(colour) / 255F * intensity);
    }

    /** In the crate's own frame (front at z = 0): the key flies from the opener's hand into the lock, then turns. */
    private static void keyInLock(PoseStack pose, MultiBufferSource buffers, CrateBlockEntity crate, CrateSequence.Show show, float t,
                                  float partialTick, int light) {
        Vec3 lock = new Vec3(0.5, 6 / 16F, -0.05);
        Vec3 start = lock.add(0, 0.6, -1.4);
        Entity opener = crate.getLevel() == null ? null : crate.getLevel().getEntity(show.openerId());
        if (opener != null) {
            Vec3 hand = opener.getEyePosition(partialTick).subtract(0, 0.4, 0).subtract(Vec3.atLowerCornerOf(crate.getBlockPos()));
            float yRot = crate.getBlockState().getValue(CrateBlock.FACING).toYRot();
            Vec3 local = hand.subtract(0.5, 0, 0.5).yRot((float) Math.toRadians(yRot - 180)).add(0.5, 0, 0.5);
            start = local;
        }
        float fly = Math.min(1, t / CrateSequence.CLICK);
        float eased = 1 - (1 - fly) * (1 - fly);
        Vec3 at = start.lerp(lock, eased);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        if (t >= CrateSequence.CLICK) {
            pose.mulPose(Axis.XP.rotationDegrees(Math.min(90, (t - CrateSequence.CLICK) * 45)));
        }
        pose.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getItemRenderer().renderStatic(show.key(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, crate.getLevel(), 0);
        pose.popPose();
    }

    private static void beam(PoseStack pose, MultiBufferSource buffers, CrateSequence.Show show, float t, float partialTick, long gameTime) {
        if (t < CrateSequence.CHARGE || t >= CrateSequence.FLY + 6) {
            return;
        }
        boolean legendary = show.rarity() == CrateRarity.LEGENDARY;
        int colour = t >= CrateSequence.REVEAL ? show.rarity().colour() : show.crateRarity().colour();
        float grow = Math.min(1, (t - CrateSequence.CHARGE) / (CrateSequence.ROULETTE - CrateSequence.CHARGE));
        int height = Math.max(1, (int) (6 * grow));
        if (t >= CrateSequence.REVEAL) {
            height = legendary ? (int) Math.min(SKY_BEAM, 6 + (t - CrateSequence.REVEAL) * 40) : 8;
        }
        float fade = t >= CrateSequence.FLY ? 1 - (t - CrateSequence.FLY) / 6F : 1;
        float radius = (legendary && t >= CrateSequence.REVEAL ? 0.3F : 0.15F) * fade;
        pose.pushPose();
        pose.translate(0, 0.85, 0);
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, gameTime, 0, height,
                FastColor.ARGB32.opaque(colour), radius, radius * 1.6F);
        pose.popPose();
    }

    /** A drum of items turning around a horizontal axis above the crate: the front item is the current one. */
    private void reel(PoseStack pose, MultiBufferSource buffers, CrateBlockEntity crate, CrateSequence.Show show, float t, int light) {
        double position = CrateSequence.reelPosition(show, t);
        int centre = (int) Math.floor(position);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 here = crate.getBlockPos().getCenter();
        float towardViewer = (float) Math.toDegrees(Math.atan2(camera.x - here.x, camera.z - here.z));
        pose.pushPose();
        pose.translate(0.5, 1.55, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(towardViewer));
        for (int i = centre - 2; i <= centre + 3; i++) {
            if (i < 0 || i >= show.roulette().size()) {
                continue;
            }
            double angle = (i - position) * 40;
            if (Math.abs(angle) > 85) {
                continue;
            }
            float front = (float) Math.cos(Math.toRadians(angle));
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees((float) angle));
            pose.translate(0, 0, 0.55);
            float scale = 0.35F + 0.3F * front * front;
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(show.roulette().get(i), ItemDisplayContext.FIXED,
                    front > 0.95 ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, pose, buffers, crate.getLevel(), i);
            pose.popPose();
        }
        pose.popPose();
    }

    private void reveal(PoseStack pose, MultiBufferSource buffers, CrateBlockEntity crate, CrateSequence.Show show, float t, float partialTick) {
        ItemStack item = show.shown();
        float grow = Math.min(1, (t - CrateSequence.REVEAL) / 8F);
        float scale = 0.4F + 0.9F * (1 - (1 - grow) * (1 - grow) * (1 - grow));
        Vec3 over = new Vec3(0.5, 1.6 + Mth.sin(t * 0.15F) * 0.05, 0.5);
        Vec3 at = over;
        if (t >= CrateSequence.FLY) {
            Entity opener = crate.getLevel() == null ? null : crate.getLevel().getEntity(show.openerId());
            float fly = (t - CrateSequence.FLY) / (CrateSequence.END - CrateSequence.FLY);
            if (opener != null) {
                Vec3 target = opener.getEyePosition(partialTick).subtract(0, 0.5, 0).subtract(Vec3.atLowerCornerOf(crate.getBlockPos()));
                at = over.lerp(target, fly * fly);
            }
            scale *= 1 - 0.7F * fly;
        }
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(t * 4));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, crate.getLevel(), 0);
        pose.popPose();
        if (t < CrateSequence.FLY) {
            Component name = Component.translatable("chat.arffornia.crate.stack", item.getCount(), item.getHoverName())
                    .withStyle(show.rarity().chatColour());
            text(pose, buffers, name, 2.35 + 0.1 * grow, grow);
        }
    }

    private void label(PoseStack pose, MultiBufferSource buffers, CrateDefinition.Client definition, int light) {
        if (definition == null) {
            return;
        }
        text(pose, buffers, definition.name().copy().withStyle(definition.rarity().chatColour()), 1.35, 1);
    }

    private void text(PoseStack pose, MultiBufferSource buffers, Component text, double height, float alpha) {
        pose.pushPose();
        pose.translate(0.5, height, 0.5);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        float x = -font.width(text) / 2F;
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255 * alpha) << 24;
        // Glyphs and their background quad are coplanar and z-fight. The first pass draws only the background:
        // Font forces alpha 0 to opaque, so 0x04 is the smallest alpha it keeps, and the text shader discards it.
        // The glyphs follow with a polygon offset so they always win the depth test.
        font.drawInBatch(text, x, 0, 0x04000000, false, matrix, buffers, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, 0, 0xFFFFFF | ((int) (255 * Math.max(0.1F, alpha)) << 24), false, matrix, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CrateBlockEntity crate) {
        if (crate.show() != null) {
            return AABB.INFINITE;
        }
        return new AABB(crate.getBlockPos()).expandTowards(0, 2, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(CrateBlockEntity crate) {
        return crate.show() != null;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
