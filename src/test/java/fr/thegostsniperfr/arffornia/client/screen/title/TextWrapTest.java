package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextWrapTest {
    private static List<String> wrap(String text, int maxChars, int maxLines) {
        return TextWrap.wrap(text, String::length, maxChars, maxLines);
    }

    @Test
    void keepsAShortHeadlineOnOneLine() {
        assertEquals(List.of("Season 2"), wrap("Season 2", 14, 2));
    }

    @Test
    void wrapsAtWordsOntoTwoLines() {
        assertEquals(List.of("Season 2 opens", "Friday"), wrap("Season 2 opens Friday", 14, 2));
    }

    @Test
    void cutsTheLastLineWithAnEllipsisWhenWordsAreLeft() {
        assertEquals(List.of("Season 2 opens", "Friday with a…"), wrap("Season 2 opens Friday with a new map", 14, 2));
    }

    @Test
    void cutsAWordLongerThanTheLine() {
        assertEquals(List.of("Supercalifr…"), wrap("Supercalifragilistic", 12, 2));
    }
}
