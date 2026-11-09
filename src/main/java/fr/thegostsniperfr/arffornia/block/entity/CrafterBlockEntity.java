package fr.thegostsniperfr.arffornia.block.entity;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.machine.MachineEnergyStorage;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.recipe.CrafterRecipes;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private long linkedProgressionId = ModAttachments.NO_PROGRESSION;
    @Nullable
    private ResourceLocation selectedRecipeId = null;
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
        MachineRecipe currentRecipe = be.getSelectedRecipe().map(RecipeHolder::value).orElse(null);

        if (currentRecipe != null && be.isUnlocked(currentRecipe) && be.canCraft(currentRecipe)) {
            hasWork = true;
            be.maxProgress = currentRecipe.time();
            int energyPerTick = MachineEnergyStorage.perTick(currentRecipe.energy(), be.maxProgress);

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

    private boolean isUnlocked(MachineRecipe recipe) {
        return level != null && level.getServer() != null
                && CrafterRecipes.isUnlocked(level.getServer(), linkedProgressionId, recipe);
    }

    private List<ItemStack> inputStacks() {
        List<ItemStack> stacks = new ArrayList<>(INPUT_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks.add(this.itemHandler.getStackInSlot(i));
        }
        return stacks;
    }

    private boolean canCraft(MachineRecipe recipe) {
        if (recipe.allocate(inputStacks()).isEmpty()) {
            return false;
        }
        if (recipe.results().size() > OUTPUT_SLOTS) {
            return false;
        }
        for (int i = 0; i < recipe.results().size(); i++) {
            ItemStack result = recipe.results().get(i);
            ItemStack output = this.itemHandler.getStackInSlot(INPUT_SLOTS + i);
            if (result.getCount() > Math.min(result.getMaxStackSize(), this.itemHandler.getSlotLimit(INPUT_SLOTS + i))) {
                return false;
            }
            if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                    || output.getCount() + result.getCount() > output.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    private void craftItem(MachineRecipe recipe) {
        Optional<int[]> allocation = recipe.allocate(inputStacks());
        if (allocation.isEmpty()) {
            return;
        }
        int[] taken = allocation.get();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (taken[slot] > 0) {
                this.itemHandler.extractItem(slot, taken[slot], false);
            }
        }
        for (int i = 0; i < recipe.results().size(); i++) {
            this.itemHandler.insertItem(INPUT_SLOTS + i, recipe.results().get(i).copy(), false);
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
    public ResourceLocation getSelectedRecipeId() {
        return selectedRecipeId;
    }

    public Optional<RecipeHolder<MachineRecipe>> getSelectedRecipe() {
        if (this.selectedRecipeId == null || this.level == null) {
            return Optional.empty();
        }
        return CrafterRecipes.byId(this.level.getRecipeManager(), this.selectedRecipeId);
    }

    public void setSelectedRecipe(@Nullable ResourceLocation recipeId) {
        if (!Objects.equals(this.selectedRecipeId, recipeId)) {
            this.selectedRecipeId = recipeId;
            this.progress = 0;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public boolean isRecipeSelected(ResourceLocation recipeId) {
        return recipeId.equals(this.selectedRecipeId);
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
        if (selectedRecipeId != null) {
            pTag.putString("selectedRecipe", selectedRecipeId.toString());
        }
        pTag.putInt("progress", progress);
        pTag.putInt("maxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);

        if (pTag.contains("inventory")) {
            // Create a temporary handler to load the potentially outdated saved data.
            ItemStackHandler savedHandler = new ItemStackHandler();
            savedHandler.deserializeNBT(pRegistries, pTag.getCompound("inventory"));

            // Copy items from the loaded data into our correctly-sized handler.
            // This prevents an out-of-bounds error if the saved inventory was smaller.
            int limit = Math.min(savedHandler.getSlots(), this.itemHandler.getSlots());
            for (int i = 0; i < limit; i++) {
                this.itemHandler.setStackInSlot(i, savedHandler.getStackInSlot(i));
            }
        }

        energyStorage.setEnergy(pTag.getInt("energy"));
        linkedProgressionId = pTag.contains("linkedProgressionId") ? pTag.getLong("linkedProgressionId") : ModAttachments.NO_PROGRESSION;
        selectedRecipeId = pTag.contains("selectedRecipe") ? ResourceLocation.tryParse(pTag.getString("selectedRecipe")) : null;
        progress = pTag.getInt("progress");

        // Safety check to prevent division-by-zero if maxProgress is invalid from a save
        int loadedMaxProgress = pTag.getInt("maxProgress");
        maxProgress = (loadedMaxProgress > 0) ? loadedMaxProgress : 200;
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

    public void linkToProgression(long progressionId) {
        this.linkedProgressionId = progressionId;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            Progressions.ensureKnown(serverLevel.getServer(), linkedProgressionId);
        }
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