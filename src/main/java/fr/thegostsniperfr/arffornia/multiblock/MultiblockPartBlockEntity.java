package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.machine.SideConfig;
import fr.thegostsniperfr.arffornia.machine.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One block of a formed structure: the block it replaced, the master it belongs to and, for a port, which faces let
 * items or energy through.
 */
public class MultiblockPartBlockEntity extends BlockEntity {
    private BlockState original = Blocks.AIR.defaultBlockState();
    private BlockPos master = BlockPos.ZERO;
    @Nullable
    private PortKind port;
    @Nullable
    private SideConfig sides;

    public MultiblockPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MULTIBLOCK_PART_BE.get(), pos, state);
    }

    void formed(BlockState original, BlockPos master) {
        this.original = original;
        this.master = master;
        this.port = original.getBlock() instanceof PortBlock portBlock ? portBlock.kind() : null;
        this.sides = port != null ? new SideConfig(port.sideCycle()) : null;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public BlockState original() {
        return original;
    }

    public BlockPos master() {
        return master;
    }

    public boolean belongsTo(BlockPos masterPos) {
        return master.equals(masterPos);
    }

    public Optional<PortKind> port() {
        return Optional.ofNullable(port);
    }

    /** Switches the port's {@code face} to its next mode; empty when this part is not a port. */
    public Optional<SideMode> cycleSide(Direction face) {
        if (sides == null) {
            return Optional.empty();
        }
        SideMode mode = sides.cycle(face);
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
        return Optional.of(mode);
    }

    /** A port is reached through its faces only: a query without a face gets nothing, so a switched-off face cannot be bypassed. */
    public SideMode side(@Nullable Direction face) {
        if (face == null || sides == null) {
            return SideMode.NONE;
        }
        return sides.get(face);
    }

    @Nullable
    public IItemHandler items(@Nullable Direction face) {
        SideMode mode = side(face);
        return port != null && port.carriesItems() && mode != SideMode.NONE ? new PortItemHandler(this, mode) : null;
    }

    @Nullable
    public IEnergyStorage energy(@Nullable Direction face) {
        SideMode mode = side(face);
        return port != null && port.carriesEnergy() && mode != SideMode.NONE ? new PortEnergyStorage(this, mode) : null;
    }

    /** The master this part belongs to, when it is loaded and still lists this part. Never loads a chunk. */
    @Nullable
    public MultiblockMachineBlockEntity machine() {
        return level != null && level.isLoaded(master) ? owner() : null;
    }

    /** The master even when its chunk is not loaded: taking a structure apart must not leave a working machine with a hole. */
    @Nullable
    MultiblockMachineBlockEntity owner() {
        return level != null && level.getBlockEntity(master) instanceof MultiblockMachineBlockEntity machine
                && machine.parts().contains(worldPosition) ? machine : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("original", NbtUtils.writeBlockState(original));
        tag.put("master", NbtUtils.writeBlockPos(master));
        if (sides != null) {
            tag.put("sides", sides.save());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("original", NbtUtils.writeBlockState(original));
        tag.put("master", NbtUtils.writeBlockPos(master));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        original = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("original"));
        master = NbtUtils.readBlockPos(tag, "master").orElse(BlockPos.ZERO);
        port = original.getBlock() instanceof PortBlock portBlock ? portBlock.kind() : null;
        sides = port != null ? new SideConfig(port.sideCycle()) : null;
        if (sides != null) {
            sides.load(tag.getCompound("sides"));
        }
    }
}
