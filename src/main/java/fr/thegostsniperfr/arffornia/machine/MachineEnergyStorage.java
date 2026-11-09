package fr.thegostsniperfr.arffornia.machine;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * A consumer's buffer: cables may fill it but never drain it, and saved values are clamped to the capacity.
 */
public class MachineEnergyStorage extends EnergyStorage {
    private final Runnable onChanged;

    public MachineEnergyStorage(int capacity, int maxReceive, Runnable onChanged) {
        super(capacity, maxReceive, 0);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int received = super.receiveEnergy(toReceive, simulate);
        if (received > 0 && !simulate) {
            onChanged.run();
        }
        return received;
    }

    /** Draws {@code amount} for the machine's own work, all or nothing. */
    public boolean consume(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        onChanged.run();
        return true;
    }

    public void setEnergy(int value) {
        energy = Mth.clamp(value, 0, capacity);
    }

    /**
     * Energy drawn per tick so that a craft of {@code ticks} ticks consumes at least {@code totalEnergy} FE.
     * Rounds up: a recipe whose energy is lower than its duration still costs 1 FE per tick.
     */
    public static int perTick(int totalEnergy, int ticks) {
        if (totalEnergy <= 0) {
            return 0;
        }
        if (ticks <= 0) {
            return totalEnergy;
        }
        return (int) ((totalEnergy + (long) ticks - 1) / ticks);
    }
}
