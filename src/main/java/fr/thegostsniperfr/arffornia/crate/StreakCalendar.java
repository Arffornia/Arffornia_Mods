package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.thegostsniperfr.arffornia.shop.RewardEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The website's {@code GET /api/players/{uuid}/rewards/streak}: the current streak and the 7-day block (days 1-7,
 * 8-14...) holding the current day. A day is done when {@code day <= dayStreak}; {@code dayStreak} is 0 once a day was
 * missed.
 */
public record StreakCalendar(int dayStreak, int bestStreak, boolean todayClaimed, List<Day> week) {

    public record Day(int day, List<RewardEntry> contents) {
    }

    /** The streak day today is, or will be once today's connection is recorded. */
    public int today() {
        return todayClaimed ? Math.max(1, dayStreak) : dayStreak + 1;
    }

    @Nullable
    public static StreakCalendar parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        try {
            JsonObject json = element.getAsJsonObject();
            List<Day> week = new ArrayList<>();
            for (JsonElement dayElement : json.getAsJsonArray("week")) {
                JsonObject day = dayElement.getAsJsonObject();
                List<RewardEntry> contents = new ArrayList<>();
                if (day.has("contents") && day.get("contents").isJsonArray()) {
                    day.getAsJsonArray("contents").forEach(entry -> contents.add(RewardEntry.parse(entry)));
                }
                week.add(new Day(day.get("day").getAsInt(), List.copyOf(contents)));
            }
            return new StreakCalendar(json.get("day_streak").getAsInt(), json.get("best_streak").getAsInt(),
                    json.get("today_claimed").getAsBoolean(), List.copyOf(week));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
