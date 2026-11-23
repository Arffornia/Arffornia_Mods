package fr.thegostsniperfr.arffornia.earlywindow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArfforniaEarlyWindowTest {
    @Test
    void fmlDisplayWindowStillHasEveryFieldTheProviderReplaces() {
        for (String name : ArfforniaEarlyWindow.requiredFields()) {
            assertDoesNotThrow(() -> DisplayWindow.class.getDeclaredField(name), name);
        }
    }

    @Test
    void fmlDisplayWindowStillPaintsThroughRenderThreadFunc() {
        assertDoesNotThrow(() -> DisplayWindow.class.getDeclaredMethod("renderThreadFunc"));
    }

    @Test
    void isRegisteredAsAnImmediateWindowProviderService() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/services/net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider")) {
            assertEquals(ArfforniaEarlyWindow.class.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());
        }
    }

    @Test
    void answersToTheNameShippedInFmlToml() {
        assertEquals("arffornia", new ArfforniaEarlyWindow().name());
    }

    @Test
    void readsTheMinecraftVersionFromTheLaunchArguments() {
        assertEquals("1.21.1", ArfforniaEarlyWindow.argument(new String[] { "--fml.mcVersion", "1.21.1" }, "--fml.mcVersion", "?"));
    }

    @Test
    void usesTheCachedMotdWhenTheTitleScreenSavedOne(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("arffornia"));
        Files.writeString(dir.resolve(MotdCache.RELATIVE_PATH), "  Your factory is not big enough. Yet.\n");

        assertEquals("Your factory is not big enough. Yet.", MotdCache.read(dir).orElseThrow());
    }

    @Test
    void hasNoMotdOnFirstLaunch(@TempDir Path dir) {
        assertEquals(java.util.Optional.empty(), MotdCache.read(dir));
    }
}
