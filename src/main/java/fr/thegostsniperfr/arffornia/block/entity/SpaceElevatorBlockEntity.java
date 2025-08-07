package fr.thegostsniperfr.arffornia.block.entity;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.screen.SpaceElevatorMenu;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SpaceElevatorBlockEntity extends BlockEntity implements MenuProvider {
    public final ItemStackHandler itemHandler = new ItemStackHandler(70);
    private final IItemHandler automationHandler;
    private long linkedProgressionId = -1;
    private boolean isLaunching = false;

    @Nullable
    public ArfforniaApiDtos.MilestoneDetails cachedMilestoneDetails;

    public SpaceElevatorBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.SPACE_ELEVATOR_BE.get(), pPos, pBlockState);

        this.automationHandler = new IItemHandler() {
            @Override
            public int getSlots() {
                return itemHandler.getSlots();
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return itemHandler.getStackInSlot(slot);
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return itemHandler.insertItem(slot, stack, simulate);
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return itemHandler.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return itemHandler.isItemValid(slot, stack);
            }
        };
    }

    public IItemHandler getAutomationItemHandler() {
        return this.automationHandler;
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

    public boolean areRequirementsMet(@Nullable ArfforniaApiDtos.MilestoneDetails details) {
        if (details == null || details.requirements() == null || details.requirements().isEmpty()) {
            return false;
        }

        for (ArfforniaApiDtos.MilestoneRequirement requirement : details.requirements()) {
            Item requiredItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(requirement.itemId()));
            if (countItems(requiredItem) < requirement.amount()) {
                return false;
            }
        }

        return true;
    }

    public List<ItemStack> consumeRequirements() {
        if (cachedMilestoneDetails == null) return new ArrayList<>();

        List<ItemStack> consumedItems = new ArrayList<>();
        for (ArfforniaApiDtos.MilestoneRequirement requirement : cachedMilestoneDetails.requirements()) {
            Item requiredItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(requirement.itemId()));
            int amountToConsume = requirement.amount();

            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (amountToConsume <= 0) break;
                if (itemHandler.getStackInSlot(i).is(requiredItem)) {
                    ItemStack extractedStack = itemHandler.extractItem(i, amountToConsume, false);
                    if (!extractedStack.isEmpty()) {
                        consumedItems.add(extractedStack);
                        amountToConsume -= extractedStack.getCount();
                    }
                }
            }
        }

        return consumedItems;
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

    public void launch(ServerPlayer player) {
        if (level == null || level.isClientSide() || isLaunching || !areRequirementsMet(this.cachedMilestoneDetails)) return;

        final ArfforniaApiDtos.MilestoneDetails details = this.cachedMilestoneDetails;
        if (details == null) {
            Arffornia.LOGGER.error("Launch triggered at {} but milestone details were not cached.", getBlockPos());
            return;
        }

        // --- TRANSACTIONAL LOGIC ---
        this.isLaunching = true;
        setChanged();

        final List<ItemStack> consumedItems = consumeRequirements();
        final int activeMilestoneId = details.id();

        Arffornia.ARFFORNA_API_SERVICE.addMilestone(player.getUUID(), activeMilestoneId)
                .thenAcceptAsync(success -> {
                    if (success) {
                        Component playerName = player.getDisplayName();
                        Component message = Component.empty()
                                .append(playerName)
                                .append(Component.literal(" has completed the milestone: "))
                                .append(Component.literal(details.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                        player.getServer().getPlayerList().broadcastSystemMessage(message, false);

                        int stageNumber = details.stageNumber() != null ? details.stageNumber() : 1;
                        scheduleFireworks(stageNumber, 6);

                    } else {
                        Arffornia.LOGGER.error("API call to complete milestone {} for player {} failed. Refunding items.", activeMilestoneId, player.getUUID());
                        player.sendSystemMessage(Component.literal("§cAn error occurred while validating the milestone. Your items have been refunded.").withStyle(ChatFormatting.RED));
                        refundItems(player, consumedItems);
                    }
                    this.isLaunching = false;
                    setChanged();
                }, player.getServer());
    }

    private void refundItems(ServerPlayer player, List<ItemStack> itemsToRefund) {
        for (ItemStack stack : itemsToRefund) {
            if (!player.getInventory().add(stack)) {
                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            }
        }
    }

    private void scheduleFireworks(int count, int delayTicks) {
        if (count <= 0 || this.level == null || this.level.isClientSide()) {
            return;
        }

        spawnFirework();

        level.getServer().tell(new TickTask(
                level.getServer().getTickCount() + delayTicks,
                () -> scheduleFireworks(count - 1, delayTicks)
        ));
    }


    private void spawnFirework() {
        if (this.level == null || this.level.isClientSide()) return;

        ItemStack fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
        RandomSource random = this.level.random;

        FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[random.nextInt(FireworkExplosion.Shape.values().length)];
        IntArrayList colors = new IntArrayList();
        for (int i = 0; i < 1 + random.nextInt(4); i++) {
            colors.add(DyeColor.byId(random.nextInt(16)).getFireworkColor());
        }

        IntArrayList fadeColors = new IntArrayList();
        if (random.nextBoolean()) {
            for (int i = 0; i < 1 + random.nextInt(3); i++) {
                fadeColors.add(DyeColor.byId(random.nextInt(16)).getFireworkColor());
            }
        }

        FireworkExplosion explosion = new FireworkExplosion(shape, colors, fadeColors, random.nextBoolean(), random.nextBoolean());

        Fireworks fireworks = new Fireworks((byte) (1 + random.nextInt(3)), List.of(explosion));

        fireworkStack.set(DataComponents.FIREWORKS, fireworks);

        FireworkRocketEntity fireworkEntity = new FireworkRocketEntity(this.level,
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 2.5,
                this.worldPosition.getZ() + 0.5,
                fireworkStack);

        this.level.addFreshEntity(fireworkEntity);
    }


    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);

        pTag.put("inventory", itemHandler.serializeNBT(pRegistries));
        pTag.putLong("linkedProgressionId", linkedProgressionId);
        pTag.putBoolean("isLaunching", isLaunching);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);

        if (pTag.contains("inventory", Tag.TAG_COMPOUND)) {
            itemHandler.deserializeNBT(pRegistries, pTag.getCompound("inventory"));
        }

        if (pTag.contains("linkedProgressionId", Tag.TAG_LONG)) {
            this.linkedProgressionId = pTag.getLong("linkedProgressionId");
        }

        this.isLaunching = pTag.getBoolean("isLaunching");
    }

    public void setOwner(Player player) {
        String playerUuid = player.getUUID().toString().replace("-", "");

        Arffornia.ARFFORNA_API_SERVICE.fetchPlayerData(playerUuid).thenAccept(playerData -> {
            if (playerData != null) {
                this.linkedProgressionId = playerData.activeProgressionId();
                Arffornia.LOGGER.info("Space Elevator at {} linked to progression ID {}", getBlockPos(), this.linkedProgressionId);
                setChanged();
            } else {
                Arffornia.LOGGER.error("Failed to get player data for {} to link Space Elevator.", player.getName().getString());
            }
        });
    }

    public long getLinkedProgressionId() {
        return this.linkedProgressionId;
    }

    public boolean isLaunching() {
        return this.isLaunching;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) { return saveWithoutMetadata(pRegistries); }

    public void setCachedMilestoneDetails(@Nullable ArfforniaApiDtos.MilestoneDetails d) { this.cachedMilestoneDetails = d; }

    @Nullable
    public ArfforniaApiDtos.MilestoneDetails getCachedMilestoneDetails() {
        return cachedMilestoneDetails;
    }
}