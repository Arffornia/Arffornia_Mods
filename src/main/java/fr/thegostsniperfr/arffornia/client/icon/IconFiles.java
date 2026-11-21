package fr.thegostsniperfr.arffornia.client.icon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * File layout of an item icon export: {@code <size>/<namespace>/<path>.png} per size and one
 * {@code manifest.json} mapping each exported item id to a short content hash, used by clients
 * as a cache-busting version ({@code ?v=<hash>}).
 */
public final class IconFiles {

    public static final int MANIFEST_FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private IconFiles() {
    }

    /**
     * Relative file of one icon, or empty when the id could escape the export folder. Registry paths
     * allow {@code .} and {@code /}, so a {@code ..} segment is possible in theory.
     */
    public static Optional<String> relativePath(String namespace, String path, int size) {
        for (String segment : (namespace + "/" + path).split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return Optional.empty();
            }
        }
        return Optional.of(size + "/" + namespace + "/" + path + ".png");
    }

    public static List<Integer> parseSizes(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .peek(size -> {
                    if (size < 16 || size > 512) {
                        throw new IllegalArgumentException("Icon size out of 16..512: " + size);
                    }
                })
                .sorted()
                .toList();
    }

    public static String shortHash(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }

    public static void writeManifest(Path dir, List<Integer> sizes, Map<String, String> hashes,
                                     List<String> failed, Instant generatedAt) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", MANIFEST_FORMAT);
        root.addProperty("generated_at", generatedAt.toString());
        root.add("sizes", GSON.toJsonTree(sizes));
        root.addProperty("count", hashes.size());
        SortedMap<String, String> sorted = new TreeMap<>(hashes);
        root.add("items", GSON.toJsonTree(sorted));
        root.add("failed", GSON.toJsonTree(failed.stream().sorted().toList()));
        try (Writer out = Files.newBufferedWriter(dir.resolve("manifest.json"), StandardCharsets.UTF_8)) {
            GSON.toJson(root, out);
        }
    }
}
