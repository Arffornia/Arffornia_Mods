package fr.thegostsniperfr.arffornia.client.screen.title;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/** The splash text is line 2 of the server-list MOTD, colour codes stripped (spec 007 D13, D14). */
public final class MotdText {
    private static final Pattern FORMATTING = Pattern.compile("(?i)\u00a7[0-9a-fk-or]");

    private MotdText() {}

    public static Optional<String> splash(String motd) {
        String[] lines = Arrays.stream(motd.split("\\R"))
                .map(l -> FORMATTING.matcher(l).replaceAll(""))
                .map(String::strip)
                .filter(l -> !l.isEmpty())
                .toArray(String[]::new);
        if (lines.length == 0) return Optional.empty();
        return Optional.of(lines.length > 1 ? lines[1] : lines[0]);
    }
}
