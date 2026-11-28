package fr.thegostsniperfr.arffornia.shop;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopLabelTest {
    private static final Map<String, String> BOTH = Map.of("en", "Diamond pack", "fr", "Pack de diamants");

    @Test
    void labelUsesTheClientLanguageWhenPresent() {
        assertEquals("Pack de diamants", ShopLabel.resolve(BOTH, "fr_fr"));
    }

    @Test
    void labelFallsBackToEnglishWhenTheLanguageIsMissing() {
        assertEquals("Diamond pack", ShopLabel.resolve(BOTH, "de_de"));
    }

    @Test
    void labelFallsBackToEnglishWhenTheLanguageValueIsBlank() {
        assertEquals("Diamond pack", ShopLabel.resolve(Map.of("en", "Diamond pack", "fr", "  "), "fr_fr"));
    }

    @Test
    void labelFallsBackToTheFirstNonBlankValueWhenEnglishIsMissing() {
        assertEquals("Pack", ShopLabel.resolve(Map.of("en", "", "es", "Pack"), "fr_fr"));
    }

    @Test
    void labelIsEmptyWhenNothingIsUsable() {
        assertEquals("", ShopLabel.resolve(Map.of(), "fr_fr"));
    }

    @Test
    void labelAcceptsAMissingClientLanguage() {
        assertEquals("Diamond pack", ShopLabel.resolve(BOTH, null));
    }
}
