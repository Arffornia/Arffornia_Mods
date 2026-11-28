package fr.thegostsniperfr.arffornia.screen;

import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.machine.MachineData;
import fr.thegostsniperfr.arffornia.machine.MachineTypes;
import fr.thegostsniperfr.arffornia.machine.SlotLayout;
import fr.thegostsniperfr.arffornia.recipe.CrafterRecipes;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Crafter's own layout around the machine slots and {@link MachineData}: a recipe list on the left, the grid on the right. */
public class CrafterMenu extends AbstractContainerMenu {
    private static final SlotLayout LAYOUT = MachineTypes.CRAFTER.layout();
    private static final int PROGRESS_ARROW_WIDTH = 24;

    public final CrafterBlockEntity blockEntity;
    public final List<RecipeHolder<MachineRecipe>> availableRecipes = new ArrayList<>();
    private final ContainerData data;

    public CrafterMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, (CrafterBlockEntity) inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(MachineData.COUNT));
        Set<String> unlockedStages = extraData.readCollection(HashSet::new, FriendlyByteBuf::readUtf);
        this.availableRecipes.addAll(CrafterRecipes.unlocked(inv.player.level().getRecipeManager(), unlockedStages));
    }

    public CrafterMenu(int containerId, Inventory inv, CrafterBlockEntity blockEntity) {
        this(containerId, inv, blockEntity, blockEntity.data());
    }

    private CrafterMenu(int containerId, Inventory inv, CrafterBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.CRAFTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        addSlots(inv);
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, Set<String> unlockedStages) {
        buf.writeBlockPos(pos);
        buf.writeCollection(unlockedStages, FriendlyByteBuf::writeUtf);
    }

    private void addSlots(Inventory inv) {
        int mainPanelXOffset = 79;
        int gridX = mainPanelXOffset + 9;
        int gridY = 18;
        for (int i = 0; i < LAYOUT.inputs(); i++) {
            addSlot(new SlotItemHandler(blockEntity.items(), i, gridX + (i % 3) * 18, gridY + (i / 3) * 18));
        }
        for (int i = 0; i < LAYOUT.outputs(); i++) {
            addSlot(new SlotItemHandler(blockEntity.items(), LAYOUT.outputSlot(i), mainPanelXOffset + 126, 27 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        int playerInvX = 8 + 80;
        int playerInvY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                addSlot(new Slot(inv, column + row * 9 + 9, playerInvX + column * 18, playerInvY + row * 18));
            }
        }
        for (int column = 0; column < 9; ++column) {
            addSlot(new Slot(inv, column, playerInvX + column * 18, playerInvY + 58));
        }
    }

    public int getScaledProgress() {
        return data.get(MachineData.PROGRESS) * PROGRESS_ARROW_WIDTH / MachineData.PROGRESS_SCALE;
    }

    public int getEnergy() {
        return MachineData.energy(data);
    }

    public int getCapacity() {
        return MachineData.capacity(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack moved = source.copy();
        int machineSlots = LAYOUT.size();
        boolean fromMachine = index < machineSlots;
        if (fromMachine ? !moveItemStackTo(source, machineSlots, slots.size(), true) : !moveItemStackTo(source, 0, LAYOUT.inputs(), false)) {
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
        return blockEntity.stillValid(player);
    }
}
