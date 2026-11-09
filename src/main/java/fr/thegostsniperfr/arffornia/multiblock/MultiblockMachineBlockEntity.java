package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.machine.MachineBlockEntity;
import fr.thegostsniperfr.arffornia.machine.MachineType;
import fr.thegostsniperfr.arffornia.machine.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The machine of a formed structure, at the trigger's position. It owns inventory, energy and recipe processing; the
 * other blocks are {@link MultiblockPartBlock}s pointing here. It remembers the trigger block it replaced and where its
 * parts are, so any shape can be taken apart.
 */
public class MultiblockMachineBlockEntity extends MachineBlockEntity {
    private BlockState original = Blocks.AIR.defaultBlockState();
    private List<BlockPos> parts = List.of();
    private boolean mirrored;
    private boolean disassembling;

    public MultiblockMachineBlockEntity(BlockEntityType<?> blockEntityType, MachineType type, BlockPos pos, BlockState state) {
        super(blockEntityType, type, pos, state);
    }

    void formed(BlockState trigger, Formation formation, long progressionId) {
        this.original = trigger;
        this.parts = formation.positions().stream().filter(pos -> !pos.equals(worldPosition)).toList();
        this.mirrored = formation.mirrored();
        linkToProgression(progressionId);
    }

    /**
     * Puts every block of the structure back as it was before formation, except {@code removed}, the block being
     * broken. Called once, by whichever block of the structure goes first.
     */
    void disassemble(BlockPos removed) {
        if (disassembling || level == null || level.isClientSide()) {
            return;
        }
        disassembling = true;
        for (BlockPos pos : parts) {
            if (!pos.equals(removed) && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && part.belongsTo(worldPosition)) {
                level.setBlock(pos, part.original(), 3);
            }
        }
        if (!worldPosition.equals(removed)) {
            level.setBlock(worldPosition, original, 3);
        }
    }

    @Override
    protected boolean canOperate() {
        return !parts.isEmpty();
    }

    @Nullable
    IItemHandler portItems(PortKind kind, SideMode mode) {
        return kind.carriesItems() ? itemView(mode) : null;
    }

    @Nullable
    IEnergyStorage portEnergy(PortKind kind, SideMode mode) {
        return kind.carriesEnergy() ? energyView(mode) : null;
    }

    public BlockState original() {
        return original;
    }

    public List<BlockPos> parts() {
        return parts;
    }

    public boolean mirrored() {
        return mirrored;
    }

    public AABB bounds() {
        AABB box = new AABB(worldPosition);
        for (BlockPos pos : parts) {
            box = box.minmax(new AABB(pos));
        }
        return box;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("original", NbtUtils.writeBlockState(original));
        ListTag list = new ListTag();
        parts.forEach(pos -> list.add(NbtUtils.writeBlockPos(pos)));
        tag.put("parts", list);
        tag.putBoolean("mirrored", mirrored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        original = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("original"));
        List<BlockPos> loaded = new ArrayList<>();
        for (Tag entry : tag.getList("parts", Tag.TAG_INT_ARRAY)) {
            NbtUtils.readBlockPos(wrap(entry), "pos").ifPresent(loaded::add);
        }
        parts = List.copyOf(loaded);
        mirrored = tag.getBoolean("mirrored");
    }

    private static CompoundTag wrap(Tag entry) {
        CompoundTag holder = new CompoundTag();
        holder.put("pos", entry);
        return holder;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ListTag list = new ListTag();
        parts.forEach(pos -> list.add(NbtUtils.writeBlockPos(pos)));
        tag.put("parts", list);
        tag.putBoolean("mirrored", mirrored);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
