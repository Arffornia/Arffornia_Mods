package fr.thegostsniperfr.arffornia.recipe.pack;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.config.ApiConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads the recipe datapack from the website, keeps it only when it is valid, then reloads the server data so
 * every connected client and JEI get the new recipes. Any failure keeps the last valid pack, so recipe bans stay
 * applied when the website is down.
 */
public final class RecipePackUpdater {
    public static final String API_PATH = "/progression/datapack";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile Optional<String> loadedSha = Optional.empty();

    private static boolean probeArmed;
    private static long lastTickStart;
    private static long longestTickGap;
    private static CompletableFuture<Long> probeResult;

    public enum Status { UPDATED, UNCHANGED, BUSY, FAILED }

    /**
     * @param reloadMillis    wall time of the data reload, from request to the new data being live
     * @param longestTickGapMillis longest time between two server ticks during the reload, the freeze players feel
     */
    public record Outcome(Status status, String detail, int recipeFiles, long reloadMillis, long longestTickGapMillis) {
        static Outcome of(Status status, String detail) {
            return new Outcome(status, detail, 0, 0, 0);
        }
    }

    private record Download(Status status, String detail, byte[] body, RecipePackValidator.Summary summary) {
    }

    private RecipePackUpdater() {
    }

    public static URI uri() {
        return URI.create(ApiConfig.API_BASE_URL.get() + API_PATH);
    }

    public static CompletableFuture<Outcome> update(MinecraftServer server) {
        if (!RUNNING.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(Outcome.of(Status.BUSY, "an update is already running"));
        }
        Path target = RecipePack.file();
        return download(uri(), loadedSha)
                .thenApply(download -> store(download, target))
                .thenComposeAsync(download -> download.status() == Status.UPDATED
                        ? reload(server).thenApply(timing -> {
                            loadedSha = Optional.of(download.summary().sha256());
                            return new Outcome(Status.UPDATED, download.summary().sha256(),
                                    download.summary().recipeFiles(), timing[0], timing[1]);
                        })
                        : CompletableFuture.completedFuture(Outcome.of(download.status(), download.detail())), server)
                .exceptionally(error -> Outcome.of(Status.FAILED, rootCause(error).toString()))
                .whenComplete((outcome, error) -> RUNNING.set(false));
    }

    /** Records the pack the server loaded at start, so later updates compare against what is live. */
    public static void rememberLoadedPack() {
        loadedSha = localSha(RecipePack.file());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNNING.set(false);
        probeArmed = false;
        probeResult = null;
        loadedSha = Optional.empty();
    }

    private static Optional<String> localSha(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.of(RecipePackValidator.sha256(Files.readAllBytes(file))) : Optional.empty();
        } catch (IOException e) {
            Arffornia.LOGGER.warn("Cannot read the current recipe datapack {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static CompletableFuture<Download> download(URI uri, Optional<String> localSha) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("Accept", "application/zip").GET();
        localSha.ifPresent(sha -> request.header("If-None-Match", "\"" + sha + "\""));
        return CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            if (response.statusCode() == 304) {
                closeQuietly(response.body());
                return new Download(Status.UNCHANGED, "not modified", null, null);
            }
            if (response.statusCode() != 200) {
                closeQuietly(response.body());
                return new Download(Status.FAILED, "HTTP " + response.statusCode() + " from " + uri, null, null);
            }
            byte[] body;
            try (InputStream in = response.body()) {
                body = in.readNBytes(RecipePackValidator.MAX_BYTES + 1);
            } catch (IOException e) {
                return new Download(Status.FAILED, "download interrupted: " + e, null, null);
            }
            RecipePackValidator.Summary summary;
            try {
                summary = RecipePackValidator.validate(body);
            } catch (RecipePackValidator.InvalidPackException e) {
                return new Download(Status.FAILED, "invalid datapack: " + e.getMessage(), null, null);
            }
            Optional<String> announced = response.headers().firstValue("ETag").map(RecipePackUpdater::etagSha);
            if (announced.isPresent() && !announced.get().isEmpty() && !announced.get().equals(summary.sha256())) {
                return new Download(Status.FAILED, "checksum mismatch: ETag " + announced.get() + ", body " + summary.sha256(), null, null);
            }
            if (localSha.isPresent() && localSha.get().equals(summary.sha256())) {
                return new Download(Status.UNCHANGED, "same content", null, null);
            }
            return new Download(Status.UPDATED, null, body, summary);
        });
    }

    private static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException e) {
            Arffornia.LOGGER.debug("Closing the datapack response failed: {}", e.toString());
        }
    }

    static String etagSha(String etag) {
        String value = etag.startsWith("W/") ? etag.substring(2) : etag;
        value = value.replace("\"", "").toLowerCase(Locale.ROOT);
        return value.matches("[0-9a-f]{64}") ? value : "";
    }

    private static Download store(Download download, Path target) {
        if (download.status() != Status.UPDATED) {
            return download;
        }
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "recipe-pack", ".zip.part");
            try {
                Files.write(temp, download.body());
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            return download;
        } catch (IOException e) {
            return new Download(Status.FAILED, "cannot write " + target + ": " + e.getMessage(), null, null);
        }
    }

    /** Must run on the server thread. Completes with {reload millis, longest tick gap millis}. */
    private static CompletableFuture<long[]> reload(MinecraftServer server) {
        PackRepository repository = server.getPackRepository();
        repository.reload();
        Collection<String> selected = new ArrayList<>(repository.getSelectedIds());
        long start = System.nanoTime();
        probeArmed = true;
        lastTickStart = start;
        longestTickGap = 0;
        return server.reloadResources(selected)
                .thenComposeAsync(ignored -> {
                    long reloadMillis = (System.nanoTime() - start) / 1_000_000;
                    probeResult = new CompletableFuture<>();
                    return probeResult.thenApply(gap -> new long[]{reloadMillis, gap / 1_000_000});
                }, server)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        probeArmed = false;
                        probeResult = null;
                    }
                });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        if (!probeArmed) {
            return;
        }
        long now = System.nanoTime();
        longestTickGap = Math.max(longestTickGap, now - lastTickStart);
        lastTickStart = now;
        if (probeResult != null) {
            CompletableFuture<Long> result = probeResult;
            probeArmed = false;
            probeResult = null;
            result.complete(longestTickGap);
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        return cause;
    }
}
