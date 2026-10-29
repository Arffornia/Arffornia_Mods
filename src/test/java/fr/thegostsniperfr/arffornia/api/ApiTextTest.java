package fr.thegostsniperfr.arffornia.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiTextTest {

    @Test
    void convertsMinecraftLanguageCodesToLanguageTags() {
        assertEquals("fr-FR", ApiText.toLanguageTag("fr_fr"));
        assertEquals("en-US", ApiText.toLanguageTag("en_us"));
        assertEquals("fr-CA", ApiText.toLanguageTag("FR_CA"));
        assertEquals("fr", ApiText.toLanguageTag("fr"));
        assertEquals("lol-US", ApiText.toLanguageTag("lol_us"));
    }

    @Test
    void sendsNoLanguageForMissingOrMalformedCodes() {
        assertNull(ApiText.toLanguageTag(null));
        assertNull(ApiText.toLanguageTag(""));
        assertNull(ApiText.toLanguageTag("  "));
        assertNull(ApiText.toLanguageTag("f"));
        assertNull(ApiText.toLanguageTag("fr fr\r\nX-Injected: 1"));
        // A malformed region is dropped, the language is kept.
        assertEquals("fr", ApiText.toLanguageTag("fr_!!"));
    }

    @Test
    void itemFallbackPrefersTheApiNameThenTheId() {
        assertEquals("Osmium Ingot", ApiText.itemFallbackName("mekanism:ingot_osmium", "Osmium Ingot"));
        assertEquals("mekanism:ingot_osmium", ApiText.itemFallbackName("mekanism:ingot_osmium", " "));
        assertEquals("mekanism:ingot_osmium", ApiText.itemFallbackName("mekanism:ingot_osmium", null));
        assertEquals("", ApiText.itemFallbackName(null, null));
    }
}
