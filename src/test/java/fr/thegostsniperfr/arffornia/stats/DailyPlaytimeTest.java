package fr.thegostsniperfr.arffornia.stats;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyPlaytimeTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);

    private static Instant at(String time) {
        return Instant.parse(time);
    }

    @Test
    void addsTheSessionToTheRunningTotalOfTheSameDay() {
        DailyPlaytime current = new DailyPlaytime(DAY, 600);

        List<DailyPlaytime> days = DailyPlaytime.advance(current, at("2026-11-22T10:00:00Z"), at("2026-11-22T10:05:00Z"));

        assertEquals(List.of(new DailyPlaytime(DAY, 900)), days);
    }

    @Test
    void splitsASessionAtUtcMidnight() {
        List<DailyPlaytime> days = DailyPlaytime.advance(DailyPlaytime.NONE, at("2026-11-22T23:50:00Z"), at("2026-11-23T00:20:00Z"));

        assertEquals(List.of(new DailyPlaytime(DAY, 600), new DailyPlaytime(DAY.plusDays(1), 1200)), days);
    }

    @Test
    void reportsAnEarlierDayAgainBeforeStartingTheNewOne() {
        DailyPlaytime yesterday = new DailyPlaytime(DAY.minusDays(1), 3600);

        List<DailyPlaytime> days = DailyPlaytime.advance(yesterday, at("2026-11-22T08:00:00Z"), at("2026-11-22T08:00:00Z"));

        assertEquals(List.of(yesterday, new DailyPlaytime(DAY, 0)), days);
    }

    @Test
    void neverExceedsADayOrGoesBackwards() {
        DailyPlaytime almostFull = new DailyPlaytime(DAY, DailyPlaytime.MAX_SECONDS - 10);

        List<DailyPlaytime> capped = DailyPlaytime.advance(almostFull, at("2026-11-22T12:00:00Z"), at("2026-11-22T13:00:00Z"));
        List<DailyPlaytime> clockSkew = DailyPlaytime.advance(new DailyPlaytime(DAY, 60), at("2026-11-22T12:00:00Z"), at("2026-11-22T11:00:00Z"));

        assertEquals(List.of(List.of(new DailyPlaytime(DAY, DailyPlaytime.MAX_SECONDS)), List.of(new DailyPlaytime(DAY, 60))), List.of(capped, clockSkew));
    }
}
