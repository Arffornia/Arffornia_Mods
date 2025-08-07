package fr.thegostsniperfr.arffornia.screen;

import com.google.gson.Gson;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class SpaceElevatorMenu extends AbstractContainerMenu {
    public final SpaceElevatorBlockEntity blockEntity;
    @Nullable
    public final ArfforniaApiDtos.MilestoneDetails initialDetails;
    public final boolean isMilestoneCompleted;

    private static final Gson GSON = new Gson();
    private static final int BUFFER_SLOTS = 70; // 10x7

    public SpaceElevatorMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), extraData.readUtf(), extraData.readBoolean());
    }

    public SpaceElevatorMenu(int pContainerId, Inventory inv, BlockEntity entity) {
        this(pContainerId, inv, entity, GSON.toJson(entity instanceof SpaceElevatorBlockEntity be ? be.getCachedMilestoneDetails() : null), false);
    }

    private SpaceElevatorMenu(int pContainerId, Inventory inv, BlockEntity entity, String detailsJson, boolean isCompleted) {
        super(ModMenuTypes.SPACE_ELEVATOR_MENU.get(), pContainerId);

        if (entity instanceof SpaceElevatorBlockEntity be) {
            this.blockEntity = be;
        } else {
            throw new IllegalStateException("Incorrect BlockEntity type provided to SpaceElevatorMenu!");
        }

        this.initialDetails = detailsJson.isEmpty() ? null : GSON.fromJson(detailsJson, ArfforniaApiDtos.MilestoneDetails.class);
        this.isMilestoneCompleted = isCompleted;

        for (int i = 0; i < 7; ++i) {
            for (int j = 0; j < 10; ++j) {
                this.addSlot(new SlotItemHandler(this.blockEntity.itemHandler, j + i * 10, -181 + j * 18, 18 + i * 18) {
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        blockEntity.setChanged();
                    }
                });
            }
        }

        int playerInvY = 84;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, playerInvY + i * 18));
            }
        }
        int hotbarY = 142;
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, hotbarY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(pIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack sourceStack = slot.getItem();
            itemstack = sourceStack.copy();
            if (pIndex < BUFFER_SLOTS) {
                if (!this.moveItemStackTo(sourceStack, BUFFER_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(sourceStack, 0, BUFFER_SLOTS, false)) return ItemStack.EMPTY;
            }
            if (sourceStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), pPlayer, ModBlocks.SPACE_ELEVATOR.get());
    }
}