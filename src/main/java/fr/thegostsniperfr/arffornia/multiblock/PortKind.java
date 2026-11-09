package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.machine.SideMode;

import java.util.List;

/**
 * What a port block lets through once its structure is formed. Each face can be switched off with the Wrench but never
 * reversed. Fluid ports come with the first machine that has a tank.
 */
public enum PortKind {
    ITEM_INPUT(SideMode.INPUT),
    ITEM_OUTPUT(SideMode.OUTPUT),
    ENERGY_INPUT(SideMode.INPUT);

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
}
