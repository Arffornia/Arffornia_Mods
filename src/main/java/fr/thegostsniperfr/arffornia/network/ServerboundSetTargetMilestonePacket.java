package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ServerboundSetTargetMilestonePacket(@Nullable Integer milestoneId) implements CustomPacketPayload {

    public static final Type<ServerboundSetTargetMilestonePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "set_target_milestone")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSetTargetMilestonePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
            packet -> Optional.ofNullable(packet.milestoneId()),
            optionalId -> new ServerboundSetTargetMilestonePacket(optionalId.orElse(null))
    );

    /**
     * Handles the packet's logic on the server side.
     */
    public static void handle(final ServerboundSetTargetMilestonePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Arffornia.LOGGER.info("Received request from player {} to target milestone {}", player.getName().getString(), packet.milestoneId() != null ? packet.milestoneId() : "NONE");
                ArfforniaApiService.getInstance().setTargetMilestone(player.getUUID(), packet.milestoneId());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}