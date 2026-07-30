package com.berlord.bertieblackhole.mixin;

import com.berlord.bertieblackhole.BlackHoleState;
import com.berlord.bertieblackhole.StatefulBlackHole;
import com.stal111.forbidden_arcanus.common.block.entity.BlackHoleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bolts the level/counter machinery onto Forbidden and Arcanus' black hole.
 *
 * <p>Base behaviour is left alone: the ±5 pull, the 60-XP-to-Xpetrified-Orb conversion, the
 * protection tag and the 4.0 magic damage to arrows and other affected entities all still run
 * untouched. The one thing taken over is what happens to an <em>item</em> entity when it reaches
 * the centre - instead of being shredded it is offered to {@link BlackHoleState}.
 */
@Mixin(BlackHoleBlockEntity.class)
public abstract class BlackHoleBlockEntityMixin extends BlockEntity implements StatefulBlackHole {

    @Shadow
    private void throwOutItemStack(Level level, ItemStack stack, Vec3 pos) {
        throw new AssertionError("mixin stub");
    }

    @Unique
    private final BlackHoleState bbh$state = new BlackHoleState();

    // Never constructed - Mixin drops this, it only exists so javac accepts extending BlockEntity.
    private BlackHoleBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------ behaviour

    /**
     * This call site is F&amp;A deciding that something has arrived at the centre, which makes it
     * exactly the right hook for absorption - the hole's own pull does the collecting, so the mod
     * needs no sweep and no radius of its own. Items are handed to the state machine; everything
     * else still burns.
     */
    @Redirect(method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean bbh$onReachCentre(Entity entity, DamageSource source, float amount,
                                             Level level, BlockPos pos, BlockState state,
                                             BlackHoleBlockEntity blockEntity) {
        if (entity instanceof ItemEntity item) {
            StatefulBlackHole hole = (StatefulBlackHole) (Object) blockEntity;
            hole.bbh$state().onItemReachedCentre(level, pos, hole, item);
            return false;
        }
        return entity.hurt(source, amount);
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void bbh$serverTick(Level level, BlockPos pos, BlockState state,
                                       BlackHoleBlockEntity blockEntity, CallbackInfo ci) {
        StatefulBlackHole hole = (StatefulBlackHole) (Object) blockEntity;
        hole.bbh$state().serverTick(level, hole);
    }

    // ------------------------------------------------------------ StatefulBlackHole

    @Override
    public BlackHoleState bbh$state() {
        return bbh$state;
    }

    @Override
    public int bbh$level() {
        return bbh$state.level();
    }

    @Override
    public void bbh$throwOut(ItemStack stack) {
        if (level != null) {
            throwOutItemStack(level, stack, getBlockPos().getCenter());
        }
    }

    @Override
    public void bbh$markChanged() {
        setChanged();
    }

    @Override
    public void bbh$sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------ persistence and sync

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void bbh$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        bbh$state.save(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void bbh$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        bbh$state.load(tag);
    }

    /** F&amp;A does not sync this block entity at all; the level has to reach the client somehow. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        bbh$state.saveForClient(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
