package com.qiuxtao.create_ugocraft.block.entity;

import com.qiuxtao.create_ugocraft.block.RotationCoreBlock;
import com.qiuxtao.create_ugocraft.init.ModBlockEntities;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.*;

public class RotationCoreBlockEntity extends BlockEntity implements IControlContraption {

    private ControlledContraptionEntity movedContraption;
    
    private State state = State.IDLE;
    private float currentAngle = 0;
    private float returnTargetAngle = 0;
    // 动态从配置获取旋转速度
    private float currentDirection = 1.0f; // 1 为正向，-1 为反向
    private int autoStartCooldown = 0;
    private static final int AUTO_START_INTERVAL = 10; // 每10Tick检测一次自动启动
    private transient int recoveryTicks = 0; // 读档恢复计时器（不持久化）

    public enum State {
        IDLE, ROTATING, RETURNING
    }

    public RotationCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ROTATION_CORE_BE.get(), pos, blockState);
    }

    public void activate(Direction facing, boolean poweredOn) {
        if (this.level == null || this.level.isClientSide()) return;
        
        if (com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation) return;

        if (poweredOn) {
            if (state == State.IDLE) {
                assembleAndStart(facing);
            } else if (state == State.RETURNING) {
                // 如果在归位途中再次拉下开关，继续转动
                state = State.ROTATING;
            }
        } else {
            if (state == State.ROTATING) {
                triggerReturnToZero();
            }
        }
    }

    private void assembleAndStart(Direction facing) {
        BlockPos startPos = this.worldPosition.relative(facing);
        java.util.Map<BlockPos, BlockState> structure = gatherStructure(startPos);
        if (structure.isEmpty()) return;

        com.qiuxtao.create_ugocraft.entity.UgoBearingContraption contraption = new com.qiuxtao.create_ugocraft.entity.UgoBearingContraption(false, facing);
        contraption.setCustomStructure(structure);
        try {
            if (!contraption.assemble(this.level, this.worldPosition)) {
                return;
            }
        } catch (AssemblyException e) {
            return;
        }

        com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = true;
        try {
            contraption.removeBlocksFromWorld(this.level, BlockPos.ZERO);
        } finally {
            com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = false;
        }
        movedContraption = ControlledContraptionEntity.create(this.level, this, contraption);
        
        BlockPos anchor = this.worldPosition.relative(facing);
        movedContraption.setPos(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        // Create 内部会修正 Contraption 坐标偏移 (Bearing 是块中心还是边界，需要通过 setPos 设置为方块中心)
        movedContraption.setPos(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        
        movedContraption.setRotationAxis(facing.getAxis());
        this.state = State.ROTATING;
        this.currentAngle = 0;
        this.currentDirection = 1.0f;
        
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        this.level.addFreshEntity(movedContraption);
    }

    private void triggerReturnToZero() {
        // 计算退回逻辑：
        // 如果当前是向正向转了 angle 度，我们需要退回到小于 angle 的最近 360 整数倍。
        if (currentDirection > 0) {
            returnTargetAngle = (float) Math.floor(currentAngle / 360.0) * 360f;
        } else {
            returnTargetAngle = (float) Math.ceil(currentAngle / 360.0) * 360f;
        }
        
        this.state = State.RETURNING;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void tick() {
        if (this.level == null) return;

        // 读档恢复：当方块状态不是 IDLE 但控制实体丢失时
        // 不主动搜索实体！Create 的 tickContraption() 会自动回调 isAttachedTo() 重连
        if (state != State.IDLE && movedContraption == null && !level.isClientSide) {
            recoveryTicks++;
            if (recoveryTicks > 40) {
                state = State.IDLE;
                currentAngle = 0;
                recoveryTicks = 0;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return;
        }
        if (movedContraption != null) {
            recoveryTicks = 0;
        }

        // 自动启动检测：当空闲时检查是否应该开始旋转
        if (state == State.IDLE && !level.isClientSide) {
            if (autoStartCooldown <= 0) {
                autoStartCooldown = AUTO_START_INTERVAL;
                tryAutoStart();
            } else {
                autoStartCooldown--;
            }
        }

        if (state == State.ROTATING) {
            currentAngle += currentDirection * (float) com.qiuxtao.create_ugocraft.config.SpeedConfig.getRotationSpeed();
            applyRotation();
        } else if (state == State.RETURNING) {
            float diff = currentAngle - returnTargetAngle;
            float step = (float) com.qiuxtao.create_ugocraft.config.SpeedConfig.getRotationSpeed();

            if (Math.abs(diff) <= step) {
                currentAngle = returnTargetAngle;
                applyRotation();
                disassemble();
            } else {
                currentAngle -= Math.signum(diff) * step;
                applyRotation();
            }
        }
    }

    /**
     * 自动启动检测：如果核心处于 IDLE 且有红石供电，自动开始旋转。
     * 解决先放红石再放核心时不触发旋转的问题。
     */
    private void tryAutoStart() {
        BlockState coreState = getBlockState();
        if (!coreState.hasProperty(BlockStateProperties.FACING)) return;

        Direction facing = coreState.getValue(BlockStateProperties.FACING);

        // 直接检测实际红石信号，而非依赖 POWERED 属性（刚放置时可能还没更新）
        boolean hasPower = false;
        for (Direction dir : Direction.values()) {
            if (dir == facing) continue;
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getSignal(neighborPos, dir) > 0) {
                hasPower = true;
                break;
            }
        }
        if (!hasPower) return;

        // 同步 POWERED 属性
        if (coreState.hasProperty(BlockStateProperties.POWERED)
                && !coreState.getValue(BlockStateProperties.POWERED)) {
            level.setBlock(worldPosition, coreState.setValue(BlockStateProperties.POWERED, true), 3);
        }

        // 检查是否有可组装的结构
        BlockPos startPos = this.worldPosition.relative(facing);
        if (level.getBlockState(startPos).isAir()) return;

        // 有电且有结构，自动启动旋转
        assembleAndStart(facing);
    }

    private void applyRotation() {
        if (movedContraption != null) {
            movedContraption.setAngle(currentAngle);
        }
    }

    private void disassemble() {
        if (!level.isClientSide && movedContraption != null) {
            com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = true;
            try {
                movedContraption.disassemble();
            } finally {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
                        com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = false;
                    }));
                } else {
                    com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = false;
                }
            }
            movedContraption = null;
        }
        this.state = State.IDLE;
        this.currentAngle = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private Map<BlockPos, BlockState> gatherStructure(BlockPos start) {
        Map<BlockPos, BlockState> structure = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        if (isGatherableBlock(start)) {
            queue.add(start);
            visited.add(start);
        }

        int max = 1024;
        while (!queue.isEmpty() && structure.size() < max) {
            BlockPos current = queue.poll();
            BlockState blockState = this.level.getBlockState(current);

            structure.put(current, blockState);

            // 【依附方块机制】：如果当前方块是火把、拉杆等脆弱物，它自身不再往外蔓延。
            if (com.qiuxtao.create_ugocraft.config.BlockConfig.isFragileAttachedBlock(blockState)) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                // 【核心引擎正面绝缘机制】：如果当前处于其他自带的核心方块（比如附着在车上的副引擎），
                // 这个副引擎的正前方（也就是它的输出面）绝对不可以被蔓延粘连，否则它就会把它该推出去的轨道给彻底黏住。
                if (blockState.is(com.qiuxtao.create_ugocraft.init.ModBlocks.SLIDE_CORE_BLOCK.get()) || 
                    blockState.is(com.qiuxtao.create_ugocraft.init.ModBlocks.ROTATION_CORE_BLOCK.get())) {
                    if (blockState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) &&
                        dir == blockState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                        continue;
                    }
                }

                BlockPos neighbor = current.relative(dir);
                if (visited.contains(neighbor)) continue;
                if (neighbor.equals(this.worldPosition)) continue; // 跳过核心本身
                
                if (isGatherableBlock(neighbor)) {
                    BlockState neighborState = this.level.getBlockState(neighbor);
                    
                    // 【反向绝缘】：如果试图蔓延的邻居是一个副引擎，且我们恰好是触碰在它的正门输出面上，也不能去抓取它！
                    if (neighborState.is(com.qiuxtao.create_ugocraft.init.ModBlocks.SLIDE_CORE_BLOCK.get()) || 
                        neighborState.is(com.qiuxtao.create_ugocraft.init.ModBlocks.ROTATION_CORE_BLOCK.get())) {
                        if (neighborState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) &&
                            dir.getOpposite() == neighborState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                            continue;
                        }
                    }

                    // 仅当 current 是依附类方块的支撑面时才将其打包
                    if (com.qiuxtao.create_ugocraft.config.BlockConfig.isFragileAttachedBlock(neighborState)) {
                        if (!com.qiuxtao.create_ugocraft.config.BlockConfig.isAnchoredTo(neighborState, dir.getOpposite())) {
                            continue;
                        }
                    }

                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return structure;
    }

    private boolean isGatherableBlock(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        return com.qiuxtao.create_ugocraft.config.BlockConfig.canMove(this.level, state, pos);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("State", state.ordinal());
        tag.putFloat("CurrentAngle", currentAngle);
        tag.putFloat("ReturnTargetAngle", returnTargetAngle);
        tag.putFloat("CurrentDirection", currentDirection);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("State")) {
            this.state = State.values()[tag.getInt("State")];
        }
        this.currentAngle = tag.getFloat("CurrentAngle");
        this.returnTargetAngle = tag.getFloat("ReturnTargetAngle");
        if (tag.contains("CurrentDirection")) {
            this.currentDirection = tag.getFloat("CurrentDirection");
        }
        
        // 刚落地时延迟 2 tick 检查
        this.autoStartCooldown = 2;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    // --- IControlContraption 实现 ---

    @Override
    public void setRemoved() {
        // 核心方块被破坏时，立即将运动中的结构解体回世界
        if (level != null && !level.isClientSide && movedContraption != null && movedContraption.isAlive()) {
            movedContraption.disassemble();
            movedContraption.discard();
            movedContraption = null;
            this.state = State.IDLE;
            this.currentAngle = 0;
        }
        super.setRemoved();
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        if (!(contraption instanceof ControlledContraptionEntity)) return false;
        
        if (this.movedContraption != contraption) {
            this.movedContraption = (ControlledContraptionEntity) contraption;
            setChanged();
        }
        return true;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        this.movedContraption = contraption;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onStall() {
        // 当结构碰撞卡死时触发，这里可根据需求停止旋转或拆解
        if (!level.isClientSide) {
            triggerReturnToZero();
        }
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }
}
