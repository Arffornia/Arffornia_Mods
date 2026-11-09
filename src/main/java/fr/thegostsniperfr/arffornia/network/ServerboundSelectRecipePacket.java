package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.recipe.CrafterRecipes;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record ServerboundSelectRecipePacket(BlockPos blockPos, Optional<ResourceLocation> recipeId) implements CustomPacketPayload {

    public static final Type<ServerboundSelectRecipePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "select_recipe")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSelectRecipePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ServerboundSelectRecipePacket::blockPos,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ServerboundSelectRecipePacket::recipeId,
            ServerboundSelectRecipePacket::new
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
     * unless the selection is being cleared, the recipe is a Crafter recipe unlocked for the Crafter's progression.
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
        if (packet.recipeId().isEmpty()) {
            be.setSelectedRecipe(null);
            return true;
        }

        ResourceLocation recipeId = packet.recipeId().get();
        Optional<RecipeHolder<MachineRecipe>> recipe = CrafterRecipes.byId(player.server.getRecipeManager(), recipeId);
        if (recipe.isEmpty()) {
            Arffornia.LOGGER.warn("Rejected recipe selection {} from {}: not a Crafter recipe.", recipeId, player.getName().getString());
            return false;
        }
        if (!CrafterRecipes.isUnlocked(player.server, be.getLinkedProgressionId(), recipe.get().value())) {
            Arffornia.LOGGER.warn("Rejected recipe selection {} from {}: stage {} not unlocked in progression {}, or it is retired.",
                    recipeId, player.getName().getString(), recipe.get().value().stage().orElse(""), be.getLinkedProgressionId());
            return false;
        }
        be.setSelectedRecipe(recipeId);
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
