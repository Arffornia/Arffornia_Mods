package fr.thegostsniperfr.arffornia.earlywindow;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The early window runs before Minecraft's language manager: it reads `lang:` from options.txt itself.
 * Strings are UTF-8 .properties because the SERVICE layer has no JSON library we can rely on.
 */
public final class EarlyStrings {
    static final String FALLBACK = "en_us";
    private final Map<String, String> strings;
    private final String language;

    private EarlyStrings(String language, Map<String, String> strings) {
        this.language = language;
        this.strings = strings;
    }

    public static EarlyStrings forGameDir(Path gameDir) {
        return forLanguage(readLanguage(gameDir.resolve("options.txt")));
    }

    public static EarlyStrings forLanguage(String language) {
        Map<String, String> merged = new HashMap<>(load(FALLBACK));
        Map<String, String> chosen = load(language);
        merged.putAll(chosen);
        return new EarlyStrings(chosen.isEmpty() ? FALLBACK : language, merged);
    }

    static String readLanguage(Path options) {
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("lang:")) {
                    return line.substring(5).trim().toLowerCase(Locale.ROOT);
                }
            }
        } catch (IOException ignored) {
            // First launch: Minecraft has not written options.txt yet.
        }
        return FALLBACK;
    }

    private static Map<String, String> load(String language) {
        if (!language.matches("[a-z]{2,3}_[a-z]{2,3}")) {
            return Map.of();
        }
        try (InputStream in = EarlyStrings.class.getResourceAsStream("/arffornia_earlywindow/lang/" + language + ".properties")) {
            if (in == null) {
                return Map.of();
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> out = new HashMap<>();
            props.forEach((k, v) -> out.put((String) k, (String) v));
            return out;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public String language() {
        return language;
    }

    public String get(String key, Object... args) {
        String pattern = strings.getOrDefault(key, key);
        return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
    }
}
