package fr.thegostsniperfr.arffornia.i18n;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lang lint, plain file parsing (no game boot). Guarantees that:
 * <ul>
 *   <li>en_us.json and fr_fr.json have exactly the same keys, no duplicates and no empty values;</li>
 *   <li>each key has the same number of {@code %s}/{@code %n$s} arguments in both languages;</li>
 *   <li>every key referenced from the Java sources ({@code translatable("...")}, {@code translatableWithFallback("...")},
 *       key-mapping names and other lang-key string constants) exists in both files;</li>
 *   <li>registered blocks and items have a name;</li>
 *   <li>no {@code §} formatting codes and no user-facing {@code Component.literal("words")} come back.</li>
 * </ul>
 */
class LangFilesTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/arffornia/lang");
    private static final Path JAVA_DIR = Path.of("src/main/java");
    private static final List<String> LOCALES = List.of("en_us", "fr_fr");

    /** Strings passed to Component.translatable / translatableWithFallback. */
    private static final Pattern TRANSLATABLE_CALL = Pattern.compile("translatable(?:WithFallback)?\\(\\s*\"([^\"]+)\"");
    /** Any string literal that looks like one of our lang keys (constants, KeyMapping names...). */
    private static final Pattern LANG_KEY_LITERAL = Pattern.compile(
            "\"((?:gui|hud|chat|commands|key|item|block|creativetab)\\.arffornia[a-z0-9_.]*|key\\.category\\.arffornia|creativetab\\.arffornia_tab)\"");
    private static final Pattern BLOCK_REGISTRATION = Pattern.compile("registerBlock\\(\\s*\"([a-z0-9_]+)\"");
    private static final Pattern ITEM_REGISTRATION = Pattern.compile("ITEMS\\.register\\(\\s*\"([a-z0-9_]+)\"");
    /** Component.literal("...") with at least one letter: user-facing text that must be translatable. */
    private static final Pattern WORDY_LITERAL = Pattern.compile("Component\\.literal\\(\\s*\"([^\"]*[A-Za-z][^\"]*)\"");
    /** Literals allowed because they are not language content (commands typed as-is). */
    private static final Set<String> ALLOWED_LITERALS = Set.of("/arffornia shop claim_reward");
    private static final Pattern FORMAT_ARG = Pattern.compile("%(?:\\d+\\$)?s");
    /** An item name read from API data; it must be shown through ItemLabels.of(itemId, displayName). */
    private static final Pattern API_ITEM_NAME = Pattern.compile("\\b\\w+\\.displayName\\(\\)");

    private static final Map<String, Map<String, String>> LANGS = new LinkedHashMap<>();
    private static final Map<Path, String> SOURCES = new LinkedHashMap<>();

    @BeforeAll
    static void load() throws IOException {
        for (String locale : LOCALES) {
            LANGS.put(locale, readLang(LANG_DIR.resolve(locale + ".json")));
        }
        try (Stream<Path> files = Files.walk(JAVA_DIR)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                SOURCES.put(file, Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        assertFalse(SOURCES.isEmpty(), "no Java sources found under " + JAVA_DIR.toAbsolutePath());
    }

    /** Reads a flat lang file and fails on duplicate keys (a plain Map parse would hide them). */
    private static Map<String, String> readLang(Path file) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8); JsonReader json = new JsonReader(reader)) {
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                assertEquals(JsonToken.STRING, json.peek(), file + ": value of " + key + " is not a string");
                String previous = entries.put(key, json.nextString());
                assertTrue(previous == null, file + ": duplicate key " + key);
            }
            json.endObject();
        }
        return entries;
    }

    private static Set<String> matches(Pattern pattern) {
        Set<String> found = new TreeSet<>();
        for (String source : SOURCES.values()) {
            Matcher m = pattern.matcher(source);
            while (m.find()) {
                found.add(m.group(1));
            }
        }
        return found;
    }

    private static int argCount(String value) {
        Matcher m = FORMAT_ARG.matcher(value);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    @Test
    void localesHaveTheSameKeys() {
        Set<String> en = new TreeSet<>(LANGS.get("en_us").keySet());
        for (String locale : LOCALES) {
            Set<String> other = new TreeSet<>(LANGS.get(locale).keySet());
            Set<String> missing = new TreeSet<>(en);
            missing.removeAll(other);
            Set<String> extra = new TreeSet<>(other);
            extra.removeAll(en);
            assertTrue(missing.isEmpty(), locale + " is missing keys: " + missing);
            assertTrue(extra.isEmpty(), locale + " has keys not in en_us: " + extra);
        }
    }

    @Test
    void valuesAreNonEmptyAndHaveMatchingArguments() {
        Map<String, String> en = LANGS.get("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            LANGS.get(locale).forEach((key, value) -> {
                if (value.isBlank()) problems.add(locale + ":" + key + " is empty");
                if (value.indexOf('§') >= 0) problems.add(locale + ":" + key + " uses a section-sign colour code");
                if (en.containsKey(key) && argCount(value) != argCount(en.get(key))) {
                    problems.add(locale + ":" + key + " has " + argCount(value) + " arguments, en_us has " + argCount(en.get(key)));
                }
            });
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyReferencedKeyExists() {
        Set<String> referenced = new TreeSet<>(matches(TRANSLATABLE_CALL));
        referenced.addAll(matches(LANG_KEY_LITERAL));
        matches(BLOCK_REGISTRATION).forEach(id -> referenced.add("block.arffornia." + id));
        matches(ITEM_REGISTRATION).forEach(id -> referenced.add("item.arffornia." + id));
        // Keys built from a prefix constant (e.g. "gui.arffornia.recipe_type.") are dynamic and have a fallback.
        referenced.removeIf(key -> key.endsWith("."));

        assertTrue(referenced.size() > 50, "suspiciously few keys found: " + referenced);
        for (String locale : LOCALES) {
            Set<String> missing = new TreeSet<>(referenced);
            missing.removeAll(LANGS.get(locale).keySet());
            assertTrue(missing.isEmpty(), locale + ".json is missing keys used in the code: " + missing);
        }
    }

    @Test
    void noHardcodedUserFacingText() {
        List<String> problems = new ArrayList<>();
        SOURCES.forEach((file, source) -> {
            if (source.indexOf('§') >= 0) {
                problems.add(file + ": section-sign colour code, use ChatFormatting or Style");
            }
            Matcher m = WORDY_LITERAL.matcher(source);
            while (m.find()) {
                if (!ALLOWED_LITERALS.contains(m.group(1))) {
                    problems.add(file + ": Component.literal(\"" + m.group(1) + "\"), use Component.translatable");
                }
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void modDescriptionIsTranslated() {
        for (String locale : LOCALES) {
            assertTrue(LANGS.get(locale).containsKey("fml.menu.mods.info.description.arffornia"),
                    locale + ".json: missing the Mods-list description (fml.menu.mods.info.description.arffornia)");
        }
    }

    /**
     * API item display names are English text generated on the server. Screens show the game's own,
     * translated item name and use the API value only as a fallback (ItemLabels.of).
     */
    @Test
    void apiItemNamesAreShownThroughItemLabels() {
        List<String> problems = new ArrayList<>();
        SOURCES.forEach((file, source) -> {
            if (!file.toString().replace('\\', '/').contains("/client/")) {
                return;
            }
            String[] lines = source.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = API_ITEM_NAME.matcher(lines[i]);
                while (m.find()) {
                    if (!lines[i].contains("ItemLabels.of(")) {
                        problems.add(file + ":" + (i + 1) + ": " + m.group() + " shown raw, use ItemLabels.of(itemId, displayName)");
                    }
                }
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
