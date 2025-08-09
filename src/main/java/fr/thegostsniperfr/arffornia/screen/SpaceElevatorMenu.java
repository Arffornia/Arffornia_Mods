package fr.thegostsniperfr.arffornia.screen;

import com.google.gson.Gson;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class SpaceElevatorMenu extends AbstractContainerMenu {
    private static final Gson GSON = new Gson();
    private static final int INVENTORY_SLOTS = 70; // 10x7
    private static final int REQUIREMENT_SLOTS = 3;
    private static final int UNLOCK_SLOTS = 8;
    public final SpaceElevatorBlockEntity blockEntity;
    @Nullable
    public final ArfforniaApiDtos.MilestoneDetails initialDetails;
    public final boolean isMilestoneCompleted;
    private final Container requirementSlots;
    private final Container unlockSlots;

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

        this.requirementSlots = new SimpleContainer(REQUIREMENT_SLOTS);
        this.unlockSlots = new SimpleContainer(UNLOCK_SLOTS);

        // Block inventory
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

        // Player Inventory
        int playerInvY = 84;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, playerInvY + i * 18));
            }
        }

        // Player Hotbar
        int hotbarY = 142;
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, hotbarY));
        }

        // Requirement Slots
        for (int i = 0; i < REQUIREMENT_SLOTS; i++) {
            this.addSlot(new DisplaySlot(this.requirementSlots, i, 8, 18 + i * 18));
        }

        // Unlock Slots
        for (int i = 0; i < UNLOCK_SLOTS; i++) {
            this.addSlot(new DisplaySlot(this.unlockSlots, i, 98 + (i % 4) * 18, 18 + (i / 4) * 18));
        }

        populateDisplaySlots();
    }

    private void populateDisplaySlots() {
        if (initialDetails == null) return;

        // Populate requirements
        for (int i = 0; i < REQUIREMENT_SLOTS; i++) {
            if (i < initialDetails.requirements().size()) {
                ArfforniaApiDtos.MilestoneRequirement req = initialDetails.requirements().get(i);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.itemId()));
                ItemStack stack = new ItemStack(item, 1);
                this.requirementSlots.setItem(i, stack);
            } else {
                this.requirementSlots.setItem(i, ItemStack.EMPTY);
            }
        }

        // Populate unlocks
        for (int i = 0; i < UNLOCK_SLOTS; i++) {
            if (i < initialDetails.unlocks().size()) {
                ArfforniaApiDtos.MilestoneUnlock unlock = initialDetails.unlocks().get(i);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(unlock.itemId()));
                ItemStack stack = new ItemStack(item);
                this.unlockSlots.setItem(i, stack);
            } else {
                this.unlockSlots.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(pIndex);

        if (slot.hasItem()) {
            ItemStack sourceStack = slot.getItem();
            itemstack = sourceStack.copy();

            int readOnlySlotsStart = INVENTORY_SLOTS + 36;
            if (pIndex >= readOnlySlotsStart) {
                return ItemStack.EMPTY;
            }

            if (pIndex < INVENTORY_SLOTS) { // from block inventory to player inventory
                if (!this.moveItemStackTo(sourceStack, INVENTORY_SLOTS, readOnlySlotsStart, true)) {
                    return ItemStack.EMPTY;
                }
            } else { // from player inventory to block inventory
                if (!this.moveItemStackTo(sourceStack, 0, INVENTORY_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (sourceStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), pPlayer, ModBlocks.SPACE_ELEVATOR.get());
    }
}