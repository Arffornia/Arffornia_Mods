package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.SyncedProgressionState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * The receiving player's active progression: completed milestones, target and whether it is retired.
 */
public record ClientboundProgressionStatePacket(ProgressionSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<ClientboundProgressionStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "progression_state")
    );

    private static final StreamCodec<RegistryFriendlyByteBuf, ProgressionSnapshot> SNAPSHOT_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ProgressionSnapshot::id,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), s -> List.copyOf(s.completedMilestones()),
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), s -> Optional.ofNullable(s.currentMilestoneId()),
            ByteBufCodecs.BOOL, ProgressionSnapshot::retired,
            (id, completed, target, retired) -> new ProgressionSnapshot(id, new HashSet<>(completed), target.orElse(null), retired)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundProgressionStatePacket> STREAM_CODEC =
            SNAPSHOT_CODEC.map(ClientboundProgressionStatePacket::new, ClientboundProgressionStatePacket::snapshot);

    public static void handle(final ClientboundProgressionStatePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> SyncedProgressionState.set(packet.snapshot()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
