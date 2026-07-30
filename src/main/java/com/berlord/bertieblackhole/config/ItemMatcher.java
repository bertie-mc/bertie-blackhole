package com.berlord.bertieblackhole.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of a counter's item list, or an exchange's input. Written in JSON either as a
 * plain item id ({@code "forbidden_arcanus:dark_matter"}) or as a tag ({@code "#c:dusts"}).
 */
public final class ItemMatcher {

    private final ResourceLocation id;
    private final boolean tag;
    private final TagKey<Item> tagKey;

    private ItemMatcher(ResourceLocation id, boolean tag) {
        this.id = id;
        this.tag = tag;
        this.tagKey = tag ? TagKey.create(Registries.ITEM, id) : null;
    }

    /** Parses the {@code "#namespace:path"} / {@code "namespace:path"} form. */
    public static ItemMatcher parse(String raw, String where) {
        boolean isTag = raw.startsWith("#");
        String body = isTag ? raw.substring(1) : raw;
        ResourceLocation parsed = ResourceLocation.tryParse(body);
        if (parsed == null) {
            throw new ConfigException(where + ": '" + raw + "' is not a valid item id or tag");
        }
        return new ItemMatcher(parsed, isTag);
    }

    public boolean matches(ItemStack stack) {
        return tag ? stack.is(tagKey) : stack.is(resolveItem());
    }

    /**
     * The concrete item this matcher names, or {@code null} for a tag / an id that no loaded
     * mod registers. Only used for exchange outputs, which have to be a single real item.
     */
    public Item resolveItem() {
        if (tag) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    public boolean isTag() {
        return tag;
    }

    public ResourceLocation id() {
        return id;
    }

    @Override
    public String toString() {
        return tag ? "#" + id : id.toString();
    }
}
