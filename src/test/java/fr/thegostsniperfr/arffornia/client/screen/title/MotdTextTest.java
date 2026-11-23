package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MotdTextTest {
    @Test
    void usesTheSecondLineWithoutColourCodes() {
        assertEquals(Optional.of("Your factory is not big enough. Yet."),
                MotdText.splash("§6§lArffornia §7| Modded\n§eYour factory is not big enough. Yet."));
    }

    @Test
    void fallsBackToTheOnlyLine() {
        assertEquals(Optional.of("Arffornia"), MotdText.splash("§6Arffornia"));
    }

    @Test
    void hasNothingForABlankMotd() {
        assertEquals(Optional.empty(), MotdText.splash("  \n "));
    }
}
