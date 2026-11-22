package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gate keys on machine recipes and multiblock definitions. Three forms:
 * <ul>
 *   <li>{@code arffornia:stage/<n>}: stage {@code n} is open for the progression (spec 017);</li>
 *   <li>{@code arffornia:milestone/<key>}: the milestone with that stable key is done, e.g. {@code arffornia:milestone/mekanism/ore_x3};</li>
 *   <li>{@code arffornia:milestone/<id>}: all digits, the milestone row id the current website datapack still uses.
 *       It goes once the datapack is built from a snapshot. Generated keys always contain a {@code /}, so no key is all digits.</li>
 * </ul>
 * Anything else fails closed.
 */
public final class Stages {
    private static final String MILESTONE_PREFIX = Arffornia.MODID + ":milestone/";
    private static final String STAGE_PREFIX = Arffornia.MODID + ":stage/";
    private static final Pattern LEGACY_ID = Pattern.compile("[0-9]{1,9}");
    private static final Pattern STAGE_NUMBER = Pattern.compile("[1-9][0-9]{0,4}");
    /** Same pattern as the website's {@code Keys::MILESTONE_PATTERN}. */
    public static final Pattern MILESTONE_KEY = Pattern.compile("[a-z0-9_]+(/[a-z0-9_]+)?");

    private Stages() {
    }

    public static String milestoneStage(int milestoneId) {
        return MILESTONE_PREFIX + milestoneId;
    }

    public static String milestoneStage(String milestoneKey) {
        return MILESTONE_PREFIX + milestoneKey;
    }

    public static String stage(int number) {
        return STAGE_PREFIX + number;
    }

    /** Whether a machine placed for {@code progressionId} may use what {@code stage} gates. Unknown and retired progressions fail closed. */
    public static boolean isUnlocked(MinecraftServer server, long progressionId, Optional<String> stage) {
        return Progressions.find(server, progressionId)
                .filter(snapshot -> !snapshot.retired())
                .map(snapshot -> stage.isEmpty() || isUnlocked(snapshot, stage.get()))
                .orElse(false);
    }

    static boolean isUnlocked(ProgressionSnapshot snapshot, String gate) {
        if (gate.startsWith(STAGE_PREFIX)) {
            String number = gate.substring(STAGE_PREFIX.length());
            return STAGE_NUMBER.matcher(number).matches() && snapshot.isStageOpen(Integer.parseInt(number));
        }
        if (gate.startsWith(MILESTONE_PREFIX)) {
            String milestone = gate.substring(MILESTONE_PREFIX.length());
            if (LEGACY_ID.matcher(milestone).matches()) {
                return snapshot.isCompleted(Integer.parseInt(milestone));
            }
            return MILESTONE_KEY.matcher(milestone).matches() && snapshot.isCompleted(milestone);
        }
        return false;
    }

    /** Every gate key the progression has unlocked, for menus that filter recipes on the client. */
    public static Set<String> unlockedStages(MinecraftServer server, long progressionId) {
        return Progressions.find(server, progressionId)
                .filter(snapshot -> !snapshot.retired())
                .map(Stages::unlockedStages)
                .orElse(Set.of());
    }

    static Set<String> unlockedStages(ProgressionSnapshot snapshot) {
        Set<String> unlocked = new HashSet<>(ofMilestones(snapshot.completedMilestones()));
        snapshot.completedKeys().forEach(key -> unlocked.add(milestoneStage(key)));
        for (int number = 1; snapshot.isStageOpen(number); number++) {
            unlocked.add(stage(number));
        }
        return Set.copyOf(unlocked);
    }

    public static Set<String> ofMilestones(Collection<Integer> completedMilestoneIds) {
        return completedMilestoneIds.stream().map(Stages::milestoneStage).collect(Collectors.toUnmodifiableSet());
    }
}
