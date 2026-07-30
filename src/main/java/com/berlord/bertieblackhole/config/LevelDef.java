package com.berlord.bertieblackhole.config;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * One rung of the ladder.
 *
 * <p>{@code requires} is what has to be filled to reach this level, so the level-1 entry holds
 * the counters an untransformed hole accepts. {@code exchanges}, {@code sound} and
 * {@code particleColor} are what the hole has once it <em>is</em> at this level.
 *
 * @param particleColor packed 0xRRGGBB, or -1 to leave the vanilla purple portal particles alone
 * @param shadeJitter   how far each particle's brightness drifts from the base colour, 0..1
 */
public record LevelDef(int level,
                       Map<String, CounterDef> requires,
                       ResourceLocation sound,
                       float soundVolume,
                       float soundPitch,
                       int particleColor,
                       float shadeJitter,
                       List<ExchangeDef> exchanges) {
}
