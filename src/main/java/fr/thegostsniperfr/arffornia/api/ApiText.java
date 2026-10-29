package fr.thegostsniperfr.arffornia.api;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helpers for text that comes from the Arffornia API (see the docs decision record
 * "Player-visible text in data and APIs", ADR 0008).
 * <ul>
 *   <li>Requests made for a player carry that player's game language in {@code Accept-Language}, so the
 *       API can answer in it (the website falls back to English).</li>
 *   <li>Item names stored by the API are only a fallback: the game's own, translated item name wins.</li>
 * </ul>
 * Pure Java on purpose (no game classes), so it is unit-tested without booting Minecraft.
 */
public final class ApiText {
    public static final String ACCEPT_LANGUAGE = "Accept-Language";

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,8}");
    private static final Pattern REGION = Pattern.compile("[a-z0-9]{2,8}");

    private ApiText() {
    }

    /**
     * Converts a Minecraft language code to a BCP 47 tag for {@code Accept-Language}:
     * {@code fr_fr -> fr-FR}, {@code en_us -> en-US}, {@code fr -> fr}.
     *
     * @return the tag, or {@code null} when the code is missing or malformed (send no header then).
     */
    public static @Nullable String toLanguageTag(@Nullable String minecraftLanguageCode) {
        if (minecraftLanguageCode == null || minecraftLanguageCode.isBlank()) {
            return null;
        }
        String[] parts = minecraftLanguageCode.trim().toLowerCase(Locale.ROOT).split("_", 2);
        if (!LANGUAGE.matcher(parts[0]).matches()) {
            return null;
        }
        if (parts.length == 2 && REGION.matcher(parts[1]).matches()) {
            return parts[0] + "-" + parts[1].toUpperCase(Locale.ROOT);
        }
        return parts[0];
    }

    /**
     * Name to show for an item that this game does not know (otherwise its translated name is used):
     * the API's display name, else the raw item id, else an empty string.
     */
    public static String itemFallbackName(@Nullable String itemId, @Nullable String apiDisplayName) {
        if (apiDisplayName != null && !apiDisplayName.isBlank()) {
            return apiDisplayName;
        }
        return itemId == null ? "" : itemId;
    }
}
