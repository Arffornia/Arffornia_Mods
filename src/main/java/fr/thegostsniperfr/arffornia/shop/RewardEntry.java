package fr.thegostsniperfr.arffornia.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * One typed entry of a reward, as sent by the website ({@code App\Rewards\RewardCatalog}). The website validates
 * entries too; the checks here are repeated because the game server must not trust the API blindly.
 */
public sealed interface RewardEntry {
    Pattern RESOURCE_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    Pattern PERMISSION_NODE = Pattern.compile("^[a-z0-9_-]+(\\.[a-z0-9_-]+)*$");
    Pattern GROUP = Pattern.compile("^[a-z0-9_-]{1,36}$");
    int MAX_ITEM_COUNT = 2304;
    int MAX_BOX_COUNT = 64;

    record Item(String item, int count) implements RewardEntry {
    }

    record Permission(String node, boolean value) implements RewardEntry {
    }

    record Rank(String group) implements RewardEntry {
    }

    record LuckyBox(String box, int count) implements RewardEntry {
    }

    /** A type this mod version does not know (added later on the website), or an entry that failed validation. */
    record Unsupported(String type, String reason) implements RewardEntry {
    }

    static RewardEntry parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new Unsupported("?", "not an object");
        }
        JsonObject json = element.getAsJsonObject();
        String type = string(json, "type");
        if (type == null) {
            return new Unsupported("?", "missing type");
        }
        return switch (type) {
            case "item" -> {
                String item = string(json, "item");
                Integer count = integer(json, "count");
                yield matches(RESOURCE_ID, item) && inRange(count, MAX_ITEM_COUNT)
                        ? new Item(item, count)
                        : new Unsupported(type, "invalid item or count");
            }
            case "permission" -> {
                String node = string(json, "node");
                Boolean value = json.has("value") ? bool(json, "value") : Boolean.TRUE;
                yield matches(PERMISSION_NODE, node) && value != null
                        ? new Permission(node, value)
                        : new Unsupported(type, "invalid node");
            }
            case "rank" -> {
                String group = string(json, "group");
                yield matches(GROUP, group) ? new Rank(group) : new Unsupported(type, "invalid group");
            }
            case "lucky_box" -> {
                String box = string(json, "box");
                Integer count = integer(json, "count");
                yield matches(RESOURCE_ID, box) && inRange(count, MAX_BOX_COUNT)
                        ? new LuckyBox(box, count)
                        : new Unsupported(type, "invalid box or count");
            }
            default -> new Unsupported(type, "unknown type");
        };
    }

    private static boolean matches(Pattern pattern, @Nullable String value) {
        return value != null && value.length() <= 128 && pattern.matcher(value).matches();
    }

    private static boolean inRange(@Nullable Integer count, int max) {
        return count != null && count >= 1 && count <= max;
    }

    @Nullable
    private static String string(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    @Nullable
    private static Integer integer(JsonObject json, String key) {
        JsonElement e = json.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            return null;
        }
        double d = p.getAsDouble();
        return d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? (int) d : null;
    }

    @Nullable
    private static Boolean bool(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : null;
    }
}
