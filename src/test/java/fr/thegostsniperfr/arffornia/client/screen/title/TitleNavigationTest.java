package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.thegostsniperfr.arffornia.client.screen.title.TitleNavigation.Arrow;
import fr.thegostsniperfr.arffornia.client.screen.title.TitleNavigation.Node;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TitleNavigationTest {
    // The title screen's flower: Play in the middle, axial hex layout of 250 x 216.5 tiles.
    private static final List<Node> TILES = List.of(
            new Node("play", 1440, 560),
            new Node("single", 1440, 343.5),
            new Node("mile", 1627.5, 451.75),
            new Node("mods", 1627.5, 668.25),
            new Node("options", 1440, 776.5),
            new Node("quit", 1252.5, 668.25),
            new Node("news", 1252.5, 451.75));

    @ParameterizedTest(name = "{1} from {0} goes to {2}")
    @CsvSource({
            "play, UP, single",
            "play, DOWN, options",
            "play, RIGHT, mile",
            "play, LEFT, news",
            "mods, UP, mile",
            "mods, LEFT, play",
            "single, DOWN, play",
            "quit, RIGHT, play",
            "options, LEFT, quit",
    })
    void arrowsMoveToTheNearestTileInThatDirectionPreferringTheUpperOne(String from, Arrow arrow, String expected) {
        assertEquals(expected, TitleNavigation.neighbour(TILES, from, arrow));
    }

    @ParameterizedTest(name = "{1} from {0} stays")
    @CsvSource({ "single, UP", "options, DOWN", "mile, RIGHT" })
    void staysWhenNoTileLiesThatWay(String from, Arrow arrow) {
        assertEquals(from, TitleNavigation.neighbour(TILES, from, arrow));
    }
}
