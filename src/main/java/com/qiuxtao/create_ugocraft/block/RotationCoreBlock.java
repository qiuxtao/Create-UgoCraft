package com.qiuxtao.create_ugocraft.block;

import com.qiuxtao.create_ugocraft.block.entity.RotationCoreBlockEntity;
import com.qiuxtao.create_ugocraft.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
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

public class RotationCoreBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public RotationCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.ROTATION_CORE_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return type == ModBlockEntities.ROTATION_CORE_BE.get() ? (lvl, pos, st, be) -> ((RotationCoreBlockEntity) be).tick() : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);

            boolean hasPower = hasRedstoneSignalExceptFacing(level, pos, facing);
            boolean wasPowered = state.getValue(POWERED);

            if (hasPower != wasPowered) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof RotationCoreBlockEntity core) {
                    core.activate(facing, hasPower);
                }
                level.setBlock(pos, state.setValue(POWERED, hasPower), 3);
            }
        }
    }

    private boolean hasRedstoneSignalExceptFacing(Level level, BlockPos pos, Direction facing) {
        for (Direction dir : Direction.values()) {
            if (dir == facing) continue;
            BlockPos neighborPos = pos.relative(dir);
            if (level.getSignal(neighborPos, dir) > 0) {
                return true;
            }
        }
        return false;
    }
}
