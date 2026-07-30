package com.berlord.bertieblackhole.mixin;

import com.berlord.bertieblackhole.StatefulBlackHole;
import com.berlord.bertieblackhole.client.BbhParticles;
import com.berlord.bertieblackhole.config.BbhConfig;
import com.berlord.bertieblackhole.config.LevelDef;
import com.stal111.forbidden_arcanus.common.block.BlackHoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the purple portal particles for the current level's colour. Client side only. */
@Mixin(BlackHoleBlock.class)
public class BlackHoleBlockMixin {

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void bbh$recolour(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof StatefulBlackHole hole)) {
            return;
        }
        int holeLevel = hole.bbh$level();
        if (holeLevel <= 0) {
            return;
        }
        LevelDef def = BbhConfig.get().levelDef(holeLevel);
        if (def == null || def.particleColor() < 0) {
            return;
        }
        BbhParticles.spawn(level, pos, random, def);
        ci.cancel();
    }
}
