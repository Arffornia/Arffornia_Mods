package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.ClientProgressionData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundUpdateTargetNamePacket(String targetName) implements CustomPacketPayload {

    public static final Type<ClientboundUpdateTargetNamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "update_target_name")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateTargetNamePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ClientboundUpdateTargetNamePacket::targetName,
            ClientboundUpdateTargetNamePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ClientboundUpdateTargetNamePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientProgressionData.currentMilestoneTarget = packet.targetName();
        });
    }
}