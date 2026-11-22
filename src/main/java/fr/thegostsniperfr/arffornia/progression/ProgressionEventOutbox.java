package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Events the website has not acknowledged yet, saved with the world so a restart during an outage loses none.
 * The events endpoint is idempotent by event id, so sending one twice is harmless.
 */
public final class ProgressionEventOutbox extends SavedData {
    private static final String NAME = Arffornia.MODID + "_progression_events";

    public record Pending(long progressionId, String id, String milestoneKey, long occurredAt, Optional<String> actor) {
        static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("progression").forGetter(Pending::progressionId),
                Codec.STRING.fieldOf("id").forGetter(Pending::id),
                Codec.STRING.fieldOf("milestone").forGetter(Pending::milestoneKey),
                Codec.LONG.fieldOf("occurred_at").forGetter(Pending::occurredAt),
                Codec.STRING.optionalFieldOf("actor").forGetter(Pending::actor)
        ).apply(instance, Pending::new));

        ArfforniaApiDtos.ProgressionEvent toDto() {
            return new ArfforniaApiDtos.ProgressionEvent(id, ArfforniaApiDtos.ProgressionEvent.MILESTONE_COMPLETED, milestoneKey, occurredAt, actor.orElse(null));
        }
    }

    private static final Codec<List<Pending>> LIST_CODEC = Pending.CODEC.listOf();

    private final List<Pending> pending = new ArrayList<>();

    public static ProgressionEventOutbox get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProgressionEventOutbox::new, ProgressionEventOutbox::load, null), NAME);
    }

    private static ProgressionEventOutbox load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressionEventOutbox outbox = new ProgressionEventOutbox();
        LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("events"))
                .resultOrPartial(error -> Arffornia.LOGGER.error("Could not read pending progression events: {}", error))
                .ifPresent(outbox.pending::addAll);
        return outbox;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("events", LIST_CODEC.encodeStart(NbtOps.INSTANCE, pending).getOrThrow());
        return tag;
    }

    public void add(long progressionId, String eventId, String milestoneKey, long occurredAt, @Nullable String actor) {
        pending.add(new Pending(progressionId, eventId, milestoneKey, occurredAt, Optional.ofNullable(actor)));
        setDirty();
    }

    /** The oldest pending events of one progression, at most {@code limit}. */
    public List<Pending> oldest(long progressionId, int limit) {
        return pending.stream().filter(event -> event.progressionId() == progressionId).limit(limit).toList();
    }

    public Set<Long> progressionIds() {
        return pending.stream().map(Pending::progressionId).collect(Collectors.toUnmodifiableSet());
    }

    /** Milestones completed in game that the website does not know yet; they must survive a refresh of the state. */
    public Set<String> pendingKeys(long progressionId) {
        return pending.stream().filter(event -> event.progressionId() == progressionId).map(Pending::milestoneKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void remove(Collection<String> eventIds) {
        if (pending.removeIf(event -> eventIds.contains(event.id()))) {
            setDirty();
        }
    }
}
