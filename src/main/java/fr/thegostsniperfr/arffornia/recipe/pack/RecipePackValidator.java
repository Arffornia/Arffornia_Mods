package fr.thegostsniperfr.arffornia.recipe.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Structural checks on a downloaded recipe datapack, so a truncated or broken download never replaces the last good
 * copy. Recipe content is checked by Minecraft at reload; a recipe it cannot parse is skipped and logged.
 */
public final class RecipePackValidator {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final int MAX_ENTRIES = 20_000;

    public record Summary(String sha256, int recipeFiles) {
    }

    public static final class InvalidPackException extends Exception {
        public InvalidPackException(String message) {
            super(message);
        }
    }

    private RecipePackValidator() {
    }

    public static Summary validate(byte[] zip) throws InvalidPackException {
        if (zip.length == 0 || zip.length > MAX_BYTES) {
            throw new InvalidPackException("size " + zip.length + " bytes is outside 1.." + MAX_BYTES);
        }
        boolean hasMeta = false;
        int recipeFiles = 0;
        int entries = 0;
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new InvalidPackException("more than " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..") || name.contains("\\")) {
                    throw new InvalidPackException("illegal entry name " + name);
                }
                if (!names.add(name)) {
                    throw new InvalidPackException("duplicate entry " + name);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.equals("pack.mcmeta")) {
                    checkPackMeta(parseJson(in, name), name);
                    hasMeta = true;
                } else if (name.endsWith(".json")) {
                    if (!name.startsWith("data/")) {
                        throw new InvalidPackException("JSON file outside data/: " + name);
                    }
                    if (!parseJson(in, name).isJsonObject()) {
                        throw new InvalidPackException(name + " is not a JSON object");
                    }
                    if (name.matches("data/[^/]+/recipe/.+")) {
                        recipeFiles++;
                    }
                }
            }
        } catch (IOException e) {
            throw new InvalidPackException("not a readable zip: " + e.getMessage());
        }
        if (!hasMeta) {
            throw new InvalidPackException("pack.mcmeta is missing");
        }
        return new Summary(sha256(zip), recipeFiles);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    private static JsonElement parseJson(ZipInputStream in, String name) throws InvalidPackException {
        try {
            return JsonParser.parseReader(new InputStreamReader(new NonClosingStream(in), StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            throw new InvalidPackException(name + " is not valid JSON: " + e.getMessage());
        }
    }

    private static void checkPackMeta(JsonElement meta, String name) throws InvalidPackException {
        if (meta.isJsonObject() && meta.getAsJsonObject().get("pack") instanceof JsonObject pack
                && pack.get("pack_format") != null && pack.get("pack_format").isJsonPrimitive()
                && pack.get("pack_format").getAsJsonPrimitive().isNumber()) {
            return;
        }
        throw new InvalidPackException(name + " has no pack.pack_format");
    }

    private static final class NonClosingStream extends FilterInputStream {
        NonClosingStream(ZipInputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }
}
