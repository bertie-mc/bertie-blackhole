package com.berlord.bertieblackhole;

import com.berlord.bertieblackhole.config.BbhConfig;
import com.berlord.bertieblackhole.config.CounterDef;
import com.berlord.bertieblackhole.config.ExchangeDef;
import com.berlord.bertieblackhole.config.LevelDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything this mod adds to a single black hole: how far it has been levelled, how full each
 * counter is, what inputs are banked for each exchange, and the timer that drives conversions.
 *
 * <p>Deliberately free of Forbidden and Arcanus types - it talks to the block entity through
 * {@link StatefulBlackHole}.
 */
public class BlackHoleState {

    public static final String NBT_KEY = "BertieBlackHole";

    private int level;
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final Map<String, Integer> buffers = new LinkedHashMap<>();

    /** Ticks until the pending conversion pops; 0 when nothing is queued. */
    private int outputTimer;
    @Nullable
    private String pendingExchange;

    // ---------------------------------------------------------------- reads

    public int level() {
        return level;
    }

    public Map<String, Integer> counters() {
        return Collections.unmodifiableMap(counters);
    }

    public Map<String, Integer> buffers() {
        return Collections.unmodifiableMap(buffers);
    }

    public int outputTimer() {
        return outputTimer;
    }

    @Nullable
    public String pendingExchange() {
        return pendingExchange;
    }

    // ---------------------------------------------------------------- absorption

    /**
     * Called the moment F&amp;A decides an item has reached the centre - the exact point where it
     * would otherwise have destroyed it. No polling, no radius of our own: the hole's own pull is
     * the delivery mechanism, and an item is dealt with the instant it arrives.
     *
     * <p>Anything reaching here has already passed F&amp;A's own filter, so it is neither one of
     * the hole's own outputs nor in {@code #forbidden_arcanus:black_hole_unaffected}.
     */
    public void onItemReachedCentre(Level world, BlockPos pos, StatefulBlackHole hole, ItemEntity entity) {
        if (world.isClientSide || !entity.isAlive()) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        BbhConfig cfg = BbhConfig.get();

        // Counters win over exchanges when an item appears in both - the ladder is the point of
        // the block, and a full counter releases the item to nothing else.
        LevelDef next = cfg.nextLevelDef(level);
        if (next != null && intoCounter(cfg, next, hole, entity, stack)) {
            tryTransform(cfg, world, pos, hole);
            return;
        }

        ExchangeDef exchange = cfg.matchExchange(level, stack);
        if (exchange != null) {
            buffers.merge(exchange.id(), stack.getCount(), Integer::sum);
            entity.discard();
            hole.bbh$markChanged();
            return;
        }

        // Unlisted. Voiding it is opt-in; otherwise it just hangs there until it despawns.
        if (cfg.eatUnlisted()) {
            entity.discard();
        }
    }

    /**
     * @return true when some counter claimed this item - either it was absorbed, or the counter
     *         is full and is ignoring it. False means no counter wants it at all.
     */
    private boolean intoCounter(BbhConfig cfg, LevelDef next, StatefulBlackHole hole,
                                ItemEntity entity, ItemStack stack) {
        for (CounterDef def : next.requires().values()) {
            int value = def.valueOf(stack);
            if (value <= 0) {
                continue;
            }
            int current = counters.getOrDefault(def.id(), 0);
            boolean overCap = def.acceptsOverCap(cfg.acceptOverCap());

            int take;
            if (overCap) {
                take = stack.getCount();
            } else if (current >= def.max()) {
                take = 0;
            } else {
                // The last item is allowed to overshoot - you cannot absorb a fraction of one.
                int needed = def.max() - current;
                take = Math.min(stack.getCount(), (needed + value - 1) / value);
            }

            if (take <= 0) {
                // Full: the item is simply ignored - not absorbed, not moved, not destroyed.
                return true;
            }

            int raised = current + value * take;
            counters.put(def.id(), overCap ? raised : Math.min(def.max(), raised));

            ItemStack remainder = stack.copy();
            remainder.shrink(take);
            if (remainder.isEmpty()) {
                entity.discard();
            } else {
                // Only happens on the stack that tops the counter off. The leftover keeps
                // hanging at the centre, and from the next tick the counter ignores it.
                entity.setItem(remainder);
            }
            hole.bbh$markChanged();
            return true;
        }
        return false;
    }

    private void tryTransform(BbhConfig cfg, Level world, BlockPos pos, StatefulBlackHole hole) {
        LevelDef next = cfg.nextLevelDef(level);
        if (next == null || next.requires().isEmpty()) {
            return;
        }
        for (CounterDef def : next.requires().values()) {
            if (counters.getOrDefault(def.id(), 0) < def.max()) {
                return;
            }
        }

        level = next.level();
        counters.clear();
        playTransformSound(world, pos, next);
        hole.bbh$sync();
        BertieBlackHole.LOGGER.debug("Black hole at {} transformed to level {}", pos, level);
    }

    private void playTransformSound(Level world, BlockPos pos, LevelDef def) {
        if (def.sound() == null) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(def.sound());
        if (event == null) {
            BertieBlackHole.LOGGER.warn("Unknown transform sound {} - nothing will play", def.sound());
            return;
        }
        world.playSound((Player) null, pos, event, SoundSource.BLOCKS, def.soundVolume(), def.soundPitch());
    }

    // ---------------------------------------------------------------- conversions

    /**
     * The first output of a run lands {@code firstOutputDelayTicks} after the buffer reached the
     * recipe cost; every further one follows {@code subsequentOutputDelayTicks} later, until the
     * buffer drops below the cost. Whatever is left over stays banked.
     */
    public void serverTick(Level world, StatefulBlackHole hole) {
        if (world.isClientSide) {
            return;
        }
        BbhConfig cfg = BbhConfig.get();

        if (outputTimer > 0) {
            outputTimer--;
            if (outputTimer > 0) {
                return;
            }
            fire(cfg, hole);
            schedule(cfg, cfg.subsequentOutputDelayTicks());
            return;
        }
        schedule(cfg, cfg.firstOutputDelayTicks());
    }

    private void fire(BbhConfig cfg, StatefulBlackHole hole) {
        ExchangeDef exchange = findExchange(cfg, pendingExchange);
        pendingExchange = null;
        if (exchange == null) {
            return;
        }
        int have = buffers.getOrDefault(exchange.id(), 0);
        if (have < exchange.count()) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(exchange.output());
        if (item == Items.AIR) {
            BertieBlackHole.LOGGER.warn("Exchange '{}' outputs unknown item {}", exchange.id(), exchange.output());
            return;
        }
        buffers.put(exchange.id(), have - exchange.count());
        hole.bbh$throwOut(new ItemStack(item, exchange.outputCount()));
        hole.bbh$markChanged();
    }

    private void schedule(BbhConfig cfg, int delay) {
        if (pendingExchange != null) {
            return;
        }
        for (ExchangeDef exchange : cfg.exchangesFor(level)) {
            if (buffers.getOrDefault(exchange.id(), 0) >= exchange.count()) {
                pendingExchange = exchange.id();
                outputTimer = delay;
                return;
            }
        }
    }

    @Nullable
    private ExchangeDef findExchange(BbhConfig cfg, @Nullable String id) {
        if (id == null) {
            return null;
        }
        for (ExchangeDef exchange : cfg.exchangesFor(level)) {
            if (exchange.id().equals(id)) {
                return exchange;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- persistence

    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("outputTimer", outputTimer);
        if (pendingExchange != null) {
            tag.putString("pending", pendingExchange);
        }
        tag.put("counters", writeMap(counters));
        tag.put("buffers", writeMap(buffers));
        root.put(NBT_KEY, tag);
    }

    /** The client only renders off the level, so the update packet carries nothing else. */
    public void saveForClient(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        root.put(NBT_KEY, tag);
    }

    public void load(CompoundTag root) {
        if (!root.contains(NBT_KEY)) {
            return;
        }
        CompoundTag tag = root.getCompound(NBT_KEY);
        level = tag.getInt("level");
        // "counters" is only written by the full save, never by the client update packet - so its
        // absence means this is a level-only sync and the rest must be left alone.
        if (tag.contains("counters")) {
            outputTimer = tag.getInt("outputTimer");
            pendingExchange = tag.contains("pending") ? tag.getString("pending") : null;
            readMap(tag.getCompound("counters"), counters);
            readMap(tag.getCompound("buffers"), buffers);
        }
    }

    private static CompoundTag writeMap(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach(tag::putInt);
        return tag;
    }

    private static void readMap(CompoundTag tag, Map<String, Integer> into) {
        into.clear();
        for (String key : tag.getAllKeys()) {
            into.put(key, tag.getInt(key));
        }
    }
}
