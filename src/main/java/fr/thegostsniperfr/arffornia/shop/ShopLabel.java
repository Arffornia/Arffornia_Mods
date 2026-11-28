package fr.thegostsniperfr.arffornia.shop;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

final class ShopLabel {
    private static final String FALLBACK_LANGUAGE = "en";

    private ShopLabel() {
    }

    /** The client's language ("fr_fr" reads as "fr"), then English, then any non-blank value; empty when there is none. */
    static String resolve(Map<String, String> label, @Nullable String clientLanguage) {
        String preferred = clientLanguage == null ? "" : clientLanguage.toLowerCase(Locale.ROOT).split("_", 2)[0];
        for (String language : new String[]{preferred, FALLBACK_LANGUAGE}) {
            String value = label.get(language);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return label.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
    }
}
