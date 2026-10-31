package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
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
import java.util.concurrent.CompletableFuture;

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
     * @return a future completing (on the server thread) with true if the selection was applied.
     */
    public static CompletableFuture<Boolean> processOnServer(ServerPlayer player, ServerboundSelectRecipePacket packet) {
        if (!(player.containerMenu instanceof CrafterMenu menu)
                || !ServerPayloadChecks.isInteractingWith(player, menu, menu.blockEntity, packet.blockPos())) {
            Arffornia.LOGGER.warn("Rejected recipe selection from {} at {}: Crafter menu not open or out of reach.",
                    player.getName().getString(), packet.blockPos());
            return CompletableFuture.completedFuture(false);
        }

        CrafterBlockEntity be = menu.blockEntity;
        Integer unlockId = packet.milestoneUnlockId();
        if (unlockId == null) {
            be.setSelectedRecipe(null);
            return CompletableFuture.completedFuture(true);
        }

        ArfforniaApiDtos.CustomRecipe recipe = CustomRecipeManager.getRecipeByMilestoneUnlockId(unlockId);
        long progressionId = be.getLinkedProgressionId();
        if (recipe == null || progressionId == -1) {
            Arffornia.LOGGER.warn("Rejected recipe selection {} from {}: unknown recipe or unlinked Crafter.",
                    unlockId, player.getName().getString());
            return CompletableFuture.completedFuture(false);
        }

        int requiredMilestoneId = recipe.milestoneId();
        return ArfforniaApiService.getInstance().fetchProgressionData(progressionId).thenApplyAsync(progressionData -> {
            boolean unlocked = progressionData != null && progressionData.completedMilestones() != null
                    && progressionData.completedMilestones().contains(requiredMilestoneId);
            if (!unlocked) {
                Arffornia.LOGGER.warn("Rejected recipe selection {} from {}: milestone {} not completed.",
                        unlockId, player.getName().getString(), requiredMilestoneId);
                return false;
            }
            // The menu may have been closed while the progression was fetched.
            if (!ServerPayloadChecks.isInteractingWith(player, menu, be, packet.blockPos())) {
                return false;
            }
            be.setSelectedRecipe(unlockId);
            return true;
        }, player.server);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}