package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ServerboundSelectRecipePacket(BlockPos blockPos,
                                            @Nullable Integer milestoneUnlockId) implements CustomPacketPayload {

    public static final Type<ServerboundSelectRecipePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "select_recipe")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSelectRecipePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ServerboundSelectRecipePacket::blockPos,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
            packet -> Optional.ofNullable(packet.milestoneUnlockId()),
            (pos, id) -> new ServerboundSelectRecipePacket(pos, id.orElse(null))
    );

    public static void handle(final ServerboundSelectRecipePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                processOnServer(player, packet);
            }
        });
    }

    /**
     * Applies a recipe selection after validating it. Must be called on the server thread.
     * The selection is accepted only if the player has this Crafter's menu open and is close to it, and,
     * unless the selection is being cleared, the recipe's milestone is completed in the Crafter's linked progression.
     *
     * @return true if the selection was applied.
     */
    public static boolean processOnServer(ServerPlayer player, ServerboundSelectRecipePacket packet) {
        if (!(player.containerMenu instanceof CrafterMenu menu)
                || !ServerPayloadChecks.isInteractingWith(player, menu, menu.blockEntity, packet.blockPos())) {
            Arffornia.LOGGER.warn("Rejected recipe selection from {} at {}: Crafter menu not open or out of reach.",
                    player.getName().getString(), packet.blockPos());
            return false;
        }

        CrafterBlockEntity be = menu.blockEntity;
        Integer unlockId = packet.milestoneUnlockId();
        if (unlockId == null) {
            be.setSelectedRecipe(null);
            return true;
        }

        ArfforniaApiDtos.CustomRecipe recipe = CustomRecipeManager.getRecipeByMilestoneUnlockId(unlockId);
        long progressionId = be.getLinkedProgressionId();
        if (recipe == null || progressionId == ModAttachments.NO_PROGRESSION) {
            Arffornia.LOGGER.warn("Rejected recipe selection {} from {}: unknown recipe or unlinked Crafter.",
                    unlockId, player.getName().getString());
            return false;
        }

        int requiredMilestoneId = recipe.milestoneId();
        if (!Progressions.isUnlocked(player.server, progressionId, requiredMilestoneId)) {
            Arffornia.LOGGER.warn("Rejected recipe selection {} from {}: milestone {} not completed in progression {}, or it is retired.",
                    unlockId, player.getName().getString(), requiredMilestoneId, progressionId);
            return false;
        }
        be.setSelectedRecipe(unlockId);
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}