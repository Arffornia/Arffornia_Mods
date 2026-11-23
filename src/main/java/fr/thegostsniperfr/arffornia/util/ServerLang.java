package fr.thegostsniperfr.arffornia.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Server-sent translatable text with the en_us wording as fallback: a client whose mod version lacks the key
 * (rollouts, mismatched jars) shows English instead of the raw key.
 */
public final class ServerLang {
    private static final String EN_US = "/assets/arffornia/lang/en_us.json";
    private static final JsonObject ENGLISH = load();

    private ServerLang() {
    }

    public static MutableComponent translatable(String key, Object... args) {
        String fallback = ENGLISH.has(key) ? ENGLISH.get(key).getAsString() : key;
        return Component.translatableWithFallback(key, fallback, args);
    }

    private static JsonObject load() {
        try (Reader reader = new InputStreamReader(ServerLang.class.getResourceAsStream(EN_US), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read " + EN_US, e);
        }
    }
}
