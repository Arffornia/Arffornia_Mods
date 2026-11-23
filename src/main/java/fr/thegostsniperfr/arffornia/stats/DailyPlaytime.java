package fr.thegostsniperfr.arffornia.stats;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * A player's playtime on one UTC day, the unit the website stores (spec 019 Q5, {@code POST /players/playtime}).
 * Days are UTC because the website's {@code app.timezone} is UTC.
 */
public record DailyPlaytime(LocalDate day, long seconds) {
    public static final long MAX_SECONDS = 86_400;
    public static final DailyPlaytime NONE = new DailyPlaytime(LocalDate.EPOCH, 0);

    /**
     * Adds the time played between {@code from} and {@code to} to {@code current}, split at UTC midnight.
     *
     * @return every day to report with its running total, oldest first; the last one is the new running day.
     * A {@code current} from an earlier day comes first, so a day cut short by a restart is still reported.
     */
    public static List<DailyPlaytime> advance(DailyPlaytime current, Instant from, Instant to) {
        List<DailyPlaytime> days = new ArrayList<>();
        LocalDate day = from.atZone(ZoneOffset.UTC).toLocalDate();
        long seconds = 0;
        if (current.day().equals(day)) {
            seconds = current.seconds();
        } else if (current.seconds() > 0) {
            days.add(current);
        }
        Instant cursor = from;
        while (true) {
            Instant midnight = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = to.isBefore(midnight) ? to : midnight;
            long played = end.isAfter(cursor) ? Duration.between(cursor, end).toSeconds() : 0;
            seconds = Math.min(MAX_SECONDS, seconds + played);
            days.add(new DailyPlaytime(day, seconds));
            if (!to.isAfter(midnight)) {
                return days;
            }
            cursor = midnight;
            day = day.plusDays(1);
            seconds = 0;
        }
    }
}
