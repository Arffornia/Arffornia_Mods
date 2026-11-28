package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Disk cache of the splash MOTD, read by the title screen at once and by the early window before
 * Minecraft starts. Same path as {@code fr.thegostsniperfr.arffornia.earlywindow.MotdCache}.
 */
public final class MotdStore {
    static final String RELATIVE_PATH = "arffornia/motd.txt";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path file;

    public MotdStore(Path gameDir) {
        this.file = gameDir.resolve(RELATIVE_PATH);
    }

    public Optional<String> read() {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void write(String motd) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, motd + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Cannot cache the MOTD in {}: {}", file, e.toString());
        }
    }
}
