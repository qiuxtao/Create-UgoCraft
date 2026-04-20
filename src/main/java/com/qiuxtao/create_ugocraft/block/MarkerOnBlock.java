package com.qiuxtao.create_ugocraft.block;

import net.minecraft.world.level.block.Block;

/**
 * Marker ON Block (标记块 - 开启端)
 *
 * 在原版 UgoCraft 中对应 Azathoth 方块的 metadata=1 状态。
 * 
 * 放置在结构中，定义结构的"通电时的终点"。
 * 当红石信号激活滑动核心时，结构将朝 Marker ON 方向滑动。
 * 该方块被包含在移动结构中一起滑动。
 */
public class MarkerOnBlock extends Block {
    public MarkerOnBlock(Properties properties) {
        super(properties);
    }
}
