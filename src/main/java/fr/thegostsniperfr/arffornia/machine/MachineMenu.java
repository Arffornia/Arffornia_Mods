package fr.thegostsniperfr.arffornia.machine;

import fr.thegostsniperfr.arffornia.screen.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The menu every machine shares: its slots laid out from the {@link SlotLayout}, the player inventory and the
 * {@link MachineData} numbers.
 */
public class MachineMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 44;
    public static final int OUTPUT_X = 116;
    public static final int SLOTS_Y = 17;
    public static final int COLUMNS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_Y = 84;

    private final SlotLayout layout;
    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final MachineBlockEntity machine;

    public MachineMenu(int containerId, Inventory inventory, MachineBlockEntity machine) {
        this(containerId, inventory, machine.getBlockPos(), machine.type().layout(), machine.items(), machine.data(), machine);
    }

    public MachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), SlotLayout.STREAM_CODEC.decode(extraData));
    }

    private MachineMenu(int containerId, Inventory inventory, BlockPos pos, SlotLayout layout) {
        this(containerId, inventory, pos, layout, new ItemStackHandler(layout.size()), new SimpleContainerData(MachineData.COUNT), null);
    }

    private MachineMenu(int containerId, Inventory inventory, BlockPos pos, SlotLayout layout, IItemHandlerModifiable items,
                        ContainerData data, @Nullable MachineBlockEntity machine) {
        super(ModMenuTypes.MACHINE_MENU.get(), containerId);
        this.layout = layout;
        this.pos = pos;
        this.data = data;
        this.machine = machine;
        for (int i = 0; i < layout.inputs(); i++) {
            addSlot(new SlotItemHandler(items, i, INPUT_X + (i % COLUMNS) * SLOT_SIZE, SLOTS_Y + (i / COLUMNS) * SLOT_SIZE));
        }
        for (int i = 0; i < layout.outputs(); i++) {
            addSlot(new SlotItemHandler(items, layout.outputSlot(i), OUTPUT_X + (i % 2) * SLOT_SIZE, SLOTS_Y + (i / 2) * SLOT_SIZE) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * SLOT_SIZE, PLAYER_INVENTORY_Y + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * SLOT_SIZE, PLAYER_INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, SlotLayout layout) {
        buf.writeBlockPos(pos);
        SlotLayout.STREAM_CODEC.encode(buf, layout);
    }

    public SlotLayout layout() {
        return layout;
    }

    public BlockPos pos() {
        return pos;
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack moved = source.copy();
        int machineSlots = layout.size();
        boolean fromMachine = index < machineSlots;
        if (fromMachine ? !moveItemStackTo(source, machineSlots, slots.size(), true) : !moveItemStackTo(source, 0, layout.inputs(), false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (source.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, source);
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine == null || machine.stillValid(player);
    }
}
