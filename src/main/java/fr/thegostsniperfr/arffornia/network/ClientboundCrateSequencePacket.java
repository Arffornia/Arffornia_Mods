package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateBlockEntity;
import fr.thegostsniperfr.arffornia.crate.CrateSequence;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One packet per opening (and one more on a skip): every viewer plays the same show from {@code tick}. */
public record ClientboundCrateSequencePacket(BlockPos pos, CrateSequence.Show show, int tick) implements CustomPacketPayload {
    public static final Type<ClientboundCrateSequencePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crate_sequence"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCrateSequencePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ClientboundCrateSequencePacket::pos,
            CrateSequence.Show.STREAM_CODEC, ClientboundCrateSequencePacket::show,
            ByteBufCodecs.VAR_INT, ClientboundCrateSequencePacket::tick,
            ClientboundCrateSequencePacket::new);

    public static void handle(ClientboundCrateSequencePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(packet.pos()) instanceof CrateBlockEntity crate) {
                crate.play(packet.show(), packet.tick());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
