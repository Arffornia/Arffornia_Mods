package fr.thegostsniperfr.arffornia.progression;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

/** Outcome of completing a milestone for a progression (spec 017 rule 3). Only {@link Completed} records anything. */
public sealed interface MilestoneResult {
    Completed COMPLETED = new Completed();
    AlreadyDone ALREADY_DONE = new AlreadyDone();
    UnknownMilestone UNKNOWN_MILESTONE = new UnknownMilestone();
    NoProgression NO_PROGRESSION = new NoProgression();
    Retired RETIRED = new Retired();
    NotReady NOT_READY = new NotReady();
    Refused REFUSED = new Refused();

    record Completed() implements MilestoneResult {
    }

    record AlreadyDone() implements MilestoneResult {
    }

    record StageClosed(int stage) implements MilestoneResult {
    }

    record MissingPrerequisites(List<String> milestoneKeys) implements MilestoneResult {
        public MissingPrerequisites {
            milestoneKeys = List.copyOf(milestoneKeys);
        }
    }

    record UnknownMilestone() implements MilestoneResult {
    }

    record NoProgression() implements MilestoneResult {
    }

    record Retired() implements MilestoneResult {
    }

    /** The progression or the model is still loading. */
    record NotReady() implements MilestoneResult {
    }

    /** The website refused this milestone for this progression; it is not tried again until more milestones are available. */
    record Refused() implements MilestoneResult {
    }

    default boolean isCompleted() {
        return this instanceof Completed;
    }

    /** @param titleOf display name of a milestone key, for the prerequisites list */
    default Component message(String milestoneName, Function<String, String> titleOf) {
        String prefix = "message.arffornia.milestone.";
        return switch (this) {
            case Completed ignored -> Component.translatable(prefix + "completed", milestoneName).withStyle(ChatFormatting.GREEN);
            case AlreadyDone ignored -> Component.translatable(prefix + "already_done", milestoneName).withStyle(ChatFormatting.YELLOW);
            case StageClosed closed -> Component.translatable(prefix + "stage_closed", milestoneName, closed.stage()).withStyle(ChatFormatting.RED);
            case MissingPrerequisites missing -> Component.translatable(prefix + "missing_prerequisites", milestoneName,
                    String.join(", ", missing.milestoneKeys().stream().map(titleOf).toList())).withStyle(ChatFormatting.RED);
            case UnknownMilestone ignored -> Component.translatable(prefix + "unknown", milestoneName).withStyle(ChatFormatting.RED);
            case NoProgression ignored -> Component.translatable(prefix + "no_progression").withStyle(ChatFormatting.RED);
            case Retired ignored -> Component.translatable(prefix + "retired").withStyle(ChatFormatting.RED);
            case NotReady ignored -> Component.translatable(prefix + "not_ready").withStyle(ChatFormatting.YELLOW);
            case Refused ignored -> Component.translatable(prefix + "refused", milestoneName).withStyle(ChatFormatting.RED);
        };
    }
}
