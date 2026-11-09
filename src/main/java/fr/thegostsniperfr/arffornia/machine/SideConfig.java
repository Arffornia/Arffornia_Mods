package fr.thegostsniperfr.arffornia.machine;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-face automation mode, cycled with the Wrench and saved with the block entity. Only the modes in {@code cycle}
 * can be set, so an input port can be switched off but never turned into an output.
 */
public final class SideConfig {
    private final List<SideMode> cycle;
    private final Map<Direction, SideMode> modes = new EnumMap<>(Direction.class);

    public SideConfig(List<SideMode> cycle) {
        if (cycle.isEmpty()) {
            throw new IllegalArgumentException("a side config needs at least one mode");
        }
        this.cycle = List.copyOf(cycle);
        for (Direction direction : Direction.values()) {
            modes.put(direction, this.cycle.getFirst());
        }
    }

    public SideMode get(Direction face) {
        return modes.get(face);
    }

    public SideMode cycle(Direction face) {
        int next = (cycle.indexOf(modes.get(face)) + 1) % cycle.size();
        modes.put(face, cycle.get(next));
        return cycle.get(next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        modes.forEach((face, mode) -> tag.putString(face.getSerializedName(), mode.getSerializedName()));
        return tag;
    }

    /** Restores saved modes; a face whose saved mode is unknown or no longer allowed keeps its current mode. */
    public void load(CompoundTag tag) {
        for (Direction face : Direction.values()) {
            String saved = tag.getString(face.getSerializedName());
            cycle.stream().filter(mode -> mode.getSerializedName().equals(saved)).findFirst()
                    .ifPresent(mode -> modes.put(face, mode));
        }
    }
}
