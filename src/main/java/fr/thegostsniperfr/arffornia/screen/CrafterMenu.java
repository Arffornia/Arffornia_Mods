package fr.thegostsniperfr.arffornia.screen;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.recipe.ClientRecipeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CrafterMenu extends AbstractContainerMenu {
    public final CrafterBlockEntity blockEntity;
    public final List<ArfforniaApiDtos.CustomRecipe> availableRecipes;

    public CrafterMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));

        int[] unlockedIds = extraData.readVarIntArray();
        Set<Integer> unlockedMilestoneIds = Arrays.stream(unlockedIds).boxed().collect(Collectors.toSet());

        this.availableRecipes.clear();
        this.availableRecipes.addAll(ClientRecipeCache.getRecipesByMilestoneIds(unlockedMilestoneIds));
    }

    public CrafterMenu(int pContainerId, Inventory inv, BlockEntity entity) {
        super(ModMenuTypes.CRAFTER_MENU.get(), pContainerId);
        this.blockEntity = (CrafterBlockEntity) entity;
        this.availableRecipes = new ArrayList<>();

        addSlots(inv);
        addDataSlots();
    }

    private void addSlots(Inventory inv) {
        int mainPanelXOffset = 80;

        // 3x3 Crafting Grid
        int gridX = mainPanelXOffset + 18;
        int gridY = 17;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new SlotItemHandler(this.blockEntity.itemHandler, col + row * 3, gridX + col * 18, gridY + row * 18));
            }
        }

        // Output Slots
        this.addSlot(new SlotItemHandler(this.blockEntity.itemHandler, 9, mainPanelXOffset + 124, 26) {
            @Override
            public boolean mayPlace(@NotNull ItemStack s) { return false; }
        });
        this.addSlot(new SlotItemHandler(this.blockEntity.itemHandler, 10, mainPanelXOffset + 124, 44) {
            @Override
            public boolean mayPlace(@NotNull ItemStack s) { return false; }
        });

        int playerInvX = 8 + 80;
        int playerInvY = 84;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, playerInvX + j * 18, playerInvY + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inv, i, playerInvX + i * 18, playerInvY + 58));
        }
    }

    private void addDataSlots() {
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getProgress(); }
            @Override public void set(int value) { blockEntity.setProgress(value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getMaxProgress(); }
            @Override public void set(int value) { blockEntity.setMaxProgress(value); }
        });

        addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getClientEnergyForDataSlot() & 0xFFFF; }
            @Override public void set(int value) {
                int energy = blockEntity.getClientEnergyForDataSlot() & 0xFFFF0000;
                blockEntity.setClientEnergyFromDataSlot(energy | (value & 0xFFFF));
            }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return (blockEntity.getClientEnergyForDataSlot() >> 16) & 0xFFFF; }
            @Override public void set(int value) {
                int energy = blockEntity.getClientEnergyForDataSlot() & 0xFFFF;
                blockEntity.setClientEnergyFromDataSlot(energy | (value << 16));
            }
        });
    }

    public int getScaledProgress() {
        int progress = this.blockEntity.getProgress();
        int maxProgress = this.blockEntity.getMaxProgress();
        int progressArrowSize = 24;
        return maxProgress != 0 && progress != 0 ? progress * progressArrowSize / maxProgress : 0;
    }

    public int getEnergy() {
        return this.blockEntity.getClientEnergyForDataSlot();
    }

    public int getCapacity(){
        return this.blockEntity.getCapacity();
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(pIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack sourceStack = slot.getItem();
            itemstack = sourceStack.copy();

            if (pIndex < CrafterBlockEntity.TOTAL_SLOTS) {
                if (!this.moveItemStackTo(sourceStack, CrafterBlockEntity.TOTAL_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(sourceStack, 0, CrafterBlockEntity.INPUT_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (sourceStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (sourceStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(pPlayer, sourceStack);
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), pPlayer, ModBlocks.CRAFTER_BLOCK.get());
    }
}