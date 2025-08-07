package fr.thegostsniperfr.arffornia.api.dto;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Data Transfer Objects (DTOs) that directly map to the JSON structure of the Arffornia API.
 * Using records makes this clean and immutable. @SerializedName is used to map
 * snake_case JSON keys to camelCase Java fields.
 */
public class ArfforniaApiDtos {

    /** Maps to the root JSON object from the main graph endpoint. */
    public record GraphData(
            List<ApiMilestone> milestones,
            @SerializedName("milestone_closure") List<ApiMilestoneClosure> milestoneClosure,
            @SerializedName("playerProgress") PlayerProgress playerProgress
    ) {}

    public record PlayerProgress(
            @SerializedName("completed_milestones") List<Integer> completedMilestones,
            @SerializedName("current_target_id") @Nullable Integer currentTargetId,
            @SerializedName("max_stage_number") Integer maxStageNumber
    ) {}

    public record ProgressionData(
            int id,
            @SerializedName("completed_milestones") List<Integer> completedMilestones,
            @SerializedName("current_milestone_id") @Nullable Integer currentMilestoneId
    ) {}

    /** Maps to a single milestone object in the main graph list. */
    public record ApiMilestone(
            int id,
            @SerializedName("icon_type")
            String iconType,
            int x,
            int y,
            @SerializedName("stage_number")
            int stageNumber
    ) {}

    /** Maps to a single link object in the milestone_closure list. */
    public record ApiMilestoneClosure(
            @SerializedName("milestone_id")
            int milestoneId,
            @SerializedName("descendant_id")
            int descendantId
    ) {}

    /**
     * Represents a single item unlocked by a milestone.
     */
    public record MilestoneUnlock(
            @SerializedName("item_id") String itemId,
            @SerializedName("display_name") String displayName,
            @SerializedName("shop_price") @Nullable Integer shopPrice
    ) {}

    /**
     * Represents a single item required to complete a milestone.
     */
    public record MilestoneRequirement(
            @SerializedName("item_id") String itemId,
            @SerializedName("display_name") String displayName,
            int amount
    ) {}


    /** Maps to the detailed JSON object for a single milestone. */
    public record MilestoneDetails(
            int id,
            String name,
            String description,
            @SerializedName("stage_id") int stageId,
            @SerializedName("stage_number") @Nullable Integer stageNumber,
            @SerializedName("reward_progress_points") int rewardProgressPoints,
            List<MilestoneUnlock> unlocks,
            List<MilestoneRequirement> requirements
    ) {}


    /**
     * Maps to the JSON object from the /api/progression/config endpoint.
     * This contains server-wide configuration data for the mod.
     */
    public record ProgressionConfig(
            @SerializedName("banned_recipes")
            List<String> bannedRecipes
    ) {}

    public record PlayerData(
            @SerializedName("active_progression_id")
            long activeProgressionId
    ) {}
}