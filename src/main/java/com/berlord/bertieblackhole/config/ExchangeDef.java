package com.berlord.bertieblackhole.config;

import net.minecraft.resources.ResourceLocation;

/**
 * A conversion the black hole offers once it has reached the level that declares it. Exchanges
 * are cumulative: a level-2 hole still offers everything level 1 declared.
 *
 * @param id          stable key - the leftover input buffer is saved under it, so renaming an
 *                    exchange orphans whatever was banked in an existing world
 * @param input       what gets absorbed into the buffer
 * @param count       how many input items one conversion consumes
 * @param output      item spat back out
 * @param outputCount stack size of one output
 */
public record ExchangeDef(String id, ItemMatcher input, int count, ResourceLocation output, int outputCount) {
}
