package fr.thegostsniperfr.arffornia.metrics;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.config.MetricsConfig;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventOutbox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;

@EventBusSubscriber(modid = Arffornia.MODID)
public final class MetricsLifecycle {
    private static MetricsServer server;

    private MetricsLifecycle() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer() || !MetricsConfig.ENABLED.get()) {
            return;
        }
        ArfforniaMetrics.gauge(ArfforniaMetrics.OUTBOX_PENDING, () -> {
            ProgressionEventOutbox outbox = ProgressionEventOutbox.get(event.getServer());
            return outbox.progressionIds().stream().mapToInt(id -> outbox.pendingKeys(id).size()).sum();
        });
        try {
            server = MetricsServer.start(MetricsConfig.PORT.get());
            Arffornia.LOGGER.info("Metrics served on port {}", server.port());
        } catch (IOException e) {
            Arffornia.LOGGER.error("Could not start the metrics endpoint on port {}: {}", MetricsConfig.PORT.get(), e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (server != null) {
            server.close();
            server = null;
        }
        ArfforniaMetrics.removeGauge(ArfforniaMetrics.OUTBOX_PENDING);
    }
}
