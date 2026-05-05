package com.qiuxtao.create_ugocraft.block;

import com.mojang.serialization.MapCodec;
import com.qiuxtao.create_ugocraft.block.entity.SlideCoreBlockEntity;
import com.qiuxtao.create_ugocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Slide Core Block (滑动核心方块)
 * 
 * 
 * 
 * 核心机制：
 * - FACING 方向 = 结构的滑动方向（出口面）
 * - 红石信号从任意非 FACING 面输入来激活
 * - 激活后，从 FACING 方向的前方搜索连通方块组成结构
 * - 激活后，从 FACING 方向的前方搜索连通方块组成结构
 */
public class SlideCoreBlock extends BaseEntityBlock {
    public static final MapCodec<SlideCoreBlock> CODEC = simpleCodec(SlideCoreBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SlideCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放置时朝向玩家看的方向（FACING = 结构滑动出去的方向）
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SLIDE_CORE_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return type == ModBlockEntities.SLIDE_CORE_BE.get() ? (lvl, pos, st, be) -> ((SlideCoreBlockEntity) be).tick() : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // 右键不做任何事情，必须通过红石触发
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SlideCoreBlockEntity core) {
                core.disassembleForBlockRemoval();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);

            // 检测除 FACING 面以外的其他5面是否有红石信号
            boolean hasPower = hasRedstoneSignalExceptFacing(level, pos, facing);
            boolean wasPowered = state.getValue(POWERED);

            if (hasPower != wasPowered) {
                // 通电或断电都触发滑动
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof SlideCoreBlockEntity core) {
                    core.activate(facing, hasPower);
                }
                level.setBlock(pos, state.setValue(POWERED, hasPower), 3);
            }
        }
    }

    /**
            if (dir == facing) continue; // 跳过出口面
     * 这样可以确保 FACING 面前方放置的红石不会误触发。
     */
    private boolean hasRedstoneSignalExceptFacing(Level level, BlockPos pos, Direction facing) {
        for (Direction dir : Direction.values()) {
            if (dir == facing) continue; // 跳过出口面
            BlockPos neighborPos = pos.relative(dir);
            if (level.getSignal(neighborPos, dir) > 0) {
                return true;
            }
        }
        return false;
    }
}
