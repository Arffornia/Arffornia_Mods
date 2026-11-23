package fr.thegostsniperfr.arffornia.api.dto;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects (DTOs) that directly map to the JSON structure of the Arffornia API.
 * Using records makes this clean and immutable. @SerializedName is used to map
 * snake_case JSON keys to camelCase Java fields.
 */
public class ArfforniaApiDtos {

    /**
     * Maps to the root JSON object from the main graph endpoint.
     */
    public record GraphData(
            List<GraphDefinition> graphs,
            List<ApiMilestone> milestones,
            @SerializedName("milestone_closure") List<ApiMilestoneClosure> milestoneClosure,
            @SerializedName("playerProgress") PlayerProgress playerProgress
    ) {
    }

    public record PlayerProgress(
            @SerializedName("completed_milestones") List<Integer> completedMilestones,
            @SerializedName("current_target_id") @Nullable Integer currentTargetId,
            @SerializedName("max_stage_number") Integer maxStageNumber
    ) {
    }

    public record ProgressionData(
            int id,
            @SerializedName("completed_milestones") List<Integer> completedMilestones,
            @SerializedName("current_milestone_id") @Nullable Integer currentMilestoneId,
            boolean retired
    ) {
    }

    public record GraphDefinition(
            int id,
            String name,
            @SerializedName("icon_item_id") String iconItemId,
            List<String> categories
    ) {
    }

    /**
     * Maps to a single milestone object in the main graph list.
     */
    public record ApiMilestone(
            int id,
            @SerializedName("icon_type") String iconType,
            int x,
            int y,
            @SerializedName("stage_number") int stageNumber,
            @SerializedName("graph_id") int graphId,
            @SerializedName("first_unlock_item_id") @Nullable String firstUnlockItemId
    ) {
    }

    /**
     * Maps to a single link object in the milestone_closure list.
     */
    public record ApiMilestoneClosure(
            @SerializedName("milestone_id")
            int milestoneId,
            @SerializedName("descendant_id")
            int descendantId
    ) {
    }

    /**
     * Represents a single item unlocked by a milestone.
     */
    public record MilestoneUnlock(
            @SerializedName("item_id") String itemId,
            @SerializedName("display_name") String displayName,
            @SerializedName("shop_price") @Nullable Integer shopPrice
    ) {
    }

    /**
     * Represents a single item required to complete a milestone.
     */
    public record MilestoneRequirement(
            @SerializedName("item_id") String itemId,
            @SerializedName("display_name") String displayName,
            int amount
    ) {
    }


    /**
     * Maps to the detailed JSON object for a single milestone.
     */
    public record MilestoneDetails(
            int id,
            @Nullable String key,
            String name,
            String description,
            @SerializedName("stage_id") int stageId,
            @SerializedName("stage_number") @Nullable Integer stageNumber,
            @SerializedName("reward_progress_points") int rewardProgressPoints,
            List<MilestoneUnlock> unlocks,
            List<MilestoneRequirement> requirements
    ) {
    }

    public record PlayerData(
            @SerializedName("active_progression_id")
            long activeProgressionId
    ) {
    }

    /** {@code GET /progressions/{id}/state} (spec 017), keyed by milestone key. Only the fields the game uses. */
    public record ProgressionState(
            @SerializedName("progression_id") long progressionId,
            @SerializedName("snapshot_version") int snapshotVersion,
            boolean retired,
            @SerializedName("open_stage") int openStage,
            List<CompletedMilestone> completed
    ) {
    }

    public record CompletedMilestone(String key, @SerializedName("completed_at") String completedAt) {
    }

    /** One entry of {@code POST /progressions/{id}/events}; only {@code milestone_completed} is sent by the game today. */
    public record ProgressionEvent(
            String id,
            String type,
            String milestone,
            @SerializedName("occurred_at") long occurredAt,
            @Nullable String actor
    ) {
        public static final String MILESTONE_COMPLETED = "milestone_completed";
    }

    /** {@code GET /progression/model}: the published snapshot, reduced to what availability (rule 3) needs. */
    public record ModelDocument(int version, List<ModelMilestone> milestones) {
    }

    public record ModelMilestone(String key, int stage, @Nullable List<String> requires, @Nullable Map<String, String> title) {
    }

    /**
     * Outcome of a model read.
     *
     * @param status   HTTP status (304 when the cached ETag still matches), or 0 when no answer came back
     * @param etag     the ETag of the answered document, without quotes
     * @param document the parsed document of a 200, else null
     */
    public record ModelReply(int status, @Nullable String etag, @Nullable ModelDocument document) {
    }

    public record EventResult(String id, String status, @Nullable String reason) {
        public static final String APPLIED = "applied";
        public static final String DUPLICATE = "duplicate";
        public static final String REJECTED = "rejected";
    }

    public record EventsResponse(List<EventResult> results, @Nullable ProgressionState state) {
    }

    /**
     * Outcome of an events call.
     *
     * @param status HTTP status, or 0 when no answer came back (network error, no service token)
     * @param body   the parsed answer of a 2xx, else null
     */
    public record EventsReply(int status, @Nullable EventsResponse body) {
        public static EventsReply noAnswer() {
            return new EventsReply(0, null);
        }
    }
}
