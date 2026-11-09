package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage keys gate machine recipes and multiblock formation. Milestones have no stable keys yet (spec 017), so the
 * website names a milestone's stage {@code arffornia:milestone/<id>} until they do.
 */
public final class Stages {
    private static final String MILESTONE_PREFIX = Arffornia.MODID + ":milestone/";

    private Stages() {
    }

    public static String milestoneStage(int milestoneId) {
        return MILESTONE_PREFIX + milestoneId;
    }

    static Optional<Integer> milestoneOf(String stage) {
        if (!stage.startsWith(MILESTONE_PREFIX)) {
            return Optional.empty();
        }
        try {
            int milestoneId = Integer.parseInt(stage.substring(MILESTONE_PREFIX.length()));
            return stage.equals(milestoneStage(milestoneId)) ? Optional.of(milestoneId) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Whether a machine placed for {@code progressionId} may use what {@code stage} gates. Unknown and retired progressions fail closed. */
    public static boolean isUnlocked(MinecraftServer server, long progressionId, Optional<String> stage) {
        return Progressions.find(server, progressionId)
                .filter(snapshot -> !snapshot.retired())
                .map(snapshot -> stage.isEmpty() || milestoneOf(stage.get()).map(snapshot::isCompleted).orElse(false))
                .orElse(false);
    }

    public static Set<String> unlockedStages(MinecraftServer server, long progressionId) {
        return Progressions.find(server, progressionId)
                .filter(snapshot -> !snapshot.retired())
                .map(snapshot -> ofMilestones(snapshot.completedMilestones()))
                .orElse(Set.of());
    }

    public static Set<String> ofMilestones(Collection<Integer> completedMilestoneIds) {
        return completedMilestoneIds.stream().map(Stages::milestoneStage).collect(Collectors.toUnmodifiableSet());
    }
}
