package fr.thegostsniperfr.arffornia.shop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.thegostsniperfr.arffornia.api.service.ApiHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRewardsClientTest {
    private static final UUID PLAYER = UUID.fromString("0123abcd-0000-4000-8000-00000000beef");
    private static final String COMPACT = "0123abcd000040008000" + "00000000beef";

    private HttpServer server;
    private HttpRewardsClient client;
    private final List<String> claimKeys = new CopyOnWriteArrayList<>();
    private final List<String> claimBodies = new CopyOnWriteArrayList<>();
    private final List<String> pendingQueries = new CopyOnWriteArrayList<>();
    private final AtomicInteger claimCalls = new AtomicInteger();
    private volatile int pendingStatus = 200;
    private volatile String pendingBody = "{\"rewards\":[]}";
    private volatile int[] claimStatuses = {200};

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/auth/token/svc", ex -> respond(ex, 200, "{\"token\":\"t\"}"));
        server.createContext("/api/rewards/pending", ex -> {
            pendingQueries.add(ex.getRequestURI().getQuery());
            respond(ex, pendingStatus, pendingBody);
        });
        server.createContext("/api/rewards/42/claim", ex -> {
            int n = claimCalls.getAndIncrement();
            claimKeys.add(ex.getRequestHeaders().getFirst("Idempotency-Key"));
            claimBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = claimStatuses[Math.min(n, claimStatuses.length - 1)];
            respond(ex, status, status == 200
                    ? "{\"status\":\"claimed\",\"reward\":{\"id\":42,\"source\":\"vote\",\"contents\":[{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1}]}}"
                    : "{\"message\":\"x\"}");
        });
        server.createContext("/api/rewards/day-streak", ex -> respond(ex, 200, "{\"day_streak\":8,\"is_first_connection_today\":true,\"reward_id\":3}"));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        ApiHttp http = new ApiHttp(NOPLogger.NOP_LOGGER, () -> baseUrl, () -> "id", () -> "secret");
        client = new HttpRewardsClient(http, NOPLogger.NOP_LOGGER);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void pendingRewardsAreParsedWithTypedEntries() throws Exception {
        pendingBody = "{\"rewards\":[{\"id\":7,\"source\":\"streak\",\"contents\":[{\"type\":\"lucky_box\",\"box\":\"arffornia:streak\",\"count\":2},{\"type\":\"pet\"}]}]}";

        RewardsClient.PendingResult result = client.fetchPending(PLAYER).get(5, TimeUnit.SECONDS);

        RewardsClient.PendingResult.Found found = assertInstanceOf(RewardsClient.PendingResult.Found.class, result);
        assertEquals(new PendingReward(7, "streak", List.of(new RewardEntry.LuckyBox("arffornia:streak", 2), new RewardEntry.Unsupported("pet", "unknown type"))), found.rewards().getFirst());
        assertEquals("player_uuid=" + COMPACT, pendingQueries.getFirst());
    }

    @Test
    void unknownPlayerIsNotLinked() throws Exception {
        pendingStatus = 404;

        assertInstanceOf(RewardsClient.PendingResult.NotLinked.class, client.fetchPending(PLAYER).get(5, TimeUnit.SECONDS));
    }

    @Test
    void malformedPendingBodyIsAFailure() throws Exception {
        pendingBody = "{\"nope\":1}";

        assertInstanceOf(RewardsClient.PendingResult.Failed.class, client.fetchPending(PLAYER).get(5, TimeUnit.SECONDS));
    }

    @Test
    void claimSendsTheKeyAndPlayerAndReturnsTheClaimedContents() throws Exception {
        RewardsClient.ClaimResult result = client.claim(42, PLAYER, "key-0123456789abcdef").get(5, TimeUnit.SECONDS);

        RewardsClient.ClaimResult.Claimed claimed = assertInstanceOf(RewardsClient.ClaimResult.Claimed.class, result);
        assertEquals(List.of(new RewardEntry.Item("minecraft:diamond", 1)), claimed.reward().contents());
        assertEquals(List.of("key-0123456789abcdef"), claimKeys);
        assertTrue(claimBodies.getFirst().contains(COMPACT));
    }

    @Test
    void claimRetriesOnceWithTheSameKeyAfterAServerError() throws Exception {
        claimStatuses = new int[]{503, 200};

        RewardsClient.ClaimResult result = client.claim(42, PLAYER, "key-0123456789abcdef").get(5, TimeUnit.SECONDS);

        assertInstanceOf(RewardsClient.ClaimResult.Claimed.class, result);
        assertEquals(List.of("key-0123456789abcdef", "key-0123456789abcdef"), claimKeys);
    }

    @Test
    void conflictIsRejectedWithoutRetry() throws Exception {
        claimStatuses = new int[]{409};

        RewardsClient.ClaimResult result = client.claim(42, PLAYER, "key-0123456789abcdef").get(5, TimeUnit.SECONDS);

        assertEquals(new RewardsClient.ClaimResult.Rejected(409), result);
        assertEquals(1, claimCalls.get());
    }

    @Test
    void persistentServerErrorIsAFailureAfterOneRetry() throws Exception {
        claimStatuses = new int[]{500};

        RewardsClient.ClaimResult result = client.claim(42, PLAYER, "key-0123456789abcdef").get(5, TimeUnit.SECONDS);

        assertInstanceOf(RewardsClient.ClaimResult.Failed.class, result);
        assertEquals(2, claimCalls.get());
    }

    @Test
    void dailyConnectionReturnsTheStreak() throws Exception {
        assertEquals(new RewardsClient.StreakResult(8, true), client.recordDailyConnection(PLAYER).get(5, TimeUnit.SECONDS));
    }
}
