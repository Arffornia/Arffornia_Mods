package fr.thegostsniperfr.arffornia.shop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardCommandFilterTest {

    private final RewardCommandFilter defaults = new RewardCommandFilter(List.of("give"));

    @Test
    void allowsConfiguredRoot() {
        assertTrue(defaults.isAllowed("give Steve minecraft:diamond 1"));
        assertTrue(defaults.isAllowed("/give Steve minecraft:diamond 1"));
        assertTrue(defaults.isAllowed("  GIVE Steve minecraft:diamond 1"));
    }

    @Test
    void refusesOtherRoots() {
        assertFalse(defaults.isAllowed("say hello"));
        assertFalse(defaults.isAllowed("lp user Steve permission set x true"));
        assertFalse(defaults.isAllowed("giveall Steve"));
        assertFalse(defaults.isAllowed("execute run give Steve minecraft:diamond 1"));
    }

    @Test
    void refusesEmptyAndMultiLine() {
        assertFalse(defaults.isAllowed(null));
        assertFalse(defaults.isAllowed(""));
        assertFalse(defaults.isAllowed("   "));
        assertFalse(defaults.isAllowed("/"));
        assertFalse(defaults.isAllowed("give Steve minecraft:diamond 1\nsay hi"));
        assertFalse(defaults.isAllowed("give Steve minecraft:diamond 1\rsay hi"));
    }

    @Test
    void normalizesConfiguredRoots() {
        RewardCommandFilter filter = new RewardCommandFilter(List.of(" /Give ", "LP", ""));
        assertEquals(java.util.Set.of("give", "lp"), filter.allowedRoots());
        assertTrue(filter.isAllowed("lp user Steve permission set x true"));
    }

    @Test
    void emptyListRefusesEverything() {
        assertFalse(new RewardCommandFilter(List.of()).isAllowed("give Steve minecraft:diamond 1"));
    }
}
