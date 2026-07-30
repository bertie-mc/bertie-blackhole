package com.berlord.bertieblackhole.config;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * One named counter on a level's requirement list. Several items can feed the same counter with
 * different weights - that is the whole point of the counter being named rather than per-item.
 *
 * @param id            counter name, e.g. "matter"
 * @param max           value at which this counter is considered full
 * @param acceptOverCap null to inherit the global default; true keeps absorbing past max
 * @param items         matcher -> value added per single item absorbed
 */
public record CounterDef(String id, int max, Boolean acceptOverCap, List<Map.Entry<ItemMatcher, Integer>> items) {

    /** The per-item value for this stack, or 0 if this counter does not want it. */
    public int valueOf(ItemStack stack) {
        for (Map.Entry<ItemMatcher, Integer> entry : items) {
            if (entry.getKey().matches(stack)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public boolean acceptsOverCap(boolean globalDefault) {
        return acceptOverCap == null ? globalDefault : acceptOverCap;
    }
}
