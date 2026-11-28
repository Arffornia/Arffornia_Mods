package fr.thegostsniperfr.arffornia.metrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Counters and gauges of the Arffornia mod in the Prometheus text format. TPS, heap and chunks come from the
 * Minecraft Prometheus Exporter in the pack; this only holds what the exporter cannot know. No Minecraft dependency.
 */
public final class ArfforniaMetrics {
    public static final String REWARDS_DELIVERED = "arffornia_rewards_delivered_total";
    public static final String REWARD_CLAIMS = "arffornia_reward_claims_total";
    public static final String MILESTONES_COMPLETED = "arffornia_milestone_deliveries_total";
    public static final String API_ERRORS = "arffornia_api_errors_total";
    public static final String OUTBOX_PENDING = "arffornia_progression_outbox_pending";

    private static final Map<String, String> HELP = Map.of(
            REWARDS_DELIVERED, "Rewards given to a player after a successful claim, by source.",
            REWARD_CLAIMS, "Reward claim attempts by result: delivered, away, delivery_failed, rejected, failed.",
            MILESTONES_COMPLETED, "Milestones completed in game (Space Elevator deliveries and admin adds).",
            API_ERRORS, "Failed calls to the website API by kind: network, 4xx, 5xx.",
            OUTBOX_PENDING, "Milestone events waiting for the website to acknowledge them.");

    private static final Map<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<Number>> GAUGES = new ConcurrentHashMap<>();

    private ArfforniaMetrics() {
    }

    /** @param labels alternating label names and values, e.g. {@code "source", "vote"} */
    public static void increment(String name, String... labels) {
        COUNTERS.computeIfAbsent(sample(name, labels), key -> new LongAdder()).increment();
    }

    public static void gauge(String name, Supplier<Number> value) {
        GAUGES.put(name, value);
    }

    public static void removeGauge(String name) {
        GAUGES.remove(name);
    }

    public static void reset() {
        COUNTERS.clear();
        GAUGES.clear();
    }

    public static String render() {
        Map<String, Map<String, Number>> byFamily = new TreeMap<>();
        COUNTERS.forEach((sample, adder) -> byFamily.computeIfAbsent(family(sample), f -> new TreeMap<>()).put(sample, adder.sum()));
        GAUGES.forEach((name, supplier) -> byFamily.computeIfAbsent(name, f -> new TreeMap<>()).put(name, supplier.get()));
        StringBuilder out = new StringBuilder();
        byFamily.forEach((family, samples) -> {
            out.append("# HELP ").append(family).append(' ').append(HELP.getOrDefault(family, family)).append('\n');
            out.append("# TYPE ").append(family).append(GAUGES.containsKey(family) ? " gauge" : " counter").append('\n');
            samples.forEach((sample, value) -> out.append(sample).append(' ').append(value).append('\n'));
        });
        return out.toString();
    }

    private static String family(String sample) {
        int brace = sample.indexOf('{');
        return brace < 0 ? sample : sample.substring(0, brace);
    }

    private static String sample(String name, String[] labels) {
        if (labels.length == 0) {
            return name;
        }
        StringBuilder out = new StringBuilder(name).append('{');
        for (int i = 0; i + 1 < labels.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append(labels[i]).append("=\"").append(escape(labels[i + 1])).append('"');
        }
        return out.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
