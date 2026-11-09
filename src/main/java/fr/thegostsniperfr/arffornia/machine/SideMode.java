package fr.thegostsniperfr.arffornia.machine;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What automation may do through one face of a machine or port. */
public enum SideMode implements StringRepresentable {
    NONE, INPUT, OUTPUT, BOTH;

    public boolean allowsInsert() {
        return this == INPUT || this == BOTH;
    }

    public boolean allowsExtract() {
        return this == OUTPUT || this == BOTH;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.arffornia.machine.side." + getSerializedName();
    }
}
