package fr.thegostsniperfr.arffornia.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a multiblock page of the GuideME guide from its layer pattern. The page uses only tags the website wiki also
 * renders (spec 021): ItemLink, Block inside GameScene, and plain Markdown.
 */
public final class GuideMarkdown {
    public enum Language {
        EN("Blocks needed", "Block", "Count", "Whole structure", "Layer by layer", "Layer %d of %d",
                "Build the structure, then use the Wrench on %s to form it."),
        FR("Blocs nécessaires", "Bloc", "Quantité", "Structure complète", "Couche par couche", "Couche %d sur %d",
                "Construisez la structure, puis utilisez la clé sur %s pour la former.");

        final String blocks;
        final String blockColumn;
        final String countColumn;
        final String whole;
        final String byLayer;
        final String layerTitle;
        final String howToForm;

        Language(String blocks, String blockColumn, String countColumn, String whole, String byLayer, String layerTitle, String howToForm) {
            this.blocks = blocks;
            this.blockColumn = blockColumn;
            this.countColumn = countColumn;
            this.whole = whole;
            this.byLayer = byLayer;
            this.layerTitle = layerTitle;
            this.howToForm = howToForm;
        }
    }

    private GuideMarkdown() {
    }

    /**
     * @param layers  bottom to top; rows from the front to the back, characters from west to east, space is empty
     * @param blockIds registry id of each pattern character
     */
    public static String multiblockPage(Language language, String title, String triggerId, List<String[]> layers, Map<Character, String> blockIds) {
        StringBuilder page = new StringBuilder();
        page.append("---\nnavigation:\n  title: ").append(title).append("\n  parent: multiblocks/index.md\n  icon: ").append(triggerId)
                .append("\nitem_ids:\n  - ").append(triggerId).append("\n---\n\n# ").append(title).append("\n\n")
                .append(language.howToForm.formatted("<ItemLink id=\"" + triggerId + "\" optional />")).append("\n\n");

        page.append("## ").append(language.blocks).append("\n\n| ").append(language.blockColumn).append(" | ").append(language.countColumn)
                .append(" |\n|---|---|\n");
        counts(layers, blockIds).forEach((id, count) -> page.append("| <ItemLink id=\"").append(id).append("\" optional /> | ").append(count).append(" |\n"));

        page.append("\n## ").append(language.whole).append("\n\n");
        scene(page, layers, 0, layers.size(), blockIds, true);

        page.append("\n## ").append(language.byLayer).append("\n");
        for (int y = 0; y < layers.size(); y++) {
            page.append("\n### ").append(language.layerTitle.formatted(y + 1, layers.size())).append("\n\n");
            scene(page, layers, y, y + 1, blockIds, false);
        }
        return page.toString();
    }

    static Map<String, Integer> counts(List<String[]> layers, Map<Character, String> blockIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String[] layer : layers) {
            for (String row : layer) {
                for (char key : row.toCharArray()) {
                    if (key != ' ') {
                        counts.merge(blockIds.get(key), 1, Integer::sum);
                    }
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    private static void scene(StringBuilder page, List<String[]> layers, int from, int to, Map<Character, String> blockIds, boolean zoomOut) {
        page.append("<GameScene zoom=\"").append(zoomOut ? 3 : 4).append("\">\n");
        for (int y = from; y < to; y++) {
            String[] layer = layers.get(y);
            for (int z = 0; z < layer.length; z++) {
                for (int x = 0; x < layer[z].length(); x++) {
                    char key = layer[z].charAt(x);
                    if (key != ' ') {
                        page.append("  <Block id=\"").append(blockIds.get(key)).append("\" x=\"").append(x).append("\" y=\"").append(y - from)
                                .append("\" z=\"").append(z).append("\" />\n");
                    }
                }
            }
        }
        page.append("</GameScene>\n");
    }
}
