package fr.thegostsniperfr.arffornia.block.entity;

import fr.thegostsniperfr.arffornia.lootbox.LootBoxData;
import fr.thegostsniperfr.arffornia.lootbox.LootBoxManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class LootBoxBlockEntity extends BlockEntity {
    private String boxId = "";
    private List<String> hologram = List.of("§cUnconfigured Box");

    public LootBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOT_BOX_BE.get(), pos, state);
    }

    public String getBoxId() { return boxId; }
    public List<String> getHologram() { return hologram; }

    public void setBoxId(String boxId) {
        this.boxId = boxId;
        LootBoxData data = LootBoxManager.getBox(boxId);
        if (data != null && data.hologram != null) {
            this.hologram = data.hologram;
        } else {
            this.hologram = List.of("§cUnknown Box: " + boxId);
        }
        setChanged();
        if(level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("BoxId", boxId);

        ListTag list = new ListTag();
        for(String s : hologram) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("Hologram", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.boxId = tag.getString("BoxId");

        if (tag.contains("Hologram", 9)) {
            this.hologram = new ArrayList<>();
            ListTag list = tag.getList("Hologram", 8);
            for(int i = 0; i < list.size(); i++) {
                this.hologram.add(list.getString(i));
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}