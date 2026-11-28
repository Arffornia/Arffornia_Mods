package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TitleFontsTest {
    @ParameterizedTest(name = "{0} px per em -> oversample {1}")
    @CsvSource({ "12, 1", "24, 2", "240, 20", "960, 20", "1, 0.5", "15, 1.25", "26, 2" })
    void picksTheTierClosestToThePhysicalSize(float pixelsPerEm, float tier) {
        assertEquals(tier, TitleFonts.tier(pixelsPerEm));
    }

    @Test
    void everyFamilyHasADefinitionPerTierWithThatOversample() throws Exception {
        for (String family : TitleFonts.FAMILIES) {
            for (float tier : TitleFonts.TIERS) {
                String path = "/assets/arffornia/font/" + family + "/" + TitleFonts.tierName(tier) + ".json";
                try (var in = TitleFontsTest.class.getResourceAsStream(path)) {
                    assertNotNull(in, path + " is missing: run tools/fonts/title_fonts.py");
                    JsonObject ttf = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject().getAsJsonArray("providers").get(0).getAsJsonObject();
                    assertEquals(tier, ttf.get("oversample").getAsFloat(), path);
                    assertEquals(TitleFonts.EM, ttf.get("size").getAsFloat(), path);
                }
            }
        }
    }
}
