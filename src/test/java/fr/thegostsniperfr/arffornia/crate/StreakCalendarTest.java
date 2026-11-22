package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.JsonParser;
import fr.thegostsniperfr.arffornia.shop.RewardEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreakCalendarTest {
    private static final String WEEK = "[" + String.join(",", java.util.stream.IntStream.rangeClosed(8, 14)
            .mapToObj(day -> "{\"day\":" + day + ",\"contents\":[{\"type\":\"key\",\"crate\":\"arffornia:streak\",\"count\":1}]}").toList()) + "]";

    private static StreakCalendar parse(String json) {
        return StreakCalendar.parse(JsonParser.parseString(json));
    }

    @Test
    void readsTheWebsiteShape() {
        StreakCalendar calendar = parse("{\"day_streak\":10,\"best_streak\":21,\"last_streak_date\":\"2026-11-22\",\"today_claimed\":true,\"week\":" + WEEK + "}");

        assertEquals(List.of(10, 21, 7), List.of(calendar.dayStreak(), calendar.bestStreak(), calendar.week().size()));
        assertEquals(List.of(new RewardEntry.Key("arffornia:streak", 1)), calendar.week().getFirst().contents());
    }

    @Test
    void todayIsTheNextDayUntilTodaysConnectionIsRecorded() {
        StreakCalendar claimed = parse("{\"day_streak\":10,\"best_streak\":10,\"today_claimed\":true,\"week\":" + WEEK + "}");
        StreakCalendar missed = parse("{\"day_streak\":0,\"best_streak\":10,\"today_claimed\":false,\"week\":[]}");

        assertEquals(List.of(10, 1), List.of(claimed.today(), missed.today()));
    }

    @Test
    void aMalformedAnswerIsNoCalendar() {
        assertNull(parse("{\"day_streak\":\"x\"}"));
    }
}
