package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side copy of one website progression: what machines placed for it may use.
 * A retired progression belongs to a soft-deleted team; its machines stop.
 * <p>
 * {@code completedMilestones} are row ids from the old routes (the current datapack gates by them);
 * {@code completedKeys} and {@code openStage} come from the keyed state of spec 017. {@code openStage} is
 * {@link #UNKNOWN_STAGE} until that state was read once.
 */
public record ProgressionSnapshot(long id, Set<Integer> completedMilestones, @Nullable Integer currentMilestoneId,
                                  boolean retired, Set<String> completedKeys, int openStage) {
    public static final int UNKNOWN_STAGE = 0;

    public static final Codec<ProgressionSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(ProgressionSnapshot::id),
            Codec.INT.listOf().fieldOf("completed").forGetter(s -> List.copyOf(s.completedMilestones())),
            Codec.INT.optionalFieldOf("target").forGetter(s -> Optional.ofNullable(s.currentMilestoneId())),
            Codec.BOOL.fieldOf("retired").forGetter(ProgressionSnapshot::retired),
            Codec.STRING.listOf().optionalFieldOf("completed_keys", List.of()).forGetter(s -> List.copyOf(s.completedKeys())),
            Codec.INT.optionalFieldOf("open_stage", UNKNOWN_STAGE).forGetter(ProgressionSnapshot::openStage)
    ).apply(instance, (id, completed, target, retired, keys, openStage) ->
            new ProgressionSnapshot(id, Set.copyOf(completed), target.orElse(null), retired, Set.copyOf(keys), openStage)));

    public ProgressionSnapshot {
        completedMilestones = Set.copyOf(completedMilestones);
        completedKeys = Set.copyOf(completedKeys);
    }

    /** A progression known only through the old routes. */
    public ProgressionSnapshot(long id, Set<Integer> completedMilestones, @Nullable Integer currentMilestoneId, boolean retired) {
        this(id, completedMilestones, currentMilestoneId, retired, Set.of(), UNKNOWN_STAGE);
    }

    public boolean isCompleted(int milestoneId) {
        return completedMilestones.contains(milestoneId);
    }

    public boolean isCompleted(String milestoneKey) {
        return completedKeys.contains(milestoneKey);
    }

    public boolean isKeyedStateKnown() {
        return openStage != UNKNOWN_STAGE;
    }

    /** Stages are numbered from 1 and the first one is always open, also before the keyed state is known. */
    public boolean isStageOpen(int stage) {
        return stage <= Math.max(1, openStage);
    }

    public ProgressionSnapshot withCompletedMilestone(int milestoneId) {
        Set<Integer> completed = new HashSet<>(completedMilestones);
        completed.add(milestoneId);
        return new ProgressionSnapshot(id, completed, currentMilestoneId, retired, completedKeys, openStage);
    }

    public ProgressionSnapshot withCompletedKey(String milestoneKey) {
        Set<String> keys = new HashSet<>(completedKeys);
        keys.add(milestoneKey);
        return new ProgressionSnapshot(id, completedMilestones, currentMilestoneId, retired, keys, openStage);
    }

    public ProgressionSnapshot withoutCompletedKey(String milestoneKey) {
        Set<String> keys = new HashSet<>(completedKeys);
        keys.remove(milestoneKey);
        return new ProgressionSnapshot(id, completedMilestones, currentMilestoneId, retired, keys, openStage);
    }

    public ProgressionSnapshot withKeyedState(Set<String> keys, int stage, boolean isRetired) {
        return new ProgressionSnapshot(id, completedMilestones, currentMilestoneId, retired || isRetired, keys, stage);
    }

    public ProgressionSnapshot withOpenStage(int stage) {
        return new ProgressionSnapshot(id, completedMilestones, currentMilestoneId, retired, completedKeys, stage);
    }
}
