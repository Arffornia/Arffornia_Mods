package fr.thegostsniperfr.arffornia.machine;

import net.minecraft.world.inventory.ContainerData;

/**
 * The numbers a machine screen shows. Menus sync data slots as 16-bit shorts, so energy values travel as two halves.
 */
public final class MachineData {
    public static final int PROGRESS = 0;
    public static final int MAX_PROGRESS = 1;
    public static final int ENERGY_LOW = 2;
    public static final int ENERGY_HIGH = 3;
    public static final int CAPACITY_LOW = 4;
    public static final int CAPACITY_HIGH = 5;
    public static final int STATUS = 6;
    public static final int COUNT = 7;

    private MachineData() {
    }

    public static int low(int value) {
        return value & 0xFFFF;
    }

    public static int high(int value) {
        return (value >>> 16) & 0xFFFF;
    }

    public static int join(int low, int high) {
        return (high & 0xFFFF) << 16 | (low & 0xFFFF);
    }

    public static int energy(ContainerData data) {
        return join(data.get(ENERGY_LOW), data.get(ENERGY_HIGH));
    }

    public static int capacity(ContainerData data) {
        return join(data.get(CAPACITY_LOW), data.get(CAPACITY_HIGH));
    }

    public static MachineStatus status(ContainerData data) {
        return MachineStatus.byOrdinal(data.get(STATUS));
    }
}
