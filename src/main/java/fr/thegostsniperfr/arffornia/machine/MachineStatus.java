package fr.thegostsniperfr.arffornia.machine;

import java.util.Locale;

/** Why a machine is or is not working, shown in its screen. */
public enum MachineStatus {
    IDLE, RUNNING, NO_ENERGY, OUTPUT_FULL, LOCKED, STOPPED, NOT_FORMED;

    public String translationKey() {
        return "gui.arffornia.machine.status." + name().toLowerCase(Locale.ROOT);
    }

    public static MachineStatus byOrdinal(int ordinal) {
        MachineStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
    }
}
