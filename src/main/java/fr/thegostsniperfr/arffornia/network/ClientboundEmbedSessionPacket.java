package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.progression.web.ProgressionWebHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** The in-game progression page URL and the player's read-only token; an empty token when the server has none. */
public record ClientboundEmbedSessionPacket(String url, String token, long expiresAt) implements CustomPacketPayload {
    public static final Type<ClientboundEmbedSessionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "embed_session_token")
    );

    public static final StreamCodec<ByteBuf, ClientboundEmbedSessionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(512), ClientboundEmbedSessionPacket::url,
            ByteBufCodecs.stringUtf8(1024), ClientboundEmbedSessionPacket::token,
            ByteBufCodecs.VAR_LONG, ClientboundEmbedSessionPacket::expiresAt,
            ClientboundEmbedSessionPacket::new
    );

    static ClientboundEmbedSessionPacket of(@Nullable ArfforniaApiDtos.EmbedToken token) {
        return token == null || token.url().length() > 512 || token.token().length() > 1024
                ? new ClientboundEmbedSessionPacket("", "", 0)
                : new ClientboundEmbedSessionPacket(token.url(), token.token(), token.expiresAt());
    }

    public static void handle(final ClientboundEmbedSessionPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> ProgressionWebHooks.client().onSession(packet.url(), packet.token(), packet.expiresAt()));
    }

    @Override
    public String toString() {
        return "ClientboundEmbedSessionPacket[url=" + url + ", expiresAt=" + expiresAt + "]";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
