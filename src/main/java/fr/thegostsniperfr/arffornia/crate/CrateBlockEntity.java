package fr.thegostsniperfr.arffornia.crate;

import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Names the crate this block opens. Server side it runs one opening at a time; client side it plays the show. */
public class CrateBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation crate;
    @Nullable
    CrateOpening.Running running;

    @Nullable
    private CrateSequence.Show show;
    private int showTick;

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRATE_BE.get(), pos, state);
    }

    @Nullable
    public ResourceLocation crate() {
        return crate;
    }

    public void setCrate(ResourceLocation crate) {
        this.crate = crate;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isOpening() {
        return running != null;
    }

    @Nullable
    public CrateSequence.Show show() {
        return show;
    }

    public int showTick() {
        return showTick;
    }

    /** Client: start or jump the show to {@code tick}. */
    public void play(CrateSequence.Show show, int tick) {
        this.show = show;
        this.showTick = tick;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity crate) {
        CrateOpening.tick(crate);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CrateBlockEntity crate) {
        if (crate.show == null) {
            return;
        }
        CrateEffects.tick(level, pos, state, crate.show, crate.showTick);
        crate.showTick++;
        if (crate.showTick > CrateSequence.CLIENT_END) {
            crate.show = null;
        }
    }

    @Override
    public void setRemoved() {
        if (running != null) {
            CrateOpening.finish(this);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (crate != null) {
            tag.putString("crate", crate.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        crate = tag.contains("crate") ? ResourceLocation.tryParse(tag.getString("crate")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
