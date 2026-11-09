package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side copy of one website progression: what machines placed for it may use.
 * A retired progression belongs to a soft-deleted team; its machines stop.
 */
public record ProgressionSnapshot(long id, Set<Integer> completedMilestones, @Nullable Integer currentMilestoneId,
                                  boolean retired) {

    public static final Codec<ProgressionSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(ProgressionSnapshot::id),
            Codec.INT.listOf().fieldOf("completed").forGetter(s -> List.copyOf(s.completedMilestones())),
            Codec.INT.optionalFieldOf("target").forGetter(s -> Optional.ofNullable(s.currentMilestoneId())),
            Codec.BOOL.fieldOf("retired").forGetter(ProgressionSnapshot::retired)
    ).apply(instance, (id, completed, target, retired) -> new ProgressionSnapshot(id, Set.copyOf(completed), target.orElse(null), retired)));

    public ProgressionSnapshot {
        completedMilestones = Set.copyOf(completedMilestones);
    }

    public boolean isCompleted(int milestoneId) {
        return completedMilestones.contains(milestoneId);
    }
}
