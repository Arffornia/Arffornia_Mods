package fr.thegostsniperfr.arffornia.client.icon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IconFilesTest {

    @Test
    void iconPathIsSizeThenNamespaceThenRegistryPath() {
        assertEquals(Optional.of("128/mekanism/basic_chemical_tank.png"),
                IconFiles.relativePath("mekanism", "basic_chemical_tank", 128));
    }

    @Test
    void nestedRegistryPathsKeepTheirFolders() {
        assertEquals(Optional.of("36/chipped/oak/plank_1.png"),
                IconFiles.relativePath("chipped", "oak/plank_1", 36));
    }

    @Test
    void idsThatWouldLeaveTheExportFolderAreRefused() {
        assertEquals(Optional.empty(), IconFiles.relativePath("evil", "../../etc/passwd", 36));
    }

    @Test
    void shortHashIsTwelveHexCharactersAndStable() {
        String hash = IconFiles.shortHash(new byte[]{1, 2, 3});

        assertEquals("039058c6f2c0", hash);
    }

    @Test
    void manifestListsItemsSortedWithFormatAndSizes(@TempDir Path dir) throws Exception {
        IconFiles.writeManifest(dir, List.of(36, 128), Map.of("b:item", "bbb", "a:item", "aaa"),
                List.of("c:broken"), Instant.parse("2026-11-21T12:00:00Z"));

        JsonObject manifest = JsonParser.parseString(Files.readString(dir.resolve("manifest.json"))).getAsJsonObject();
        assertEquals(List.of("a:item", "b:item"), List.copyOf(manifest.getAsJsonObject("items").keySet()));
        assertEquals(1, manifest.get("format").getAsInt());
        assertEquals(2, manifest.get("count").getAsInt());
    }

    @Test
    void sizesAreParsedSortedAndBounded() {
        assertEquals(List.of(36, 128), IconFiles.parseSizes("128, 36"));
        assertThrows(IllegalArgumentException.class, () -> IconFiles.parseSizes("8"));
    }
}
