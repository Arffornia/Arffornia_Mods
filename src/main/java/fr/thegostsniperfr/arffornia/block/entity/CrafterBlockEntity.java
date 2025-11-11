package fr.thegostsniperfr.arffornia.block.entity;

import com.google.common.collect.Maps;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public class CrafterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 2;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;

    private static final int ENERGY_CAPACITY = 12000;
    private static final int ENERGY_MAX_TRANSFER = 1000;
    public final ItemStackHandler itemHandler = createItemHandler();
    public final CustomEnergyStorage energyStorage = createEnergyStorage();
    public final IItemHandler automationInputHandler = createInputItemHandler();
    public final IItemHandler automationOutputDownHandler = createOutputItemHandler(0);
    public final IItemHandler automationOutputEastHandler = createOutputItemHandler(1);
    private long linkedProgressionId = -1;
    @Nullable
    private Integer selectedRecipeMilestoneUnlockId = null;
    private int progress = 0;
    private int maxProgress = 200;

    private int clientEnergy = -1;
    private int ticksSinceLastUpdate = 0;

    public CrafterBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.CRAFTER_BE.get(), pPos, pBlockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrafterBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }

        boolean hasWork = false;
        ArfforniaApiDtos.CustomRecipe currentRecipe = be.getSelectedRecipe();

        if (currentRecipe != null && be.canCraft(currentRecipe)) {
            hasWork = true;
            be.maxProgress = currentRecipe.time() != null && currentRecipe.time() > 0 ? currentRecipe.time() : 200;
            int totalEnergyCost = currentRecipe.energy() != null ? currentRecipe.energy() : 0;

            int energyPerTick = totalEnergyCost > 0 ? (int) Math.floor((double) totalEnergyCost / be.maxProgress) : 0;

            if (be.energyStorage.getEnergyStored() >= energyPerTick) {
                be.energyStorage.extractEnergy(energyPerTick, false);
                be.progress++;
            }

            if (be.progress >= be.maxProgress) {
                be.craftItem(currentRecipe);
                be.progress = 0;
            }
        } else {
            if (be.progress != 0) {
                be.progress = 0;
            }
        }

        be.ticksSinceLastUpdate++;
        boolean needsSync = false;
        int syncInterval = (hasWork || be.clientEnergy != be.energyStorage.getEnergyStored()) ? 10 : 40;

        if(be.ticksSinceLastUpdate >= syncInterval){
            be.clientEnergy = be.energyStorage.getEnergyStored();
            needsSync = true;
            be.ticksSinceLastUpdate = 0;
        }

        if(needsSync){
            setChanged(level, pos, state);
            level.getServer().getPlayerList().getPlayers().forEach(player -> {
                if (player.containerMenu instanceof CrafterMenu crafterMenu && crafterMenu.blockEntity == be) {
                    crafterMenu.broadcastChanges();
                }
            });
        }
    }

    public int getClientEnergyForDataSlot() {
        return this.clientEnergy;
    }

    public void setClientEnergyFromDataSlot(int energy) {
        this.clientEnergy = energy;
    }

    private boolean canCraft(ArfforniaApiDtos.CustomRecipe recipe) {
        Map<Item, Integer> requiredItems = Maps.newHashMap();
        for (ArfforniaApiDtos.RecipeIngredient ingredient : recipe.ingredients()) {
            if (ingredient != null) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient.item()));
                requiredItems.merge(item, ingredient.count(), Integer::sum);
            }
        }

        if (requiredItems.isEmpty()) {
            return false;
        }

        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stackInSlot = this.itemHandler.getStackInSlot(i);
            if (!stackInSlot.isEmpty()) {
                Item item = stackInSlot.getItem();
                if (requiredItems.containsKey(item)) {
                    int needed = requiredItems.get(item);
                    int count = Math.min(needed, stackInSlot.getCount());
                    requiredItems.merge(item, -count, Integer::sum);
                }
            }
        }

        if (requiredItems.values().stream().anyMatch(count -> count > 0)) {
            return false;
        }

        for (int i = 0; i < recipe.result().size(); i++) {
            if (i >= OUTPUT_SLOTS) break;
            ArfforniaApiDtos.RecipeResult result = recipe.result().get(i);
            Item resultItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(result.item()));
            ItemStack resultStack = new ItemStack(resultItem, result.count());
            ItemStack outputStack = this.itemHandler.getStackInSlot(INPUT_SLOTS + i);

            if (!outputStack.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(outputStack, resultStack)) {
                    return false;
                }
                if (outputStack.getCount() + resultStack.getCount() > outputStack.getMaxStackSize()) {
                    return false;
                }
            }
        }

        return true;
    }

    private void craftItem(ArfforniaApiDtos.CustomRecipe recipe) {
        for (ArfforniaApiDtos.RecipeIngredient ingredient : recipe.ingredients()) {
            if (ingredient == null) {
                continue;
            }

            Item requiredItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient.item()));
            int amountToConsume = ingredient.count();
            for (int i = 0; i < INPUT_SLOTS; i++) {
                if (amountToConsume <= 0) break;
                ItemStack stackInSlot = this.itemHandler.getStackInSlot(i);
                if (stackInSlot.is(requiredItem)) {
                    int toExtract = Math.min(amountToConsume, stackInSlot.getCount());
                    this.itemHandler.extractItem(i, toExtract, false);
                    amountToConsume -= toExtract;
                }
            }
        }

        for (int i = 0; i < recipe.result().size(); i++) {
            if (i >= OUTPUT_SLOTS) break;
            ArfforniaApiDtos.RecipeResult result = recipe.result().get(i);
            ItemStack resultStack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(result.item())), result.count());
            this.itemHandler.insertItem(INPUT_SLOTS + i, resultStack, false);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.arffornia.crafter_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new CrafterMenu(pContainerId, pPlayerInventory, this);
    }

    public long getLinkedProgressionId() {
        return this.linkedProgressionId;
    }

    @Nullable
    public ArfforniaApiDtos.CustomRecipe getSelectedRecipe() {
        return this.selectedRecipeMilestoneUnlockId != null ? CustomRecipeManager.getRecipeByMilestoneUnlockId(this.selectedRecipeMilestoneUnlockId) : null;
    }

    public void setSelectedRecipe(@Nullable Integer milestoneUnlockId) {
        if (!Objects.equals(this.selectedRecipeMilestoneUnlockId, milestoneUnlockId)) {
            this.selectedRecipeMilestoneUnlockId = milestoneUnlockId;
            this.progress = 0;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public boolean isRecipeSelected(ArfforniaApiDtos.CustomRecipe recipe) {
        return this.selectedRecipeMilestoneUnlockId != null && this.selectedRecipeMilestoneUnlockId.equals(recipe.milestoneUnlockId());
    }

    public NonNullList<ItemStack> getInventoryForDrop() {
        NonNullList<ItemStack> items = NonNullList.create();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            items.add(itemHandler.getStackInSlot(i));
        }
        return items;
    }

    private ItemStackHandler createItemHandler() {
        return new ItemStackHandler(TOTAL_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    private CustomEnergyStorage createEnergyStorage() {
        return new CustomEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_TRANSFER);
    }

    private IItemHandler createInputItemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return INPUT_SLOTS;
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                return itemHandler.getStackInSlot(slot);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return itemHandler.insertItem(slot, stack, simulate);
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return itemHandler.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return itemHandler.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return true;
            }
        };
    }

    private IItemHandler createOutputItemHandler(int outputSlotIndex) {
        return new IItemHandler() {
            private final int actualSlot = INPUT_SLOTS + outputSlotIndex;

            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                return itemHandler.getStackInSlot(actualSlot);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return itemHandler.extractItem(actualSlot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return itemHandler.getSlotLimit(actualSlot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return false;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);
        pTag.put("inventory", itemHandler.serializeNBT(pRegistries));
        pTag.putInt("energy", energyStorage.getEnergyStored());
        pTag.putLong("linkedProgressionId", linkedProgressionId);
        if (selectedRecipeMilestoneUnlockId != null) {
            pTag.putInt("selectedRecipeId", selectedRecipeMilestoneUnlockId);
        }
        pTag.putInt("progress", progress);
        pTag.putInt("maxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        itemHandler.deserializeNBT(pRegistries, pTag.getCompound("inventory"));
        energyStorage.setEnergy(pTag.getInt("energy"));
        linkedProgressionId = pTag.getLong("linkedProgressionId");
        selectedRecipeMilestoneUnlockId = pTag.contains("selectedRecipeId") ? pTag.getInt("selectedRecipeId") : null;
        progress = pTag.getInt("progress");
        maxProgress = pTag.getInt("maxProgress");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }

    public void setOwner(Player player) {
        String playerUuid = player.getUUID().toString().replace("-", "");
        ArfforniaApiService.getInstance().fetchPlayerData(playerUuid).thenAccept(playerData -> {
            if (playerData != null) {
                this.linkedProgressionId = playerData.activeProgressionId();
                Arffornia.LOGGER.info("Crafter at {} linked to progression ID {}", getBlockPos(), this.linkedProgressionId);
                setChanged();
            } else {
                Arffornia.LOGGER.error("Failed to get player data for {} to link Crafter.", player.getName().getString());
            }
        });
    }

    public int getEnergy() {
        return this.energyStorage.getEnergyStored();
    }

    public int getCapacity() {
        return this.energyStorage.getMaxEnergyStored();
    }

    public int getProgress() {
        return this.progress;
    }

    public void setProgress(int value) {
        this.progress = value;
    }

    public int getMaxProgress() {
        return this.maxProgress;
    }

    public void setMaxProgress(int value) {
        this.maxProgress = value;
    }

    public boolean stillValid(Player pPlayer) {
        return this.level != null && this.level.getBlockEntity(this.worldPosition) == this && pPlayer.distanceToSqr(this.worldPosition.getCenter()) <= 64.0;
    }

    private static class CustomEnergyStorage extends EnergyStorage {
        public CustomEnergyStorage(int capacity, int maxTransfer) {
            super(capacity, maxTransfer, maxTransfer);
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }
    }
}