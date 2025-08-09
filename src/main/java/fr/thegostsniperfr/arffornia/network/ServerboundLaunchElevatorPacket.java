package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundLaunchElevatorPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ServerboundLaunchElevatorPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "launch_elevator")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundLaunchElevatorPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ServerboundLaunchElevatorPacket::pos,
            ServerboundLaunchElevatorPacket::new
    );

    public static void handle(final ServerboundLaunchElevatorPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (player.level().getBlockEntity(packet.pos()) instanceof SpaceElevatorBlockEntity be) {
                    be.launch(player);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}