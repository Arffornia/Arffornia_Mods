package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateClientHooks;
import fr.thegostsniperfr.arffornia.crate.CrateRarity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * What a crate can give, with the odds computed by the server from its loot table, and for the streak crate the week
 * calendar ({@code hasCalendar} with an empty {@code calendar} means the website could not answer).
 */
public record ClientboundCratePreviewPacket(ResourceLocation crate, Component name, CrateRarity rarity, List<Entry> entries,
                                            boolean hasCalendar, Optional<Calendar> calendar) implements CustomPacketPayload {
    public static final Type<ClientboundCratePreviewPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crate_preview"));

    /** {@code chance}: probability, 0 to 1, that one opening gives this item. */
    public record Entry(ItemStack stack, float chance, int minCount, int maxCount, CrateRarity rarity) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Entry::stack,
                ByteBufCodecs.FLOAT, Entry::chance,
                ByteBufCodecs.VAR_INT, Entry::minCount,
                ByteBufCodecs.VAR_INT, Entry::maxCount,
                CrateRarity.STREAM_CODEC, Entry::rarity,
                Entry::new);
    }

    public record Day(int day, List<ItemStack> contents) {
        static final StreamCodec<RegistryFriendlyByteBuf, Day> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Day::day,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, Day::contents,
                Day::new);
    }

    /** {@code today}: the streak day today is; days up to {@code dayStreak} are done. */
    public record Calendar(int dayStreak, int bestStreak, int today, List<Day> week) {
        static final StreamCodec<RegistryFriendlyByteBuf, Calendar> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Calendar::dayStreak,
                ByteBufCodecs.VAR_INT, Calendar::bestStreak,
                ByteBufCodecs.VAR_INT, Calendar::today,
                Day.STREAM_CODEC.apply(ByteBufCodecs.list()), Calendar::week,
                Calendar::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCratePreviewPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ClientboundCratePreviewPacket::crate,
            ComponentSerialization.STREAM_CODEC, ClientboundCratePreviewPacket::name,
            CrateRarity.STREAM_CODEC, ClientboundCratePreviewPacket::rarity,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundCratePreviewPacket::entries,
            ByteBufCodecs.BOOL, ClientboundCratePreviewPacket::hasCalendar,
            ByteBufCodecs.optional(Calendar.STREAM_CODEC), ClientboundCratePreviewPacket::calendar,
            ClientboundCratePreviewPacket::new);

    public static void handle(ClientboundCratePreviewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CrateClientHooks.openPreview(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
