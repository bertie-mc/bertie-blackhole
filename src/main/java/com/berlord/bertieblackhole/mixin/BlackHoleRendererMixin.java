package com.berlord.bertieblackhole.mixin;

import com.berlord.bertieblackhole.StatefulBlackHole;
import com.berlord.bertieblackhole.config.BbhConfig;
import com.berlord.bertieblackhole.config.LevelDef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stal111.forbidden_arcanus.client.renderer.block.BlackHoleRenderer;
import com.stal111.forbidden_arcanus.common.block.entity.BlackHoleBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tints the purple aura ring that F&amp;A draws around the hole. Ordinal 1 is the aura; ordinal 0
 * is the black core model, which is deliberately left alone.
 */
@Mixin(BlackHoleRenderer.class)
public class BlackHoleRendererMixin {

    @Redirect(method = "render(Lcom/stal111/forbidden_arcanus/common/block/entity/BlackHoleBlockEntity;"
            + "FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    ordinal = 1))
    private void bbh$tintAura(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                              BlackHoleBlockEntity blockEntity, float partialTick, PoseStack outerPose,
                              MultiBufferSource buffers, int outerLight, int outerOverlay) {
        part.render(pose, consumer, light, overlay, bbh$tintFor(blockEntity));
    }

    private static int bbh$tintFor(BlackHoleBlockEntity blockEntity) {
        if (!(blockEntity instanceof StatefulBlackHole hole)) {
            return 0xFFFFFFFF;
        }
        int holeLevel = hole.bbh$level();
        if (holeLevel <= 0) {
            return 0xFFFFFFFF;
        }
        LevelDef def = BbhConfig.get().levelDef(holeLevel);
        if (def == null || def.particleColor() < 0) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | def.particleColor();
    }
}
