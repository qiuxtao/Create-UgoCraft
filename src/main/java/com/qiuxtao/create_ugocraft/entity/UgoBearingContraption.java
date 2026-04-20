package com.qiuxtao.create_ugocraft.entity;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Map;

public class UgoBearingContraption extends BearingContraption {

    private Map<BlockPos, net.minecraft.world.level.block.state.BlockState> customStructure;
    private Direction facing;

    public UgoBearingContraption() {}

    public UgoBearingContraption(boolean isWindmill, Direction facing) {
        super(isWindmill, facing);
        this.facing = facing;
    }

    public void setCustomStructure(Map<BlockPos, net.minecraft.world.level.block.state.BlockState> structure) {
        this.customStructure = structure;
    }

    @Override
    public boolean searchMovedStructure(Level world, BlockPos pos, Direction direction) throws AssemblyException {
        this.anchor = pos;
        if (customStructure == null || customStructure.isEmpty()) {
            return false;
        }

        this.bounds = new AABB(0, 0, 0, 0, 0, 0);

        for (Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> entry : customStructure.entrySet()) {
            BlockPos currentPos = entry.getKey();
            BlockPos localPos = this.toLocalPos(currentPos);
            
            this.bounds = this.bounds.minmax(new AABB(localPos));

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
        if (!searchMovedStructure(world, pos, facing)) {
            return false;
        }
        startMoving(world);
        return true;
    }

    @Override
    public ContraptionType getType() {
        return AllContraptionTypes.BEARING.value();
    }
}
