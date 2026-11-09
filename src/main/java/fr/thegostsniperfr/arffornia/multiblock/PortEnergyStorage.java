package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.machine.SideMode;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** The energy side of a port, resolved on every call like {@link PortItemHandler}. */
final class PortEnergyStorage implements IEnergyStorage {
    private final MultiblockPartBlockEntity part;
    private final SideMode mode;

    PortEnergyStorage(MultiblockPartBlockEntity part, SideMode mode) {
        this.part = part;
        this.mode = mode;
    }

    @Nullable
    private IEnergyStorage target() {
        MultiblockMachineBlockEntity machine = part.isRemoved() ? null : part.machine();
        return machine != null ? machine.energyView(mode) : null;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        IEnergyStorage target = target();
        return target != null ? target.receiveEnergy(toReceive, simulate) : 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        IEnergyStorage target = target();
        return target != null ? target.extractEnergy(toExtract, simulate) : 0;
    }

    @Override
    public int getEnergyStored() {
        IEnergyStorage target = target();
        return target != null ? target.getEnergyStored() : 0;
    }

    @Override
    public int getMaxEnergyStored() {
        IEnergyStorage target = target();
        return target != null ? target.getMaxEnergyStored() : 0;
    }

    @Override
    public boolean canExtract() {
        IEnergyStorage target = target();
        return target != null && target.canExtract();
    }

    @Override
    public boolean canReceive() {
        IEnergyStorage target = target();
        return target != null && target.canReceive();
    }
}
