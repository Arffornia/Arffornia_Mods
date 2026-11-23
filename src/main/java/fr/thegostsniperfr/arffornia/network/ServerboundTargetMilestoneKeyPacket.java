package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.progression.web.TargetMilestones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/** Targets a milestone by key from the in-game progression page; empty clears the target. Answered with the outcome. */
public record ServerboundTargetMilestoneKeyPacket(Optional<String> milestoneKey) implements CustomPacketPayload {
    static final long COOLDOWN_MS = 500;

    public static final Type<ServerboundTargetMilestoneKeyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "target_milestone_key")
    );

    public static final StreamCodec<ByteBuf, ServerboundTargetMilestoneKeyPacket> STREAM_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(256)).map(ServerboundTargetMilestoneKeyPacket::new, ServerboundTargetMilestoneKeyPacket::milestoneKey);

    public static void handle(final ServerboundTargetMilestoneKeyPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            String key = packet.milestoneKey().orElse(null);
            if ((key != null && !ServerPayloadChecks.isMilestoneKey(key)) || !ServerPayloadChecks.allowRequest(player, "target", COOLDOWN_MS)) {
                PacketDistributor.sendToPlayer(player, new ClientboundTargetMilestoneResultPacket(packet.milestoneKey(), false));
                return;
            }
            TargetMilestones.request(player, key).thenAccept(accepted -> {
                if (!player.hasDisconnected()) {
                    PacketDistributor.sendToPlayer(player, new ClientboundTargetMilestoneResultPacket(packet.milestoneKey(), accepted));
                }
            });
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
