package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Last known state of every progression a machine or an online player uses, saved with the world so machines keep
 * working across restarts while the website is unreachable.
 */
public final class ProgressionStore extends SavedData {
    private static final String NAME = Arffornia.MODID + "_progressions";
    private static final Codec<List<ProgressionSnapshot>> LIST_CODEC = ProgressionSnapshot.CODEC.listOf();

    private final Map<Long, ProgressionSnapshot> snapshots = new HashMap<>();

    public static ProgressionStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProgressionStore::new, ProgressionStore::load, null), NAME);
    }

    private static ProgressionStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressionStore store = new ProgressionStore();
        DataResult<List<ProgressionSnapshot>> parsed = LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("progressions"));
        parsed.resultOrPartial(error -> Arffornia.LOGGER.error("Could not read saved progressions: {}", error))
                .ifPresent(list -> list.forEach(snapshot -> store.snapshots.put(snapshot.id(), snapshot)));
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Tag encoded = LIST_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(snapshots.values())).getOrThrow();
        tag.put("progressions", encoded);
        return tag;
    }

    public Optional<ProgressionSnapshot> find(long progressionId) {
        return Optional.ofNullable(snapshots.get(progressionId));
    }

    public Set<Long> knownIds() {
        return Set.copyOf(snapshots.keySet());
    }

    public void put(ProgressionSnapshot snapshot) {
        if (!snapshot.equals(snapshots.put(snapshot.id(), snapshot))) {
            setDirty();
        }
    }
}
