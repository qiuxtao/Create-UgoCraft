package com.qiuxtao.create_ugocraft.mixin;

import com.qiuxtao.create_ugocraft.entity.UgoSlideContraption;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 AbstractContraptionEntity.disassemble() 中的拆卸音效播放，
 * 当 contraption 为 UgoSlideContraption 时跳过 Create 原生的 CONTRAPTION_DISASSEMBLE 声音，
 * 改由 SlideCoreBlockEntity 自行播放自定义音效。
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {

    @Shadow
    protected Contraption contraption;

    @Redirect(
            method = "disassemble",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/AllSoundEvents$SoundEntry;playOnServer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Vec3i;)V"),
            remap = false
    )
    private void redirectDisassembleSound(AllSoundEvents.SoundEntry instance, Level level, Vec3i pos) {
        if (this.contraption instanceof UgoSlideContraption) {
            // UgoCraft 的滑动结构使用自定义音效，跳过 Create 原生拆卸音效
            return;
        }
        // 非 UgoCraft 的 contraption 正常播放
        instance.playOnServer(level, pos);
    }
}
