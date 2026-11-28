package fr.thegostsniperfr.arffornia.metrics;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Serves {@link ArfforniaMetrics} on {@code /metrics}. Meant for a cluster-internal port, never exposed to players. */
public final class MetricsServer implements AutoCloseable {
    private final HttpServer server;

    private MetricsServer(HttpServer server) {
        this.server = server;
    }

    public static MetricsServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", exchange -> {
            byte[] body = ArfforniaMetrics.render().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders("GET".equals(exchange.getRequestMethod()) ? 200 : 405, "GET".equals(exchange.getRequestMethod()) ? body.length : -1);
            if ("GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "arffornia-metrics");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return new MetricsServer(server);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
