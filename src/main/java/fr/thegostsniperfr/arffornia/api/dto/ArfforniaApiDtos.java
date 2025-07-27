package fr.thegostsniperfr.arffornia.api.dto;

import com.google.gson.annotations.SerializedName;
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
            @SerializedName("milestone_closure")
            List<ApiMilestoneClosure> milestoneClosure
    ) {}

    /** Maps to a single milestone object in the main graph list. */
    public record ApiMilestone(
            int id,
            @SerializedName("icon_type")
            String iconType,
            int x,
            int y
    ) {}

    /** Maps to a single link object in the milestone_closure list. */
    public record ApiMilestoneClosure(
            @SerializedName("milestone_id")
            int milestoneId,
            @SerializedName("descendant_id")
            int descendantId
    ) {}

    /** Maps to the detailed JSON object for a single milestone. */
    public record MilestoneDetails(
            String name,
            String description
    ) {}


    /**
     * Maps to the JSON object from the /api/progression/config endpoint.
     * This contains server-wide configuration data for the mod.
     */
    public record ProgressionConfig(
            @SerializedName("banned_recipes")
            List<String> bannedRecipes
    ) {}
}