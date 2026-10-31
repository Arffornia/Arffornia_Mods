package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.screen.SpaceElevatorMenu;
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
                processOnServer(player, packet);
            }
        });
    }

    /**
     * Starts a launch after validating that the player has this elevator's menu open and is close to it.
     * Must be called on the server thread.
     *
     * @return true if the launch was handed to the block entity.
     */
    public static boolean processOnServer(ServerPlayer player, ServerboundLaunchElevatorPacket packet) {
        if (!(player.containerMenu instanceof SpaceElevatorMenu menu)
                || !ServerPayloadChecks.isInteractingWith(player, menu, menu.blockEntity, packet.pos())) {
            Arffornia.LOGGER.warn("Rejected Space Elevator launch from {} at {}: menu not open or out of reach.",
                    player.getName().getString(), packet.pos());
            return false;
        }
        menu.blockEntity.launch(player);
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}