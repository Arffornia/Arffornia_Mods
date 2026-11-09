package fr.thegostsniperfr.arffornia.recipe.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Structural checks on a downloaded recipe datapack, so a truncated or broken download never replaces the last good
 * copy. Recipe content is checked by Minecraft at reload; a recipe it cannot parse is skipped and logged.
 */
public final class RecipePackValidator {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final int MAX_ENTRIES = 20_000;
    public static final int MAX_ENTRY_BYTES = 1024 * 1024;
    private static final Pattern RECIPE_FILE = Pattern.compile("data/[a-z0-9_.-]+/recipe/[a-z0-9_./-]+\\.json");

    public record Summary(String sha256, int recipeFiles) {
    }

    public static final class InvalidPackException extends Exception {
        public InvalidPackException(String message) {
            super(message);
        }
    }

    private RecipePackValidator() {
    }

    /** Validates through the zip central directory, the same view Minecraft's pack reader uses. */
    public static Summary validate(byte[] zip) throws InvalidPackException {
        if (zip.length == 0 || zip.length > MAX_BYTES) {
            throw new InvalidPackException("size " + zip.length + " bytes is outside 1.." + MAX_BYTES);
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("arffornia-recipe-pack", ".zip");
            Files.write(temp, zip);
            return new Summary(sha256(zip), checkEntries(temp));
        } catch (IOException e) {
            throw new InvalidPackException("not a readable zip: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // a leftover temp file is harmless
                }
            }
        }
    }

    private static int checkEntries(Path file) throws IOException, InvalidPackException {
        boolean hasMeta = false;
        int recipeFiles = 0;
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            if (zip.size() > MAX_ENTRIES) {
                throw new InvalidPackException("more than " + MAX_ENTRIES + " entries");
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..") || name.contains("\\") || name.contains("//")) {
                    throw new InvalidPackException("illegal entry name " + name);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (!name.equals("pack.mcmeta") && !RECIPE_FILE.matcher(name).matches()) {
                    throw new InvalidPackException("only pack.mcmeta and data/<namespace>/recipe/**.json are allowed, got " + name);
                }
                if (entry.getSize() > MAX_ENTRY_BYTES) {
                    throw new InvalidPackException(name + " is larger than " + MAX_ENTRY_BYTES + " bytes");
                }
                JsonElement json;
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] content = in.readNBytes(MAX_ENTRY_BYTES + 1);
                    if (content.length > MAX_ENTRY_BYTES) {
                        throw new InvalidPackException(name + " is larger than " + MAX_ENTRY_BYTES + " bytes");
                    }
                    json = parseJson(new String(content, StandardCharsets.UTF_8), name);
                }
                if (name.equals("pack.mcmeta")) {
                    checkPackMeta(json, name);
                    hasMeta = true;
                } else if (!json.isJsonObject()) {
                    throw new InvalidPackException(name + " is not a JSON object");
                } else {
                    recipeFiles++;
                }
            }
        }
        if (!hasMeta) {
            throw new InvalidPackException("pack.mcmeta is missing");
        }
        return recipeFiles;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    private static JsonElement parseJson(String content, String name) throws InvalidPackException {
        try {
            return JsonParser.parseString(content);
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
}
