package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.multiblock.BlockPlacement;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinition;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinitions;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMasterBlock;
import fr.thegostsniperfr.arffornia.multiblock.TemplateShape;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The template structures the server knows, so the client can draw the blocks still missing around a trigger.
 * Structures without a fixed template are not sent and get no hologram.
 */
public record ClientboundMultiblocksPacket(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ClientboundMultiblocksPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "multiblocks"));

    public record Entry(ResourceLocation id, Block trigger, Block master, Optional<String> stage, List<BlockPlacement> blocks) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.registry(Registries.BLOCK), Entry::trigger,
                ByteBufCodecs.registry(Registries.BLOCK), Entry::master,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), Entry::stage,
                BlockPlacement.STREAM_CODEC.apply(ByteBufCodecs.list()), Entry::blocks,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMultiblocksPacket> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(ClientboundMultiblocksPacket::new, ClientboundMultiblocksPacket::entries);

    public static ClientboundMultiblocksPacket of(Collection<MultiblockDefinition> definitions) {
        return new ClientboundMultiblocksPacket(definitions.stream()
                .filter(definition -> definition.shape() instanceof TemplateShape)
                .map(definition -> new Entry(definition.id(), definition.trigger(), definition.master(), definition.stage(),
                        ((TemplateShape) definition.shape()).blocks()))
                .toList());
    }

    public static void handle(ClientboundMultiblocksPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MultiblockDefinitions.setClient(packet.entries().stream()
                .filter(entry -> entry.master() instanceof MultiblockMasterBlock)
                .map(entry -> new MultiblockDefinition(entry.id(), TemplateShape.of(entry.blocks(), entry.trigger()), entry.trigger(),
                        (MultiblockMasterBlock) entry.master(), entry.stage()))
                .toList()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
