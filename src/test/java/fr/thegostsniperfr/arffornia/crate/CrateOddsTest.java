package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrateOddsTest {
    private static final double EPSILON = 1e-9;

    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }

    private static CrateOdds.Odds of(List<CrateOdds.Odds> odds, String item) {
        return odds.stream().filter(o -> o.item().equals(item)).findFirst().orElseThrow();
    }

    @Test
    void singleRollChanceIsTheWeightOverTheTotalWeightIncludingEmptyEntries() throws IOException {
        List<CrateOdds.Odds> odds = CrateOdds.compute(read("src/test/resources/crate/odds_fixture.json"));

        assertEquals(0.60, of(odds, "minecraft:iron_ingot").chance(), EPSILON);
    }

    @Test
    void countRangeComesFromSetCount() throws IOException {
        CrateOdds.Odds iron = of(CrateOdds.compute(read("src/test/resources/crate/odds_fixture.json")), "minecraft:iron_ingot");

        assertEquals(List.of(8, 16), List.of(iron.minCount(), iron.maxCount()));
    }

    @Test
    void uniformRollsAreAveragedOverEachRollCount() throws IOException {
        // One roll: 3/4. Two rolls: 1 - (1/4)^2 = 15/16. Averaged: 27/32.
        CrateOdds.Odds emerald = of(CrateOdds.compute(read("src/test/resources/crate/odds_fixture.json")), "minecraft:emerald");

        assertEquals(27.0 / 32.0, emerald.chance(), EPSILON);
    }

    @Test
    void anItemInTwoPoolsCombinesBothDraws() throws IOException {
        // Pool 1 misses 0.7; pool 2 misses (3/4 + 9/16) / 2 = 21/32.
        CrateOdds.Odds diamond = of(CrateOdds.compute(read("src/test/resources/crate/odds_fixture.json")), "minecraft:diamond");

        assertEquals(1 - 0.7 * 21.0 / 32.0, diamond.chance(), EPSILON);
    }

    @Test
    void shippedStreakCrateOddsMatchItsWeights() throws IOException {
        List<CrateOdds.Odds> odds = CrateOdds.compute(read("src/main/resources/data/arffornia/loot_table/crate/streak.json"));

        double[] expected = {0.40, 0.25, 0.20, 0.12, 0.03};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], odds.get(i).chance(), EPSILON);
        }
    }
}
