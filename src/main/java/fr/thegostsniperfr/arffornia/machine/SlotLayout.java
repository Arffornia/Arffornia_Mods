package fr.thegostsniperfr.arffornia.machine;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The item slots of a machine: input slots first, then output slots.
 */
public record SlotLayout(int inputs, int outputs) {
    public static final StreamCodec<ByteBuf, SlotLayout> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SlotLayout::inputs,
            ByteBufCodecs.VAR_INT, SlotLayout::outputs,
            SlotLayout::new);

    public SlotLayout {
        if (inputs < 1 || outputs < 1) {
            throw new IllegalArgumentException("a machine needs at least one input and one output slot");
        }
    }

    public int size() {
        return inputs + outputs;
    }

    public boolean isInput(int slot) {
        return slot >= 0 && slot < inputs;
    }

    public boolean isOutput(int slot) {
        return slot >= inputs && slot < size();
    }

    public int outputSlot(int index) {
        return inputs + index;
    }
}
