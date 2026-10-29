package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.ClientProgressionData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sends the player's current milestone target to the client HUD. The name is a {@link Component} so the
 * server can send either a literal milestone name (API data) or a translation key + args for fallbacks;
 * the client renders it in its own language.
 */
public record ClientboundUpdateTargetNamePacket(Component targetName) implements CustomPacketPayload {

    public static final Type<ClientboundUpdateTargetNamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "update_target_name")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateTargetNamePacket> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC,
            ClientboundUpdateTargetNamePacket::targetName,
            ClientboundUpdateTargetNamePacket::new
    );

    public static void handle(final ClientboundUpdateTargetNamePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientProgressionData.currentMilestoneTarget = packet.targetName();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}