package fr.thegostsniperfr.arffornia.screen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CrafterMenu extends AbstractContainerMenu {
    private static final Gson GSON = new Gson();
    public final CrafterBlockEntity blockEntity;
    public final List<ArfforniaApiDtos.CustomRecipe> availableRecipes;
    private final int blockEntitySlots = CrafterBlockEntity.TOTAL_SLOTS;

    // Server side
    public CrafterMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));

        String recipesJson = extraData.readUtf();
        Type listType = new TypeToken<ArrayList<ArfforniaApiDtos.CustomRecipe>>() {
        }.getType();
        this.availableRecipes.addAll(GSON.fromJson(recipesJson, listType));
    }

    // Client side
    public CrafterMenu(int pContainerId, Inventory inv, BlockEntity entity) {
        super(ModMenuTypes.CRAFTER_MENU.get(), pContainerId);
        this.blockEntity = (CrafterBlockEntity) entity;
        this.availableRecipes = new ArrayList<>();

        addSlots(inv);
        addDataSlots();
    }

    private void addSlots(Inventory inv) {
        int mainPanelXOffset = 80;

        this.addSlot(new Slot(this.blockEntity.getInventoryWrapper(), 0, mainPanelXOffset + 34, 17));
        this.addSlot(new Slot(this.blockEntity.getInventoryWrapper(), 1, mainPanelXOffset + 34, 35));
        this.addSlot(new Slot(this.blockEntity.getInventoryWrapper(), 2, mainPanelXOffset + 34, 53));

        this.addSlot(new Slot(this.blockEntity.getInventoryWrapper(), 3, mainPanelXOffset + 124, 26) {
            @Override
            public boolean mayPlace(@NotNull ItemStack s) {
                return false;
            }
        });
        this.addSlot(new Slot(this.blockEntity.getInventoryWrapper(), 4, mainPanelXOffset + 124, 44) {
            @Override
            public boolean mayPlace(@NotNull ItemStack s) {
                return false;
            }
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
            @Override
            public int get() {
                return blockEntity.getProgress();
            }

            @Override
            public void set(int value) {
                blockEntity.setProgress(value);
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return blockEntity.getMaxProgress();
            }

            @Override
            public void set(int value) {
                blockEntity.setMaxProgress(value);
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return blockEntity.getEnergy() & 0xFFFF;
            }

            @Override
            public void set(int value) {
                int energy = blockEntity.getEnergy() & 0xFFFF0000;
                blockEntity.setEnergy(energy | (value & 0xFFFF));
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (blockEntity.getEnergy() >> 16) & 0xFFFF;
            }

            @Override
            public void set(int value) {
                int energy = blockEntity.getEnergy() & 0xFFFF;
                blockEntity.setEnergy(energy | (value << 16));
            }
        });
    }

    public int getScaledProgress() {
        int progress = this.blockEntity.getProgress();
        int maxProgress = this.blockEntity.getMaxProgress();
        int progressArrowSize = 24;
        return maxProgress != 0 && progress != 0 ? progress * progressArrowSize / maxProgress : 0;
    }

    public int getScaledEnergy() {
        int energy = this.blockEntity.getEnergy();
        int capacity = this.blockEntity.getCapacity();
        int energyBarSize = 60;
        return capacity != 0 ? (int) (((long) energy * energyBarSize) / capacity) : 0;
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