package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateDefinition;
import fr.thegostsniperfr.arffornia.crate.CrateDefinitions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** The crates the server knows, so the client can name and draw keys and crates. */
public record ClientboundCrateDefinitionsPacket(List<CrateDefinition.Client> crates) implements CustomPacketPayload {
    public static final Type<ClientboundCrateDefinitionsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crate_definitions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCrateDefinitionsPacket> STREAM_CODEC =
            CrateDefinition.Client.LIST_STREAM_CODEC.map(ClientboundCrateDefinitionsPacket::new, ClientboundCrateDefinitionsPacket::crates);

    public static void handle(ClientboundCrateDefinitionsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CrateDefinitions.setClient(packet.crates()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
