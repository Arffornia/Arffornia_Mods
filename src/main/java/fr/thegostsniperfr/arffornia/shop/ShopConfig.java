package fr.thegostsniperfr.arffornia.shop;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Reward delivery settings. This is registered within the main Config class.
 */
public class ShopConfig {
    public static ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_PERMISSION_PREFIXES;
    public static ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_RANK_GROUPS;

    public static void register(ModConfigSpec.Builder builder) {
        builder.comment("Shop, vote and daily streak reward delivery (typed rewards from the website API)").push("rewards");

        ALLOWED_PERMISSION_PREFIXES = builder
                .comment("LuckPerms permission nodes a reward may grant must start with one of these prefixes.",
                        "A reward with any other node is not delivered and stays pending on the website.")
                .defineListAllowEmpty("allowedPermissionPrefixes", List.of("cosmetic.", "arffornia.reward."), () -> "cosmetic.",
                        o -> o instanceof String str && !str.isBlank());

        ALLOWED_RANK_GROUPS = builder
                .comment("LuckPerms groups a reward may add to a player, e.g. [\"vip\"]. Empty: rank rewards are not delivered.")
                .defineListAllowEmpty("allowedRankGroups", List.of(), () -> "vip",
                        o -> o instanceof String str && !str.isBlank());

        builder.pop();
    }
}
