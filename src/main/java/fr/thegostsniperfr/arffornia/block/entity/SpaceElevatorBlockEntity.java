package fr.thegostsniperfr.arffornia.block.entity;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.screen.SpaceElevatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class SpaceElevatorBlockEntity extends BlockEntity implements MenuProvider {
    public final ItemStackHandler itemHandler = new ItemStackHandler(70);
    private int activeMilestoneId = -1;
    @Nullable
    public ArfforniaApiDtos.MilestoneDetails cachedMilestoneDetails;

    public SpaceElevatorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.SPACE_ELEVATOR_BE.get(), pPos, pBlockState);
    }

    public int countItems(Item item) {
        int count = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (itemHandler.getStackInSlot(i).is(item)) {
                count += itemHandler.getStackInSlot(i).getCount();
            }
        }
        return count;
    }

    public boolean areRequirementsMet() {
        if (cachedMilestoneDetails == null || cachedMilestoneDetails.requirements().isEmpty()) return false;
        for (ArfforniaApiDtos.MilestoneRequirement req : cachedMilestoneDetails.requirements()) {
            Item requiredItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.itemId()));
            if (countItems(requiredItem) < req.amount()) return false;
        }
        return true;
    }

    public void consumeRequirements() {
        if (cachedMilestoneDetails == null) return;
        for (ArfforniaApiDtos.MilestoneRequirement req : cachedMilestoneDetails.requirements()) {
            Item requiredItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.itemId()));
            int amountToConsume = req.amount();
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (amountToConsume <= 0) break;
                if (itemHandler.getStackInSlot(i).is(requiredItem)) {
                    int toExtract = Math.min(amountToConsume, itemHandler.getStackInSlot(i).getCount());
                    itemHandler.extractItem(i, toExtract, false);
                    amountToConsume -= toExtract;
                }
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.arffornia.space_elevator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new SpaceElevatorMenu(pContainerId, pPlayerInventory, this);
    }

    public void launch() {
        if (level == null || level.isClientSide() || !areRequirementsMet()) return;
        consumeRequirements();
        Arffornia.LOGGER.info("Space Elevator at {} launched for milestone {}!", getBlockPos(), activeMilestoneId);
        setChanged();
    }
    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);
        pTag.put("inventory", itemHandler.serializeNBT(pRegistries));
        pTag.putInt("activeMilestoneId", activeMilestoneId);
    }
    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        itemHandler.deserializeNBT(pRegistries, pTag.getCompound("inventory"));
        if (pTag.contains("activeMilestoneId", Tag.TAG_INT)) this.activeMilestoneId = pTag.getInt("activeMilestoneId");
    }
    public void setOwner(Player player) {
        String playerUuid = player.getUUID().toString().replace("-", "");
        Arffornia.ARFFORNA_API_SERVICE.fetchPlayerGraphData(playerUuid).thenAccept(graphData -> {
            if (graphData != null && graphData.playerProgress() != null && graphData.playerProgress().currentTargetId() != null) {
                this.activeMilestoneId = graphData.playerProgress().currentTargetId();
                fetchAndCacheMilestoneDetails();
                setChanged();
            }
        });
    }
    public void fetchAndCacheMilestoneDetails() {
        if (this.activeMilestoneId == -1 || this.level == null) return;
        Arffornia.ARFFORNA_API_SERVICE.fetchMilestoneDetails(this.activeMilestoneId).thenAccept(details -> {
            this.cachedMilestoneDetails = details;
            setChanged();
            if (!level.isClientSide) ((ServerLevel) level).getChunkSource().blockChanged(getBlockPos());
        });
    }
    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) { return saveWithoutMetadata(pRegistries); }
    public int getActiveMilestoneId() { return activeMilestoneId; }
    public void setCachedMilestoneDetails(@Nullable ArfforniaApiDtos.MilestoneDetails d) { this.cachedMilestoneDetails = d; }

    public @Nullable ArfforniaApiDtos.MilestoneDetails getCachedMilestoneDetails() {
        return cachedMilestoneDetails;
    }
}