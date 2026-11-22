package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chance of each item of a loot table, computed from its JSON weights (spec 020 D4). Per pool, an entry's chance in
 * one roll is its weight over the pool's total weight, {@code empty} entries included; over {@code n} rolls it is
 * {@code 1 - (1 - p)^n}, averaged over uniform roll counts. Pools combine as independent draws. Conditions, luck
 * ({@code quality}, {@code bonus_rolls}) and nested tables are not modelled: their entries count in the total weight
 * but are not listed.
 */
public final class CrateOdds {

    /** {@code chance} is the probability, 0 to 1, that one opening gives this item at least once. */
    public record Odds(String item, double chance, int minCount, int maxCount) {
    }

    private CrateOdds() {
    }

    public static List<Odds> compute(JsonObject lootTable) {
        Map<String, double[]> missProbability = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        JsonArray pools = lootTable.has("pools") ? lootTable.getAsJsonArray("pools") : new JsonArray();
        for (JsonElement poolElement : pools) {
            JsonObject pool = poolElement.getAsJsonObject();
            int[] rolls = range(pool.get("rolls"), 1);
            JsonArray entries = pool.has("entries") ? pool.getAsJsonArray("entries") : new JsonArray();
            double total = 0;
            for (JsonElement entry : entries) {
                total += weight(entry.getAsJsonObject());
            }
            if (total <= 0) {
                continue;
            }
            for (JsonElement entryElement : entries) {
                JsonObject entry = entryElement.getAsJsonObject();
                if (!"minecraft:item".equals(type(entry)) || !entry.has("name")) {
                    continue;
                }
                String item = entry.get("name").getAsString();
                double p = weight(entry) / total;
                double miss = 0;
                for (int n = rolls[0]; n <= rolls[1]; n++) {
                    miss += Math.pow(1 - p, n);
                }
                miss /= rolls[1] - rolls[0] + 1;
                missProbability.computeIfAbsent(item, k -> new double[]{1})[0] *= miss;
                int[] count = itemCount(entry);
                counts.merge(item, count, (a, b) -> new int[]{Math.min(a[0], b[0]), Math.max(a[1], b[1])});
            }
        }
        List<Odds> odds = new ArrayList<>();
        missProbability.forEach((item, miss) -> odds.add(new Odds(item, 1 - miss[0], counts.get(item)[0], counts.get(item)[1])));
        odds.sort((a, b) -> Double.compare(b.chance(), a.chance()));
        return List.copyOf(odds);
    }

    private static String type(JsonObject entry) {
        String type = entry.has("type") ? entry.get("type").getAsString() : "";
        return type.contains(":") ? type : "minecraft:" + type;
    }

    private static double weight(JsonObject entry) {
        return entry.has("weight") ? Math.max(0, entry.get("weight").getAsDouble()) : 1;
    }

    private static int[] itemCount(JsonObject entry) {
        int[] count = {1, 1};
        if (entry.has("functions")) {
            for (JsonElement function : entry.getAsJsonArray("functions")) {
                JsonObject f = function.getAsJsonObject();
                String name = f.has("function") ? f.get("function").getAsString() : "";
                if ((name.equals("minecraft:set_count") || name.equals("set_count")) && !f.has("conditions") && !f.has("add")) {
                    count = range(f.get("count"), 1);
                }
            }
        }
        return count;
    }

    /** A number provider as an integer range: a constant, {@code uniform}, {@code constant} or a bare {@code min}/{@code max}. */
    private static int[] range(JsonElement provider, int fallback) {
        if (provider == null) {
            return new int[]{fallback, fallback};
        }
        if (provider.isJsonPrimitive()) {
            int value = (int) Math.floor(provider.getAsDouble());
            return new int[]{value, value};
        }
        JsonObject object = provider.getAsJsonObject();
        if (object.has("value")) {
            int value = (int) Math.floor(object.get("value").getAsDouble());
            return new int[]{value, value};
        }
        if (object.has("min") && object.has("max")) {
            int min = (int) Math.floor(object.get("min").getAsDouble());
            int max = (int) Math.floor(object.get("max").getAsDouble());
            return new int[]{Math.min(min, max), Math.max(min, max)};
        }
        return new int[]{fallback, fallback};
    }
}
