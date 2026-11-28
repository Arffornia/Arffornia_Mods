package fr.thegostsniperfr.arffornia.guide;

import fr.thegostsniperfr.arffornia.datagen.GuideMarkdown;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideSourceTest {
    private static final Path GUIDE = Path.of("src/main/resources/assets/arffornia/guides/arffornia/guide");
    private static final List<String[]> LAYERS = List.of(new String[]{"AB", "BB"}, new String[]{"A "});
    private static final Map<Character, String> IDS = Map.of('A', "arffornia:machine_casing", 'B', "minecraft:bricks");

    @Test
    void multiblockPage_listsBlockCountsMostNumerousFirst() {
        String page = GuideMarkdown.multiblockPage(GuideMarkdown.Language.EN, "Test", "arffornia:ctrl", LAYERS, IDS);

        assertTrue(page.contains("| <ItemLink id=\"minecraft:bricks\" optional /> | 3 |\n| <ItemLink id=\"arffornia:machine_casing\" optional /> | 2 |"), page);
    }

    @Test
    void multiblockPage_hasOneSceneForTheWholeStructureAndOnePerLayer() {
        String page = GuideMarkdown.multiblockPage(GuideMarkdown.Language.EN, "Test", "arffornia:ctrl", LAYERS, IDS);

        assertEquals(3, page.split("<GameScene", -1).length - 1);
    }

    @Test
    void multiblockPage_placesEachLayerAtGroundLevelInItsOwnScene() {
        String page = GuideMarkdown.multiblockPage(GuideMarkdown.Language.EN, "Test", "arffornia:ctrl", LAYERS, IDS);

        String secondLayer = page.substring(page.indexOf("### Layer 2 of 2"));
        assertTrue(secondLayer.contains("<Block id=\"arffornia:machine_casing\" x=\"0\" y=\"0\" z=\"0\" />"), secondLayer);
    }

    @Test
    void multiblockPage_isTranslated() {
        String page = GuideMarkdown.multiblockPage(GuideMarkdown.Language.FR, "Test", "arffornia:ctrl", LAYERS, IDS);

        assertTrue(page.contains("### Couche 2 sur 2"), page);
    }

    @Test
    void everyDefaultPageHasAFrenchTranslation() throws IOException {
        try (Stream<Path> pages = Files.walk(GUIDE)) {
            List<Path> handWritten = pages.filter(path -> path.toString().endsWith(".md"))
                    .map(GUIDE::relativize)
                    .filter(path -> !path.startsWith("_fr_fr"))
                    .toList();

            assertTrue(!handWritten.isEmpty());
            for (Path page : handWritten) {
                assertTrue(Files.exists(GUIDE.resolve("_fr_fr").resolve(page)), "missing French page " + page);
            }
        }
    }

    @Test
    void everyPageDeclaresNavigationOrItIsTheRoot() throws IOException {
        Pattern navigation = Pattern.compile("(?s)\\A---\\nnavigation:.*?\\n---\\n");
        try (Stream<Path> pages = Files.walk(GUIDE)) {
            for (Path page : pages.filter(path -> path.toString().endsWith(".md")).toList()) {
                assertTrue(navigation.matcher(Files.readString(page)).find(), "no navigation front matter in " + page);
            }
        }
    }
}
