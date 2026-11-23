package fr.thegostsniperfr.arffornia.client.screen.title;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;

/** Disk cache of the latest news headline, so the News tile has something to show offline. */
final class NewsStore {
    static final String RELATIVE_PATH = "arffornia/news.json";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private final Path file;

    NewsStore(Path gameDir) {
        this.file = gameDir.resolve(RELATIVE_PATH);
    }

    Optional<ArfforniaApiDtos.NewsHeadline> read() {
        try {
            return Optional.ofNullable(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ArfforniaApiDtos.NewsHeadline.class))
                    .filter(NewsStore::isValid);
        } catch (IOException | JsonParseException e) {
            return Optional.empty();
        }
    }

    void write(ArfforniaApiDtos.NewsHeadline headline) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(headline), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Cannot cache the news headline in {}: {}", file, e.toString());
        }
    }

    /** The URL is opened in the player's browser: only http(s) links with a title are kept. */
    static boolean isValid(ArfforniaApiDtos.NewsHeadline headline) {
        if (headline.title() == null || headline.title().isBlank() || headline.url() == null) return false;
        try {
            String scheme = new URI(headline.url()).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
