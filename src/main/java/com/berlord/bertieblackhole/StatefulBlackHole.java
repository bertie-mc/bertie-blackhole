package com.berlord.bertieblackhole;

import net.minecraft.world.item.ItemStack;

/**
 * What {@link BlackHoleState} is allowed to ask of the block entity it lives on.
 *
 * <p>Implemented by the mixin on Forbidden and Arcanus' BlackHoleBlockEntity. Keeping the
 * contract here means nothing outside the mixin package names an F&amp;A type, so the mod's
 * classes still load cleanly when F&amp;A is absent and the mixins are switched off.
 */
public interface StatefulBlackHole {

    BlackHoleState bbh$state();

    /** Convenience for the client, which only ever needs the level. */
    int bbh$level();

    /** Spawn a conversion output through F&amp;A's own thrower, so it is not re-eaten. */
    void bbh$throwOut(ItemStack stack);

    /** Persist to disk. */
    void bbh$markChanged();

    /** Persist and push the new level to everyone tracking the chunk, for particles and aura. */
    void bbh$sync();
}
