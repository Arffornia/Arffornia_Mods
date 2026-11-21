package fr.thegostsniperfr.arffornia.recipe.pack;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecipePackBootFetchTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger delayMillis = new AtomicInteger(0);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/progression/datapack", exchange -> {
            try {
                Thread.sleep(delayMillis.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = status.get() == 200 ? body.get() : new byte[0];
            exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + RecipePackUpdater.API_PATH);
    }

    private static byte[] pack(String recipeName) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{\"pack\":{\"pack_format\":48,\"description\":\"Arffornia\"}}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("data/arffornia/recipe/" + recipeName + ".json"));
            out.write("{\"type\":\"arffornia:machine\",\"machine\":\"arffornia:crafter\"}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void fetchInto_writesThePack_whenNoPackIsCached() throws Exception {
        Path target = dir.resolve("recipe-pack.zip");
        body.set(pack("iron_plate"));

        RecipePackUpdater.Outcome outcome = RecipePackUpdater.fetchInto(uri(), target, TIMEOUT);

        assertEquals(RecipePackUpdater.Status.UPDATED, outcome.status());
        assertArrayEquals(body.get(), Files.readAllBytes(target));
    }

    @Test
    void fetchInto_reportsUnchanged_whenTheCachedPackHasTheSameContent() throws Exception {
        Path target = dir.resolve("recipe-pack.zip");
        body.set(pack("iron_plate"));
        Files.write(target, body.get());

        RecipePackUpdater.Outcome outcome = RecipePackUpdater.fetchInto(uri(), target, TIMEOUT);

        assertEquals(RecipePackUpdater.Status.UNCHANGED, outcome.status());
    }

    @Test
    void fetchInto_keepsTheCachedPack_whenTheWebsiteFails() throws Exception {
        Path target = dir.resolve("recipe-pack.zip");
        byte[] cached = pack("iron_plate");
        Files.write(target, cached);
        status.set(503);

        RecipePackUpdater.Outcome outcome = RecipePackUpdater.fetchInto(uri(), target, TIMEOUT);

        assertEquals(RecipePackUpdater.Status.FAILED, outcome.status());
        assertArrayEquals(cached, Files.readAllBytes(target));
    }

    @Test
    void fetchInto_givesUpAndNeverWritesLater_whenTheWebsiteIsTooSlow() throws Exception {
        Path target = dir.resolve("recipe-pack.zip");
        body.set(pack("iron_plate"));
        delayMillis.set(3_000);

        RecipePackUpdater.Outcome outcome = RecipePackUpdater.fetchInto(uri(), target, Duration.ofMillis(500));
        Thread.sleep(3_500);

        assertEquals(RecipePackUpdater.Status.FAILED, outcome.status());
        assertFalse(Files.exists(target));
    }
}
