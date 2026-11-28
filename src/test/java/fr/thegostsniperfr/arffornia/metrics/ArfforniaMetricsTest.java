package fr.thegostsniperfr.arffornia.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArfforniaMetricsTest {

    @AfterEach
    void clean() {
        ArfforniaMetrics.reset();
    }

    @Test
    void countsEachLabelSetAsItsOwnSeries() {
        ArfforniaMetrics.increment(ArfforniaMetrics.REWARDS_DELIVERED, "source", "vote");
        ArfforniaMetrics.increment(ArfforniaMetrics.REWARDS_DELIVERED, "source", "vote");
        ArfforniaMetrics.increment(ArfforniaMetrics.REWARDS_DELIVERED, "source", "shop");

        String text = ArfforniaMetrics.render();

        assertTrue(text.contains("arffornia_rewards_delivered_total{source=\"vote\"} 2\n"));
        assertTrue(text.contains("arffornia_rewards_delivered_total{source=\"shop\"} 1\n"));
    }

    @Test
    void declaresTypeOncePerFamily() {
        ArfforniaMetrics.increment(ArfforniaMetrics.API_ERRORS, "kind", "5xx");
        ArfforniaMetrics.increment(ArfforniaMetrics.API_ERRORS, "kind", "network");

        String text = ArfforniaMetrics.render();

        assertTrue(text.contains("# TYPE arffornia_api_errors_total counter\n"));
        assertFalse(text.replaceFirst("# TYPE arffornia_api_errors_total counter\n", "").contains("# TYPE arffornia_api_errors_total"));
    }

    @Test
    void rendersACounterWithoutLabels() {
        ArfforniaMetrics.increment(ArfforniaMetrics.MILESTONES_COMPLETED);

        assertTrue(ArfforniaMetrics.render().contains("arffornia_milestone_deliveries_total 1\n"));
    }

    @Test
    void rendersGaugesFromTheirSupplierAtScrapeTime() {
        int[] pending = {3};
        ArfforniaMetrics.gauge(ArfforniaMetrics.OUTBOX_PENDING, () -> pending[0]);
        pending[0] = 7;

        String text = ArfforniaMetrics.render();

        assertTrue(text.contains("# TYPE arffornia_progression_outbox_pending gauge\n"));
        assertTrue(text.contains("arffornia_progression_outbox_pending 7\n"));
    }

    @Test
    void escapesLabelValues() {
        ArfforniaMetrics.increment(ArfforniaMetrics.REWARDS_DELIVERED, "source", "a\"b\\c");

        assertTrue(ArfforniaMetrics.render().contains("{source=\"a\\\"b\\\\c\"}"));
    }
}
