package fr.thegostsniperfr.arffornia.recipe.pack;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipePackValidatorTest {

    private static final String META = "{\"pack\":{\"pack_format\":48,\"description\":\"Arffornia\"}}";
    private static final String RECIPE = "{\"type\":\"arffornia:machine\",\"machine\":\"arffornia:crafter\"}";
    private static final String BAN = "{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}]}";

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, String> validPack() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", META);
        files.put("data/arffornia/recipe/iron_plate.json", RECIPE);
        files.put("data/minecraft/recipe/iron_block.json", BAN);
        return files;
    }

    @Test
    void validate_countsRecipeFilesAndHashesContent_whenPackIsValid() throws Exception {
        byte[] pack = zip(validPack());

        RecipePackValidator.Summary summary = RecipePackValidator.validate(pack);

        assertEquals(2, summary.recipeFiles());
        assertEquals(RecipePackValidator.sha256(pack), summary.sha256());
    }

    @Test
    void validate_rejects_whenPackMetaIsMissing() throws Exception {
        Map<String, String> files = validPack();
        files.remove("pack.mcmeta");

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(zip(files)));
    }

    @Test
    void validate_rejects_whenPackMetaHasNoFormat() throws Exception {
        Map<String, String> files = validPack();
        files.put("pack.mcmeta", "{\"pack\":{\"description\":\"x\"}}");

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(zip(files)));
    }

    @Test
    void validate_rejects_whenARecipeIsNotValidJson() throws Exception {
        Map<String, String> files = validPack();
        files.put("data/arffornia/recipe/broken.json", "{\"type\": ");

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(zip(files)));
    }

    @Test
    void validate_rejects_whenAnEntryEscapesThePack() throws Exception {
        Map<String, String> files = validPack();
        files.put("data/../../evil.json", RECIPE);

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(zip(files)));
    }

    @Test
    void validate_rejects_whenPackCarriesFilesOtherThanRecipes() throws Exception {
        Map<String, String> files = validPack();
        files.put("data/minecraft/tags/function/load.json", "{\"values\":[\"evil:run\"]}");

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(zip(files)));
    }

    @Test
    void validate_rejects_whenDownloadIsTruncated() throws Exception {
        byte[] pack = zip(validPack());
        byte[] truncated = Arrays.copyOf(pack, pack.length / 2);

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(truncated));
    }

    @Test
    void validate_rejects_whenBodyIsNotAZip() {
        byte[] html = "<html>maintenance</html>".getBytes(StandardCharsets.UTF_8);

        assertThrows(RecipePackValidator.InvalidPackException.class, () -> RecipePackValidator.validate(html));
    }

    @Test
    void etagSha_extractsHexDigest_fromQuotedAndWeakTags() {
        String sha = "a".repeat(64);

        assertEquals(sha, RecipePackUpdater.etagSha("\"" + sha + "\""));
        assertEquals(sha, RecipePackUpdater.etagSha("W/\"" + sha.toUpperCase() + "\""));
        assertTrue(RecipePackUpdater.etagSha("\"abc\"").isEmpty());
    }
}
