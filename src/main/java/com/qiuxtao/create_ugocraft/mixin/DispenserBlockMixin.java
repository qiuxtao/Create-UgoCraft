package com.qiuxtao.create_ugocraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 全局拦截发射器/投掷器的更新，用于在装配和释放移动结构时阻断虚假的方块更新。
 */
@Mixin(DispenserBlock.class)
public class DispenserBlockMixin {

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void create_ugocraft$suppressNeighborUpdate(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving,
            CallbackInfo ci) {
        if (com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void create_ugocraft$suppressTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation) {
            ci.cancel();
        }
    }
}
