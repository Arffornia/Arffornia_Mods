package fr.thegostsniperfr.arffornia.crate;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/** How exciting a roll is: it picks the beam colour and the extras of the opening sequence. Ordered from least to most. */
public enum CrateRarity implements StringRepresentable {
    COMMON("common", 0xFFFFFF, ChatFormatting.WHITE),
    UNCOMMON("uncommon", 0x55AAFF, ChatFormatting.BLUE),
    RARE("rare", 0xB266FF, ChatFormatting.LIGHT_PURPLE),
    LEGENDARY("legendary", 0xFFB300, ChatFormatting.GOLD);

    public static final Codec<CrateRarity> CODEC = StringRepresentable.fromEnum(CrateRarity::values);
    private static final IntFunction<CrateRarity> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, CrateRarity> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    private final String name;
    private final int colour;
    private final ChatFormatting chatColour;

    CrateRarity(String name, int colour, ChatFormatting chatColour) {
        this.name = name;
        this.colour = colour;
        this.chatColour = chatColour;
    }

    public int colour() {
        return colour;
    }

    public ChatFormatting chatColour() {
        return chatColour;
    }

    public boolean atLeast(CrateRarity other) {
        return ordinal() >= other.ordinal();
    }

    public static CrateRarity max(CrateRarity a, CrateRarity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
