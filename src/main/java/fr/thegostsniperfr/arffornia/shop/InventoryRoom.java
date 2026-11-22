package fr.thegostsniperfr.arffornia.shop;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Room in the 36 main inventory slots. The check and the delivery fill slots the same way (top up matching stacks,
 * then empty slots), so a reward that was found to fit is given whole. Nothing here ever drops an item.
 */
public final class InventoryRoom {
    public static final int MAIN_SLOTS = 36;

    private InventoryRoom() {
    }

    /** A copy of the main slots, to try rewards on. */
    public static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> slots = new ArrayList<>(MAIN_SLOTS);
        for (int i = 0; i < MAIN_SLOTS; i++) {
            slots.add(inventory.getItem(i).copy());
        }
        return slots;
    }

    public static List<ItemStack> empty() {
        List<ItemStack> slots = new ArrayList<>(MAIN_SLOTS);
        for (int i = 0; i < MAIN_SLOTS; i++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }

    public static List<ItemStack> copy(List<ItemStack> slots) {
        return new ArrayList<>(slots.stream().map(ItemStack::copy).toList());
    }

    /** How many more slots {@code stacks} need in this inventory; 0 when they all fit. */
    public static int slotsMissing(Inventory inventory, List<ItemStack> stacks) {
        return insert(snapshot(inventory), stacks);
    }

    /** Puts {@code stacks} into {@code slots} and returns the slots still missing for what did not fit. */
    public static int insert(List<ItemStack> slots, List<ItemStack> stacks) {
        return slotsFor(fill(slots, stacks));
    }

    /** Gives the stacks to the main inventory and returns what did not fit, for the caller to keep. */
    public static List<ItemStack> give(Inventory inventory, List<ItemStack> stacks) {
        NonNullList<ItemStack> main = inventory.items;
        List<ItemStack> leftovers = fill(main.subList(0, MAIN_SLOTS), stacks);
        inventory.setChanged();
        return leftovers;
    }

    /** Full slots a list of stacks takes once merged into an empty inventory. */
    public static int slotsFor(List<ItemStack> stacks) {
        int slots = 0;
        for (ItemStack stack : stacks) {
            slots += (stack.getCount() + stack.getMaxStackSize() - 1) / stack.getMaxStackSize();
        }
        return slots;
    }

    private static List<ItemStack> fill(List<ItemStack> slots, List<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack original : stacks) {
            if (original.isEmpty()) {
                continue;
            }
            ItemStack stack = original.copy();
            for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
                ItemStack slot = slots.get(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                    int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    stack.shrink(moved);
                }
            }
            for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
                if (slots.get(i).isEmpty()) {
                    slots.set(i, stack.split(stack.getMaxStackSize()));
                }
            }
            if (!stack.isEmpty()) {
                leftovers.add(stack);
            }
        }
        return leftovers;
    }
}
