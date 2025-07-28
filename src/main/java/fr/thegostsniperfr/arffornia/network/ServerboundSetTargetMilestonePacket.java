package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundSetTargetMilestonePacket(int milestoneId) implements CustomPacketPayload {

    public static final Type<ServerboundSetTargetMilestonePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "set_target_milestone")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSetTargetMilestonePacket> STREAM_CODEC = new StreamCodec<>() {
        /**
         * Decoder: Reads data from the buffer and creates a new packet instance.
         * @param buf The buffer to read from.
         * @return A new instance of ServerboundSetTargetMilestonePacket.
         */
        @Override
        public ServerboundSetTargetMilestonePacket decode(RegistryFriendlyByteBuf buf) {
            return new ServerboundSetTargetMilestonePacket(buf.readInt());
        }

        /**
         * Encoder: Writes data from the packet instance to the buffer.
         * @param buf The buffer to write to.
         * @param packet The packet instance containing the data.
         */
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ServerboundSetTargetMilestonePacket packet) {
            buf.writeInt(packet.milestoneId());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the packet's logic on the server side.
     */
    public static void handle(final ServerboundSetTargetMilestonePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Arffornia.LOGGER.info("Received request from player {} to target milestone {}", player.getName().getString(), packet.milestoneId());
                Arffornia.ARFFORNA_API_SERVICE.setTargetMilestone(player.getUUID(), packet.milestoneId);
            }
        });
    }
}