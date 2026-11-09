package fr.thegostsniperfr.arffornia.shop;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardEntryTest {

    private static RewardEntry parse(String json) {
        return RewardEntry.parse(JsonParser.parseString(json));
    }

    @Test
    void parsesEachKnownType() {
        assertEquals(new RewardEntry.Item("minecraft:diamond", 3), parse("{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":3}"));
        assertEquals(new RewardEntry.Permission("cosmetic.pet.kitten", true), parse("{\"type\":\"permission\",\"node\":\"cosmetic.pet.kitten\"}"));
        assertEquals(new RewardEntry.Permission("cosmetic.hat", false), parse("{\"type\":\"permission\",\"node\":\"cosmetic.hat\",\"value\":false}"));
        assertEquals(new RewardEntry.Rank("vip"), parse("{\"type\":\"rank\",\"group\":\"vip\"}"));
        assertEquals(new RewardEntry.LuckyBox("arffornia:streak", 2), parse("{\"type\":\"lucky_box\",\"box\":\"arffornia:streak\",\"count\":2}"));
    }

    @Test
    void unknownTypeIsUnsupportedNotAnError() {
        RewardEntry entry = parse("{\"type\":\"pet\",\"pet\":\"kitten\"}");

        assertEquals(new RewardEntry.Unsupported("pet", "unknown type"), entry);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"item\",\"item\":\"diamond\",\"count\":1}",
            "{\"type\":\"item\",\"item\":\"minecraft:diamond[enchantments={}]\",\"count\":1}",
            "{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":0}",
            "{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":2305}",
            "{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1.5}",
            "{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":\"3\"}",
            "{\"type\":\"permission\",\"node\":\"*\"}",
            "{\"type\":\"permission\",\"node\":\"a b\"}",
            "{\"type\":\"permission\",\"node\":\"x.y\",\"value\":\"true\"}",
            "{\"type\":\"rank\",\"group\":\"admin; op\"}",
            "{\"type\":\"permission\",\"node\":\"group.admin\"}",
            "{\"type\":\"permission\",\"node\":\"prefix.100.owner\"}",
            "{\"type\":\"permission\",\"node\":\"meta.x.y\"}",
            "{\"type\":\"lucky_box\",\"box\":\"arffornia:streak\",\"count\":65}",
            "{\"item\":\"minecraft:diamond\",\"count\":1}",
            "[1]"
    })
    void invalidEntriesAreUnsupported(String json) {
        assertInstanceOf(RewardEntry.Unsupported.class, parse(json));
    }

    @Test
    void allowedPrefixesMatchWholeNodeSegments() {
        assertTrue(RewardEntry.isNodeUnder("cosmetic.pet.kitten", "cosmetic."));
        assertTrue(RewardEntry.isNodeUnder("cosmetic.pet.kitten", "cosmetic"));
        assertFalse(RewardEntry.isNodeUnder("cosmetics_admin.all", "cosmetic"));
        assertFalse(RewardEntry.isNodeUnder("minecraft.command.op", "cosmetic."));
    }
}
