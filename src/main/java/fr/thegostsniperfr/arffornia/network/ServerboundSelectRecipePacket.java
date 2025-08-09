package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
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
                if (player.level().isLoaded(packet.blockPos()) && player.level().getBlockEntity(packet.blockPos()) instanceof CrafterBlockEntity be) {
                    be.setSelectedRecipe(packet.milestoneUnlockId());
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}