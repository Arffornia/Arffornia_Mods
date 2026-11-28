package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.Map;

/** A block placed in a structure where items or energy go in or out. Inert until the structure is formed. */
public class PortBlock extends Block {
    private final PortKind kind;

    public PortBlock(PortKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    private static volatile Map<PortKind, Block> byKind;

    /** The port block of {@code kind}, for holograms. */
    public static Block of(PortKind kind) {
        if (byKind == null) {
            Map<PortKind, Block> map = new EnumMap<>(PortKind.class);
            BuiltInRegistries.BLOCK.stream().filter(PortBlock.class::isInstance).map(PortBlock.class::cast)
                    .forEach(port -> map.putIfAbsent(port.kind(), port));
            byKind = map;
        }
        return byKind.get(kind);
    }

    public PortKind kind() {
        return kind;
    }
}
