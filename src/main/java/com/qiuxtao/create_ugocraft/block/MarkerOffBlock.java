package com.qiuxtao.create_ugocraft.block;

import net.minecraft.world.level.block.Block;

/**
 * Marker OFF Block (标记块 - 关闭端)
 *
 * 在原版 UgoCraft 中对应 Azathoth 方块的 metadata=0 状态。
 * 
 * 放置在结构中，定义结构的"断电时的终点"。
 * 当红石信号断开时，结构将朝 Marker OFF 方向滑动回去。
 * 该方块被包含在移动结构中一起滑动。
 */
public class MarkerOffBlock extends Block {
    public MarkerOffBlock(Properties properties) {
        super(properties);
    }
}
