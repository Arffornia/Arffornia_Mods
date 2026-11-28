package fr.thegostsniperfr.arffornia.client.screen.title;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Word wrap for tile subtitles: at most {@code maxLines}, the last one cut with an ellipsis. */
final class TextWrap {
    private TextWrap() {}

    static List<String> wrap(String text, ToDoubleFunction<String> width, double maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String[] words = text.strip().split("\\s+");
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < words.length && lines.size() < maxLines) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (line.isEmpty() || width.applyAsDouble(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                i++;
            } else {
                lines.add(line.toString());
                line.setLength(0);
            }
        }
        if (!line.isEmpty() && lines.size() < maxLines) lines.add(line.toString());
        lines.replaceAll(l -> width.applyAsDouble(l) > maxWidth ? ellipsize(l, width, maxWidth) : l);
        int last = lines.size() - 1;
        if (i < words.length && !lines.get(last).endsWith("…")) {
            lines.set(last, ellipsize(lines.get(last), width, maxWidth));
        }
        return lines;
    }

    private static String ellipsize(String line, ToDoubleFunction<String> width, double maxWidth) {
        String cut = line;
        while (!cut.isEmpty() && width.applyAsDouble(cut + "…") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut.stripTrailing() + "…";
    }
}
