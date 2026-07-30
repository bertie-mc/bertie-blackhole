package com.berlord.bertieblackhole.config;

import com.berlord.bertieblackhole.BertieBlackHole;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole mod's tuning surface: one JSON file in config/, loaded per side.
 *
 * <p>Client and server each read their own copy - the level a hole has reached is synced, but
 * the colours and timings are not. In a modpack that is fine because both sides ship the same
 * file; if you edit it on a dedicated server, push the same file to clients or the particles
 * will disagree with the server's idea of the level.
 */
public record BbhConfig(int firstOutputDelayTicks,
                        int subsequentOutputDelayTicks,
                        boolean acceptOverCap,
                        boolean eatUnlisted,
                        Map<Integer, LevelDef> levels,
                        /** Level -> every exchange available at it, accumulated. Precomputed: this
                         *  is read on the block entity's hot tick path. Keys are 0..maxLevel. */
                        Map<Integer, List<ExchangeDef>> exchangesByLevel) {

    public static final String FILE_NAME = "bertie_blackhole.json";
    private static final String DEFAULT_RESOURCE = "/bertie_blackhole/default_config.json";

    private static volatile BbhConfig active;

    // ---------------------------------------------------------------- lookups

    @Nullable
    public LevelDef levelDef(int level) {
        return levels.get(level);
    }

    /** The highest level defined; a hole that reaches it stops accepting counter items. */
    public int maxLevel() {
        return levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** What a hole currently at {@code level} is collecting towards, or null if it is maxed. */
    @Nullable
    public LevelDef nextLevelDef(int level) {
        return levels.get(level + 1);
    }

    /** Exchanges are cumulative - a level-2 hole keeps offering the level-1 conversions. */
    public List<ExchangeDef> exchangesFor(int level) {
        if (exchangesByLevel.isEmpty()) {
            return List.of();
        }
        // Clamp so a hole saved under a longer config still resolves after levels are removed.
        int clamped = Math.max(0, Math.min(level, exchangesByLevel.size() - 1));
        return exchangesByLevel.getOrDefault(clamped, List.of());
    }

    /** The first exchange at this level whose input accepts the stack, or null. */
    @Nullable
    public ExchangeDef matchExchange(int level, ItemStack stack) {
        for (ExchangeDef ex : exchangesFor(level)) {
            if (ex.input().matches(stack)) {
                return ex;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- loading

    public static BbhConfig get() {
        BbhConfig local = active;
        if (local == null) {
            synchronized (BbhConfig.class) {
                local = active;
                if (local == null) {
                    local = load();
                    active = local;
                }
            }
        }
        return local;
    }

    /** Re-reads the file from disk. Existing holes keep their level and counters. */
    public static BbhConfig reload() {
        BbhConfig fresh = load();
        active = fresh;
        return fresh;
    }

    private static BbhConfig load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try {
            if (!Files.exists(path)) {
                writeDefaultFile(path);
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            BbhConfig parsed = parse(JsonParser.parseString(json).getAsJsonObject());
            BertieBlackHole.LOGGER.info("Loaded {} ({} level(s) defined)", FILE_NAME, parsed.levels.size());
            return parsed;
        } catch (Exception e) {
            BertieBlackHole.LOGGER.error("Failed to read {} - falling back to the built-in defaults. {}",
                    FILE_NAME, e.toString());
            return builtinDefault();
        }
    }

    private static void writeDefaultFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (InputStream in = BbhConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing bundled resource " + DEFAULT_RESOURCE);
            }
            Files.write(path, in.readAllBytes());
        }
        BertieBlackHole.LOGGER.info("Wrote default {}", path);
    }

    private static BbhConfig builtinDefault() {
        try (InputStream in = BbhConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing bundled resource " + DEFAULT_RESOURCE);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (Exception e) {
            // Nothing left to fall back to; an empty config makes the mod inert rather than crashy.
            BertieBlackHole.LOGGER.error("Built-in default config is unreadable: {}", e.toString());
            return new BbhConfig(60, 20, false, false, Map.of(), Map.of());
        }
    }

    // ---------------------------------------------------------------- parsing

    public static BbhConfig parse(JsonObject root) {
        int first = positive(intOr(root, "firstOutputDelayTicks", 60), "firstOutputDelayTicks");
        int next = positive(intOr(root, "subsequentOutputDelayTicks", 20), "subsequentOutputDelayTicks");
        boolean overCap = boolOr(root, "acceptOverCap", false);
        boolean eatUnlisted = boolOr(root, "eatUnlisted", false);

        Map<Integer, LevelDef> levels = new LinkedHashMap<>();
        JsonArray arr = root.has("levels") ? root.getAsJsonArray("levels") : new JsonArray();
        for (JsonElement el : arr) {
            LevelDef def = parseLevel(el.getAsJsonObject());
            if (levels.put(def.level(), def) != null) {
                throw new ConfigException("levels: duplicate entry for level " + def.level());
            }
        }
        return new BbhConfig(first, next, overCap, eatUnlisted, levels, accumulateExchanges(levels));
    }

    private static Map<Integer, List<ExchangeDef>> accumulateExchanges(Map<Integer, LevelDef> levels) {
        int max = levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Integer, List<ExchangeDef>> byLevel = new LinkedHashMap<>();
        List<ExchangeDef> running = new ArrayList<>();
        for (int i = 0; i <= max; i++) {
            LevelDef def = levels.get(i);
            if (def != null) {
                running.addAll(def.exchanges());
            }
            byLevel.put(i, List.copyOf(running));
        }
        return Map.copyOf(byLevel);
    }

    private static LevelDef parseLevel(JsonObject obj) {
        if (!obj.has("level")) {
            throw new ConfigException("levels: every entry needs a \"level\" number");
        }
        int level = obj.get("level").getAsInt();
        String where = "levels[" + level + "]";
        if (level < 0) {
            throw new ConfigException(where + ": level must be 0 or greater");
        }

        Map<String, CounterDef> requires = new LinkedHashMap<>();
        if (obj.has("requires")) {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("requires").entrySet()) {
                requires.put(e.getKey(), parseCounter(e.getKey(), e.getValue().getAsJsonObject(), where));
            }
        }
        if (level == 0 && !requires.isEmpty()) {
            // Level 0 is where a freshly placed hole starts, so nothing can be required to reach it.
            BertieBlackHole.LOGGER.warn("{}: \"requires\" on level 0 is ignored", where);
            requires.clear();
        }

        ResourceLocation sound = null;
        if (obj.has("sound") && !obj.get("sound").isJsonNull()) {
            String raw = obj.get("sound").getAsString();
            sound = ResourceLocation.tryParse(raw);
            if (sound == null) {
                throw new ConfigException(where + ": \"" + raw + "\" is not a valid sound id");
            }
        }

        List<ExchangeDef> exchanges = new ArrayList<>();
        if (obj.has("exchanges")) {
            for (JsonElement el : obj.getAsJsonArray("exchanges")) {
                exchanges.add(parseExchange(el.getAsJsonObject(), where));
            }
        }

        return new LevelDef(level,
                requires,
                sound,
                floatOr(obj, "soundVolume", 2.0f),
                floatOr(obj, "soundPitch", 0.5f),
                parseColor(obj, where),
                floatOr(obj, "particleShadeJitter", 0.4f),
                exchanges);
    }

    private static CounterDef parseCounter(String id, JsonObject obj, String where) {
        String at = where + ".requires." + id;
        if (!obj.has("max")) {
            throw new ConfigException(at + ": missing \"max\"");
        }
        int max = positive(obj.get("max").getAsInt(), at + ".max");

        Boolean overCap = obj.has("acceptOverCap") ? obj.get("acceptOverCap").getAsBoolean() : null;

        List<Map.Entry<ItemMatcher, Integer>> items = new ArrayList<>();
        if (!obj.has("items")) {
            throw new ConfigException(at + ": missing \"items\"");
        }
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("items").entrySet()) {
            int value = e.getValue().getAsInt();
            if (value <= 0) {
                throw new ConfigException(at + ".items." + e.getKey() + ": value must be 1 or greater");
            }
            items.add(new AbstractMap.SimpleImmutableEntry<>(
                    ItemMatcher.parse(e.getKey(), at + ".items"), value));
        }
        if (items.isEmpty()) {
            throw new ConfigException(at + ": \"items\" is empty, this counter could never fill");
        }
        return new CounterDef(id, max, overCap, List.copyOf(items));
    }

    private static ExchangeDef parseExchange(JsonObject obj, String where) {
        String id = obj.has("id") ? obj.get("id").getAsString() : null;
        if (id == null || id.isBlank()) {
            throw new ConfigException(where + ".exchanges: every entry needs a non-empty \"id\"");
        }
        String at = where + ".exchanges." + id;
        for (String key : new String[]{"input", "count", "output"}) {
            if (!obj.has(key)) {
                throw new ConfigException(at + ": missing \"" + key + "\"");
            }
        }
        ItemMatcher input = ItemMatcher.parse(obj.get("input").getAsString(), at + ".input");
        int count = positive(obj.get("count").getAsInt(), at + ".count");

        String rawOut = obj.get("output").getAsString();
        ResourceLocation output = ResourceLocation.tryParse(rawOut);
        if (output == null) {
            throw new ConfigException(at + ".output: \"" + rawOut + "\" is not a valid item id");
        }
        int outCount = positive(intOr(obj, "outputCount", 1), at + ".outputCount");
        return new ExchangeDef(id, input, count, output, outCount);
    }

    /** Accepts "#RRGGBB", "0xRRGGBB", "RRGGBB" or a raw number; absent means "keep vanilla". */
    private static int parseColor(JsonObject obj, String where) {
        if (!obj.has("particleColor") || obj.get("particleColor").isJsonNull()) {
            return -1;
        }
        JsonElement el = obj.get("particleColor");
        if (el.getAsJsonPrimitive().isNumber()) {
            return el.getAsInt() & 0xFFFFFF;
        }
        String raw = el.getAsString().trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("none")) {
            return -1;
        }
        String body = raw.startsWith("#") ? raw.substring(1)
                : raw.regionMatches(true, 0, "0x", 0, 2) ? raw.substring(2)
                : raw;
        try {
            return Integer.parseInt(body, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new ConfigException(where + ".particleColor: \"" + raw + "\" is not a hex colour");
        }
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        return obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }

    private static float floatOr(JsonObject obj, String key, float fallback) {
        return obj.has(key) ? obj.get(key).getAsFloat() : fallback;
    }

    private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
    }

    private static int positive(int value, String where) {
        if (value <= 0) {
            throw new ConfigException(where + ": must be 1 or greater (was " + value + ")");
        }
        return value;
    }
}
