package fr.thegostsniperfr.arffornia.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claims started but not yet delivered, saved with the world. Written before the claim request and removed once the
 * reward is delivered or rejected, so a lost response, a disconnect or a restart is resolved by claiming again with the
 * same key: the website answers with a replay and the reward is delivered once. Server thread only.
 */
public final class RewardClaimLedger extends SavedData {
    private static final String NAME = "arffornia_reward_claims";

    public record Entry(long rewardId, UUID player, String key) {
    }

    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    public static RewardClaimLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RewardClaimLedger::new, RewardClaimLedger::load, null), NAME);
    }

    /** The key to claim this reward with: the recorded one if a claim already started, else a new recorded one. */
    public String keyFor(long rewardId, UUID player) {
        Entry existing = entries.get(rewardId);
        if (existing != null) {
            return existing.key();
        }
        Entry entry = new Entry(rewardId, player, UUID.randomUUID().toString());
        entries.put(rewardId, entry);
        setDirty();
        return entry.key();
    }

    public List<Entry> openClaimsOf(UUID player) {
        return entries.values().stream().filter(e -> e.player().equals(player)).toList();
    }

    public void close(long rewardId) {
        if (entries.remove(rewardId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag e = new CompoundTag();
            e.putLong("reward", entry.rewardId());
            e.putUUID("player", entry.player());
            e.putString("key", entry.key());
            list.add(e);
        }
        tag.put("claims", list);
        return tag;
    }

    private static RewardClaimLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        RewardClaimLedger ledger = new RewardClaimLedger();
        for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ledger.entries.put(e.getLong("reward"), new Entry(e.getLong("reward"), e.getUUID("player"), e.getString("key")));
        }
        return ledger;
    }
}
