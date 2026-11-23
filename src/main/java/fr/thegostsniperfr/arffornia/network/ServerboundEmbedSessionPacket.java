package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.progression.web.EmbedSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The client asks for the player's read-only token of the in-game progression page; {@code renew} after the page got a 401. */
public record ServerboundEmbedSessionPacket(boolean renew) implements CustomPacketPayload {
    /** Each mint is one website call; a cached token is served whatever the cooldown. */
    static final long MINT_COOLDOWN_MS = 5_000;

    public static final Type<ServerboundEmbedSessionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "embed_session")
    );

    public static final StreamCodec<ByteBuf, ServerboundEmbedSessionPacket> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ServerboundEmbedSessionPacket::new, ServerboundEmbedSessionPacket::renew);

    public static void handle(final ServerboundEmbedSessionPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean mayMint = ServerPayloadChecks.allowRequest(player, "embed_session", MINT_COOLDOWN_MS);
            EmbedSessions.forPlayer(player.getUUID(), packet.renew(), mayMint).thenAcceptAsync(token -> {
                if (!player.hasDisconnected()) {
                    PacketDistributor.sendToPlayer(player, ClientboundEmbedSessionPacket.of(token));
                }
            }, player.server);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
