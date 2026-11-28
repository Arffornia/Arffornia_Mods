package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.multiblock.BlockPlacement;
import fr.thegostsniperfr.arffornia.multiblock.CuboidShape;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinition;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinitions;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMasterBlock;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockShape;
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
 * The structures the server knows, so the client can draw the blocks still missing around a trigger: a template's
 * blocks, or a cuboid's rules. Other shapes are not sent and get no hologram.
 */
public record ClientboundMultiblocksPacket(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ClientboundMultiblocksPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "multiblocks"));

    /** Exactly one of {@code template} and {@code cuboid} is present. */
    public record Entry(ResourceLocation id, Block trigger, Block master, Optional<String> stage,
                        Optional<List<BlockPlacement>> template, Optional<CuboidShape> cuboid) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.registry(Registries.BLOCK), Entry::trigger,
                ByteBufCodecs.registry(Registries.BLOCK), Entry::master,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), Entry::stage,
                ByteBufCodecs.optional(BlockPlacement.STREAM_CODEC.apply(ByteBufCodecs.list())), Entry::template,
                ByteBufCodecs.optional(CuboidShape.STREAM_CODEC), Entry::cuboid,
                Entry::new);

        Optional<MultiblockShape> shape() {
            return template.<MultiblockShape>map(blocks -> TemplateShape.of(blocks, trigger)).or(() -> cuboid.map(MultiblockShape.class::cast));
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMultiblocksPacket> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(ClientboundMultiblocksPacket::new, ClientboundMultiblocksPacket::entries);

    public static ClientboundMultiblocksPacket of(Collection<MultiblockDefinition> definitions) {
        return new ClientboundMultiblocksPacket(definitions.stream()
                .filter(definition -> definition.shape() instanceof TemplateShape || definition.shape() instanceof CuboidShape)
                .map(definition -> new Entry(definition.id(), definition.trigger(), definition.master(), definition.stage(),
                        definition.shape() instanceof TemplateShape template ? Optional.of(template.blocks()) : Optional.empty(),
                        definition.shape() instanceof CuboidShape cuboid ? Optional.of(cuboid) : Optional.empty()))
                .toList());
    }

    public static void handle(ClientboundMultiblocksPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MultiblockDefinitions.setClient(packet.entries().stream()
                .filter(entry -> entry.master() instanceof MultiblockMasterBlock && entry.shape().isPresent())
                .map(entry -> new MultiblockDefinition(entry.id(), entry.shape().get(), entry.trigger(),
                        (MultiblockMasterBlock) entry.master(), entry.stage()))
                .toList()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
