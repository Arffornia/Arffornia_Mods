package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.world.level.block.Block;

/** A block placed in a structure where items or energy go in or out. Inert until the structure is formed. */
public class PortBlock extends Block {
    private final PortKind kind;

    public PortBlock(PortKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public PortKind kind() {
        return kind;
    }
}
