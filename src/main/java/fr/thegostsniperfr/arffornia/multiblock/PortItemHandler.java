package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.machine.SideMode;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * What a port exposes: it finds the master's inventory on every call instead of holding it, so a capability cached by a
 * pipe stays valid when the master's chunk reloads (a new handler) and is empty while the master is not loaded.
 */
final class PortItemHandler implements IItemHandler {
    private final MultiblockPartBlockEntity part;
    private final SideMode mode;

    PortItemHandler(MultiblockPartBlockEntity part, SideMode mode) {
        this.part = part;
        this.mode = mode;
    }

    @Nullable
    private IItemHandler target() {
        MultiblockMachineBlockEntity machine = part.isRemoved() ? null : part.machine();
        return machine != null ? machine.itemView(mode) : null;
    }

    @Override
    public int getSlots() {
        IItemHandler target = target();
        return target != null ? target.getSlots() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        IItemHandler target = target();
        return target != null && slot < target.getSlots() ? target.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        IItemHandler target = target();
        return target != null && slot < target.getSlots() ? target.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        IItemHandler target = target();
        return target != null && slot < target.getSlots() ? target.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        IItemHandler target = target();
        return target != null && slot < target.getSlots() ? target.getSlotLimit(slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        IItemHandler target = target();
        return target != null && slot < target.getSlots() && target.isItemValid(slot, stack);
    }
}
