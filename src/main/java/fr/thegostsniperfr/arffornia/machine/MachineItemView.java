package fr.thegostsniperfr.arffornia.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * What automation sees through a face: input slots it may fill, output slots it may drain, or both.
 */
public final class MachineItemView implements IItemHandler {
    private final IItemHandlerModifiable items;
    private final SlotLayout layout;
    private final int first;
    private final int count;

    private MachineItemView(IItemHandlerModifiable items, SlotLayout layout, int first, int count) {
        this.items = items;
        this.layout = layout;
        this.first = first;
        this.count = count;
    }

    /** Null for {@link SideMode#NONE}, as capability providers expect. */
    public static IItemHandler of(IItemHandlerModifiable items, SlotLayout layout, SideMode mode) {
        return switch (mode) {
            case NONE -> null;
            case INPUT -> new MachineItemView(items, layout, 0, layout.inputs());
            case OUTPUT -> new MachineItemView(items, layout, layout.inputs(), layout.outputs());
            case BOTH -> new MachineItemView(items, layout, 0, layout.size());
        };
    }

    @Override
    public int getSlots() {
        return count;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return items.getStackInSlot(first + slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!layout.isInput(first + slot)) {
            return stack;
        }
        return items.insertItem(first + slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!layout.isOutput(first + slot)) {
            return ItemStack.EMPTY;
        }
        return items.extractItem(first + slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return items.getSlotLimit(first + slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return layout.isInput(first + slot) && items.isItemValid(first + slot, stack);
    }
}
