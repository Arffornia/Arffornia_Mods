package fr.thegostsniperfr.arffornia.earlywindow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The server MOTD as the last title screen saw it (spec 007 D13/D18). The title screen writes this file;
 * keep the path in sync with {@code fr.thegostsniperfr.arffornia.client.screen.title.MotdStore}.
 */
public final class MotdCache {
    public static final String RELATIVE_PATH = "arffornia/motd.txt";
    static final int MAX_LENGTH = 120;

    private MotdCache() {}

    public static Optional<String> read(Path gameDir) {
        try {
            String text = Files.readString(gameDir.resolve(RELATIVE_PATH), StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
