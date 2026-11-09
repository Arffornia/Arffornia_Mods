package fr.thegostsniperfr.arffornia.machine;

import fr.thegostsniperfr.arffornia.progression.MachineProgressions;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A machine that runs {@code arffornia:machine} recipes for the progression it belongs to (#18): slots from its
 * {@link SlotLayout}, a clamped FE buffer, a {@link RecipeProcessor} and the numbers its {@link MachineMenu} shows.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider {
    private static final double MAX_MENU_DISTANCE_SQR = 64.0;

    private final MachineType type;
    private final ItemStackHandler items;
    private final MachineEnergyStorage energy;
    private final RecipeProcessor processor;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case MachineData.PROGRESS -> processor.progress();
                case MachineData.MAX_PROGRESS -> processor.maxProgress();
                case MachineData.ENERGY_LOW -> MachineData.low(energy.getEnergyStored());
                case MachineData.ENERGY_HIGH -> MachineData.high(energy.getEnergyStored());
                case MachineData.CAPACITY_LOW -> MachineData.low(energy.getMaxEnergyStored());
                case MachineData.CAPACITY_HIGH -> MachineData.high(energy.getMaxEnergyStored());
                case MachineData.STATUS -> processor.status().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return MachineData.COUNT;
        }
    };
    private long linkedProgressionId = ModAttachments.NO_PROGRESSION;

    protected MachineBlockEntity(BlockEntityType<?> blockEntityType, MachineType type, BlockPos pos, BlockState state) {
        super(blockEntityType, pos, state);
        this.type = type;
        this.processor = new RecipeProcessor(type.id(), type.layout());
        this.energy = new MachineEnergyStorage(type.energyCapacity(), type.maxReceive(), this::setChanged);
        this.items = new ItemStackHandler(type.layout().size()) {
            @Override
            protected void onContentsChanged(int slot) {
                processor.onInventoryChanged();
                setChanged();
            }
        };
    }

    public void serverTick(ServerLevel level) {
        boolean changed = canOperate() ? processor.tick(level, items, energy, linkedProgressionId) : processor.halt(MachineStatus.NOT_FORMED);
        if (changed) {
            setChanged();
        }
    }

    /** False while the machine cannot work at all, for example a multiblock that is not formed. */
    protected boolean canOperate() {
        return true;
    }

    public void openMenu(ServerPlayer player) {
        if (MachineProgressions.checkServing(player, linkedProgressionId)) {
            player.openMenu(this, buf -> MachineMenu.writeOpenData(buf, worldPosition, type.layout()));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(type.titleKey());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(containerId, inventory, this);
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && canOperate()
                && player.distanceToSqr(worldPosition.getCenter()) <= MAX_MENU_DISTANCE_SQR;
    }

    @Nullable
    public IItemHandler itemView(SideMode mode) {
        return MachineItemView.of(items, type.layout(), mode);
    }

    @Nullable
    public IEnergyStorage energyView(SideMode mode) {
        return mode.allowsInsert() ? energy : null;
    }

    public MachineType type() {
        return type;
    }

    public ItemStackHandler items() {
        return items;
    }

    public MachineEnergyStorage energy() {
        return energy;
    }

    public ContainerData data() {
        return data;
    }

    public MachineStatus status() {
        return processor.status();
    }

    public int progress() {
        return processor.progress();
    }

    public NonNullList<ItemStack> contents() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            stacks.add(items.getStackInSlot(slot));
        }
        return stacks;
    }

    public long linkedProgressionId() {
        return linkedProgressionId;
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", items.serializeNBT(registries));
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("processor", processor.save());
        tag.putLong("linkedProgressionId", linkedProgressionId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag.getCompound("inventory"));
        for (int slot = 0; slot < Math.min(saved.getSlots(), items.getSlots()); slot++) {
            items.setStackInSlot(slot, saved.getStackInSlot(slot));
        }
        energy.setEnergy(tag.getInt("energy"));
        processor.load(tag.getCompound("processor"));
        linkedProgressionId = tag.contains("linkedProgressionId") ? tag.getLong("linkedProgressionId") : ModAttachments.NO_PROGRESSION;
    }
}
