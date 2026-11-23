package fr.thegostsniperfr.arffornia.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EarlyStringsTest {
    @Test
    void readsTheLanguageFromOptionsTxt(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("options.txt"), "version:3955\nlang:fr_fr\n");

        var strings = EarlyStrings.forGameDir(dir);

        assertEquals("Chargement des mods", strings.get("gui.arffornia.loading.stage.mods"));
    }

    @Test
    void fallsBackToEnglishWithoutOptionsTxt(@TempDir Path dir) {
        var strings = EarlyStrings.forGameDir(dir);

        assertEquals("en_us", strings.language());
    }

    @Test
    void fallsBackToEnglishForALanguageWithoutStrings() {
        var strings = EarlyStrings.forLanguage("de_de");

        assertEquals("Loading mods", strings.get("gui.arffornia.loading.stage.mods"));
    }

    @Test
    void everyEnglishKeyHasAFrenchValue() {
        var en = EarlyStrings.forLanguage("en_us");
        var fr = EarlyStrings.forLanguage("fr_fr");

        for (String key : new String[] { "gui.arffornia.loading.stage.boot", "gui.arffornia.loading.stage.discovery",
                "gui.arffornia.loading.stage.registries", "gui.arffornia.loading.stage.resources",
                "gui.arffornia.loading.stage.ready", "gui.arffornia.title.tagline", "gui.arffornia.brand.eyebrow" }) {
            org.junit.jupiter.api.Assertions.assertNotEquals(en.get(key, "1.21.1"), fr.get(key, "1.21.1"), key);
        }
    }

    @Test
    void formatsTheEyebrowWithTheMinecraftVersion() {
        assertEquals("MODDED MINECRAFT · NEOFORGE 1.21.1", EarlyStrings.forLanguage("en_us").get("gui.arffornia.brand.eyebrow", "1.21.1"));
    }
}
