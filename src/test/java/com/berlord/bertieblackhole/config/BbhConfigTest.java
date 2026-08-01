package com.berlord.bertieblackhole.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbhConfigTest {

    @Test
    void parsesCountersColoursAndCumulativeExchanges() {
        BbhConfig config = parse("""
                {
                  "firstOutputDelayTicks": 7,
                  "subsequentOutputDelayTicks": 3,
                  "acceptOverCap": true,
                  "eatUnlisted": true,
                  "levels": [
                    {"level": 0, "exchanges": [
                      {"id": "early", "input": "minecraft:dirt", "count": 2,
                       "output": "minecraft:stone"}
                    ]},
                    {"level": 1, "particleColor": "#D01818", "requires": {
                      "matter": {"max": 64, "acceptOverCap": false,
                                 "items": {"minecraft:dirt": 5, "#c:dusts": 2}}
                    }, "exchanges": [
                      {"id": "late", "input": "minecraft:stone", "count": 8,
                       "output": "minecraft:diamond", "outputCount": 2}
                    ]}
                  ]
                }
                """);

        assertEquals(7, config.firstOutputDelayTicks());
        assertEquals(3, config.subsequentOutputDelayTicks());
        assertTrue(config.acceptOverCap());
        assertTrue(config.eatUnlisted());
        assertEquals(1, config.maxLevel());
        assertEquals(0xD01818, config.levelDef(1).particleColor());

        CounterDef counter = config.nextLevelDef(0).requires().get("matter");
        assertEquals(64, counter.max());
        assertFalse(counter.acceptsOverCap(config.acceptOverCap()));
        assertEquals("minecraft:dirt", counter.items().getFirst().getKey().toString());
        assertEquals("#c:dusts", counter.items().get(1).getKey().toString());

        assertEquals(1, config.exchangesFor(0).size());
        assertEquals(2, config.exchangesFor(1).size());
        assertEquals(2, config.exchangesFor(99).size());
        assertEquals("early", config.exchangesFor(-1).getFirst().id());
    }

    @Test
    void rejectsInvalidStructuralValues() {
        assertConfigError("{\"firstOutputDelayTicks\":0}", "firstOutputDelayTicks");
        assertConfigError("{\"levels\":[{\"level\":-1}]}", "level must be 0 or greater");
        assertConfigError("{\"levels\":[{\"level\":1},{\"level\":1}]}", "duplicate entry");
        assertConfigError("{\"levels\":[{\"level\":1,\"particleColor\":\"purple\"}]}", "not a hex colour");
        assertConfigError("""
                {"levels":[{"level":1,"requires":{"matter":{"max":4,"items":{}}}}]}
                """, "items\" is empty");
        assertConfigError("""
                {"levels":[{"level":0,"exchanges":[{"id":"x","input":"minecraft:dirt",
                  "count":1}]}]}
                """, "missing \"output\"");
    }

    private static BbhConfig parse(String json) {
        return BbhConfig.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static void assertConfigError(String json, String message) {
        ConfigException failure = assertThrows(ConfigException.class, () -> parse(json));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }
}
