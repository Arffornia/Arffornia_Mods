package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.progression.web.ProgressionWebHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/** Outcome of {@link ServerboundTargetMilestoneKeyPacket}. */
public record ClientboundTargetMilestoneResultPacket(Optional<String> milestoneKey, boolean accepted) implements CustomPacketPayload {
    public static final Type<ClientboundTargetMilestoneResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "target_milestone_result")
    );

    public static final StreamCodec<ByteBuf, ClientboundTargetMilestoneResultPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(256)), ClientboundTargetMilestoneResultPacket::milestoneKey,
            ByteBufCodecs.BOOL, ClientboundTargetMilestoneResultPacket::accepted,
            ClientboundTargetMilestoneResultPacket::new
    );

    public static void handle(final ClientboundTargetMilestoneResultPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> ProgressionWebHooks.client().onTargetResult(packet.milestoneKey().orElse(null), packet.accepted()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
