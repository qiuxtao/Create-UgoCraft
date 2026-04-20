package com.qiuxtao.create_ugocraft.entity;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public class UgoSlideContraption extends TranslatingContraption {

    protected Direction slideDirection;

    public UgoSlideContraption() {}

    public UgoSlideContraption(Direction slideDirection) {
        this.slideDirection = slideDirection;
    }

    private java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> customStructure;

    public void setCustomStructure(java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> structure) {
        this.customStructure = structure;
    }

    @Override
    public boolean searchMovedStructure(Level world, BlockPos pos, Direction direction) throws AssemblyException {
        this.anchor = pos;
        if (customStructure == null || customStructure.isEmpty()) {
            return false;
        }

        this.bounds = new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0);

        for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> entry : customStructure.entrySet()) {
            BlockPos currentPos = entry.getKey();
            BlockPos localPos = this.toLocalPos(currentPos);
            
            this.bounds = this.bounds.minmax(new net.minecraft.world.phys.AABB(localPos));

            var capture = this.capture(world, currentPos);
            this.addBlock(world, currentPos, capture);
        }

        // 移除发射器和投掷器的 Actor 行为，阻止 Create 在移动时自动高频触发它们
        this.getActors().removeIf(pair -> {
            net.minecraft.world.level.block.state.BlockState state = pair.getLeft().state();
            return state.is(net.minecraft.world.level.block.Blocks.DISPENSER) || 
                   state.is(net.minecraft.world.level.block.Blocks.DROPPER);
        });
        
        return true;
    }

    @Override
    public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
        if (!searchMovedStructure(world, pos, slideDirection)) {
            return false;
        }
        startMoving(world);
        return true;
    }

    @Override
    public void removeBlocksFromWorld(Level world, BlockPos offset) {
        // 重写父类的移除逻辑，使用静默标志位来防止在组装时触发邻居更新链。
        // 这可以避免发射器、投掷器等红石敏感方块在结构被收纳为实体时被意外激活。
        for (var entry : this.getBlocks().entrySet()) {
            BlockPos localPos = entry.getKey();
            BlockPos worldPos = localPos.offset(this.anchor).offset(offset);

            if (world.getBlockState(worldPos).isAir()) continue;

            // 移除方块实体（如果存在）
            world.removeBlockEntity(worldPos);

            // 使用标志位 2|16|64：
            //   2 = UPDATE_CLIENTS（通知客户端）
            //   16 = UPDATE_MOVE_BY_PISTON（阻止邻居更新）
            //   64 = UPDATE_SUPPRESS_DROPS（阻止掉落物产生）
            world.setBlock(worldPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16 | 64);
        }
    }

    @Override
    public ContraptionType getType() {
        // 使用 GANTRY 暂时欺骗 Create 的注册表，可以保证平移 Contraption 正确反序列化并被客户端渲染
        return AllContraptionTypes.GANTRY.value();
    }
}
