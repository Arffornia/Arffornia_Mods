package fr.thegostsniperfr.arffornia.multiblock;

import com.mojang.serialization.Codec;
import fr.thegostsniperfr.arffornia.machine.SideMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;

/**
 * What a port block lets through once its structure is formed. Each face can be switched off with the Wrench but never
 * reversed. Fluid ports come with the first machine that has a tank.
 */
public enum PortKind implements StringRepresentable {
    ITEM_INPUT(SideMode.INPUT),
    ITEM_OUTPUT(SideMode.OUTPUT),
    ENERGY_INPUT(SideMode.INPUT);

    public static final Codec<PortKind> CODEC = StringRepresentable.fromEnum(PortKind::values);
    public static final StreamCodec<ByteBuf, PortKind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], PortKind::ordinal);

    private final SideMode mode;

    PortKind(SideMode mode) {
        this.mode = mode;
    }

    public List<SideMode> sideCycle() {
        return List.of(mode, SideMode.NONE);
    }

    public boolean carriesItems() {
        return this == ITEM_INPUT || this == ITEM_OUTPUT;
    }

    public boolean carriesEnergy() {
        return this == ENERGY_INPUT;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
