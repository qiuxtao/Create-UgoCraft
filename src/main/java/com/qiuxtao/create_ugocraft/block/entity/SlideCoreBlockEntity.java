package com.qiuxtao.create_ugocraft.block.entity;

import com.qiuxtao.create_ugocraft.entity.UgoSlideContraption;
import com.qiuxtao.create_ugocraft.init.ModBlockEntities;
import com.qiuxtao.create_ugocraft.init.ModBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import com.qiuxtao.create_ugocraft.collision.StructureCollider;
import com.qiuxtao.create_ugocraft.collision.Matrix3d;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

public class SlideCoreBlockEntity extends BlockEntity implements IControlContraption {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ControlledContraptionEntity movedContraption;
    
    private State state = State.IDLE;
    private double currentProgress = 0.0;
    private double targetDistance = 0.0;
    private Direction slideAxis = null;
    // 动态从配置获取速度
    private BlockPos startOffsetBaseline;
    private double currentDirection = 1.0;
    private transient int settleCooldown = 0;
    private int alignCheckCooldown = 0;
    private static final int ALIGN_CHECK_INTERVAL = 10; // 每10Tick检测一次自动对齐
    private transient int recoveryTicks = 0; // 读档恢复计时器（不持久化）

    // 形状记忆：防止读档后结构捕获紧挨着的障碍物
    private boolean isShapeSaved = false;
    private long[] originalShape = new long[0];
    
    // 不持久化的状态，用于判断卡住音效的播放冷却
    private transient int warnSoundCooldown = 0;

    public enum State {
        IDLE, SLIDING
    }

    public SlideCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SLIDE_CORE_BE.get(), pos, blockState);
    }

    public void activate(Direction facing, boolean poweredOn) {
        if (this.level == null || this.level.isClientSide()) return;
        
        // 【关键防御】如果正处于其它结构的释放阶段（Create 引擎逐块放置时的混沌瞬态），
        // 绝对拒绝由于红石块先行落地诱发的假邻居更新，强行让它走 2 tick 后的全量平稳结构对齐。
        // 这彻底消灭了“有小概率少带动方块”的 Bug！
        if (com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation) return;

        if (state == State.SLIDING) {
            // 中途反向：回到捕获位置（progress=0）
            currentDirection = -currentDirection;
            if (level != null && !level.isClientSide) {
                level.playSound(null, this.worldPosition, com.qiuxtao.create_ugocraft.init.ModSounds.SLIDE_CORE_OPEN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return;
        }

        if (state == State.IDLE) {
            LOGGER.info("[SLIDE_DEBUG] activate() called - fresh start from IDLE. Facing: {}", facing);
            BlockPos baseConnection = this.worldPosition.relative(facing);

            boolean success = assembleAndStart(facing, poweredOn, baseConnection);
            if (success) {
                currentDirection = 1.0;
                LOGGER.info("[SLIDE_DEBUG] assembleAndStart success.");
                if (level != null && !level.isClientSide) {
                    level.playSound(null, this.worldPosition, com.qiuxtao.create_ugocraft.init.ModSounds.SLIDE_CORE_OPEN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            } else {
                LOGGER.info("[SLIDE_DEBUG] assembleAndStart FAILED.");
            }
        }
    }

    private boolean assembleAndStart(Direction facing, boolean poweredOn, BlockPos gatherStart) {
        // baseConnection 是 Core 方块的正前方连接点
        BlockPos baseConnection = this.worldPosition.relative(facing);

        Map<BlockPos, BlockState> structure = gatherStructure(gatherStart);
        if (structure.isEmpty()) return false;

        // ===== 计算真实偏移，以修正标志坐标 =====
        // 当因抛锚或意外崩溃后重启时，结构在世界里可能是整体偏移的
        BlockPos offsetVec = gatherStart.subtract(baseConnection);
        MarkerPair markers = findAlignedMarkerPair(structure, baseConnection, gatherStart, facing.getAxis());
        if (markers == null) {
            LOGGER.info("[SLIDE_DEBUG] assembleAndStart failed: no aligned ON/OFF marker pair.");
            return false;
        }
        BlockPos markerOnPos = markers.onPos;
        BlockPos markerOffPos = markers.offPos;

        // ===== 计算滑动轴和距离 =====
        // 目标：将对应标记移动到 baseConnection 位置
        BlockPos targetMarkerPos = poweredOn ? markerOnPos : markerOffPos;
        // 把实际受偏移的标记减去偏移量，得出它组合初期的相对真实位置
        BlockPos originalTargetMarker = targetMarkerPos.subtract(offsetVec);
        BlockPos slideVec = baseConnection.subtract(originalTargetMarker);

        int dx = slideVec.getX();
        int dy = slideVec.getY();
        int dz = slideVec.getZ();

        Direction computedSlideAxis;
        int totalDistance;
        if (dx != 0 && dy == 0 && dz == 0) {
            computedSlideAxis = dx > 0 ? Direction.EAST : Direction.WEST;
            totalDistance = Math.abs(dx);
        } else if (dy != 0 && dx == 0 && dz == 0) {
            computedSlideAxis = dy > 0 ? Direction.UP : Direction.DOWN;
            totalDistance = Math.abs(dy);
        } else if (dz != 0 && dx == 0 && dy == 0) {
            computedSlideAxis = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            totalDistance = Math.abs(dz);
        } else {
            // 目标标记已在 coreConnection 位置，无需移动
            LOGGER.info("[SLIDE_DEBUG] assembleAndStart failed: target marker is already at coreConnection! slideVec: {}", slideVec);
            return false;
        }

        this.slideAxis = computedSlideAxis;
        this.targetDistance = totalDistance;
        this.currentProgress = 0.0;
        this.settleCooldown = 0;

        LOGGER.info("[SLIDE_DEBUG] computedSlideAxis: {}, totalDistance: {}", computedSlideAxis, totalDistance);

        UgoSlideContraption contraption = new UgoSlideContraption(computedSlideAxis);
        contraption.setCustomStructure(structure);
        
        BlockPos shiftedAnchor = this.worldPosition.offset(offsetVec);
        try {
            if (!contraption.assemble(this.level, shiftedAnchor)) {
                LOGGER.info("[SLIDE_DEBUG] assembleAndStart failed: contraption.assemble returned false");
                return false;
            }
        } catch (AssemblyException e) {
            LOGGER.info("[SLIDE_DEBUG] assembleAndStart failed: AssemblyException: {}", e.getMessage());
            return false;
        }

        // 不再重置 currentProgress 为 0!
        // 如果是全新的一趟位移，currentProgress 早已在 IDLE 态下就是 0
        // 如果是意外中断恢复，必须保留旧进度以便可以向后倒退

        com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = true;
        try {
            contraption.removeBlocksFromWorld(this.level, BlockPos.ZERO);
        } finally {
            com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = false;
        }

        movedContraption = ControlledContraptionEntity.create(this.level, this, contraption);
        movedContraption.setSilent(true);

        // 保存形状记忆，用于复原时拒绝新贴上来的阻挡物
        if (!isShapeSaved) {
            this.originalShape = new long[structure.size()];
            int idx = 0;
            for (BlockPos pos : structure.keySet()) {
                this.originalShape[idx++] = pos.subtract(gatherStart).asLong();
            }
            this.isShapeSaved = true;
            LOGGER.info("[SLIDE_DEBUG] Created new originalShape with {} blocks.", this.originalShape.length);
        } else {
            LOGGER.info("[SLIDE_DEBUG] Reusing existing originalShape.");
        }

        this.state = State.SLIDING;
        updateEntityPosition();
        
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        // 把发实体包放在发方块包之后，最大限度在原生网络层级保证客户端先收到 State.SLIDING，
        // 这样实体在客户端出现寻找 Controller 时就不会被无情拒绝了！
        this.level.addFreshEntity(movedContraption);
        LOGGER.info("[SLIDE_DEBUG] Successfully assembled and spawned movedContraption!");
        
        return true;
    }

    private Direction getDirectionFromDisplacement(BlockPos disp) {
        int ddx = disp.getX(), ddy = disp.getY(), ddz = disp.getZ();
        if (ddx != 0 && ddy == 0 && ddz == 0) return ddx > 0 ? Direction.EAST : Direction.WEST;
        if (ddy != 0 && ddx == 0 && ddz == 0) return ddy > 0 ? Direction.UP : Direction.DOWN;
        if (ddz != 0 && ddx == 0 && ddy == 0) return ddz > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    private void updateEntityPosition() {
        if (movedContraption != null && slideAxis != null) {
            double nx = this.worldPosition.getX() + slideAxis.getStepX() * currentProgress;
            double ny = this.worldPosition.getY() + slideAxis.getStepY() * currentProgress;
            double nz = this.worldPosition.getZ() + slideAxis.getStepZ() * currentProgress;
            movedContraption.setPos(nx, ny, nz);

            // 强制同步实体的底层包围盒，防止 Minecraft 原版 Frustum Culling 在特定视角下将其剔除
            if (movedContraption.getContraption() != null && movedContraption.getContraption().bounds != null) {
                movedContraption.setBoundingBox(movedContraption.getContraption().bounds.move(nx, ny, nz).inflate(1.0));
            }
        }
    }

    public void tick() {
        if (this.level == null) return;

        // 读档恢复：当方块状态不是 IDLE 但控制实体丢失时
        if (state != State.IDLE && movedContraption == null && !level.isClientSide) {
            if (recoveryTicks == 0) {
                LOGGER.info("[SLIDE_DEBUG] Entity missing from level. Starting recovery timeout. currentProgress={}", currentProgress);
            }
            recoveryTicks++;
            if (recoveryTicks > 40) {
                // 实体彻底丢失，完全重置到干净状态。
                // 必须清除 isShapeSaved，因为形状过滤器的偏移量是相对于旧 gatherStart 的，
                // 恢复后用 baseConnection 搜索时偏移量会全部对不上，导致所有方块被拒绝！
                state = State.IDLE;
                currentProgress = 0;
                currentDirection = 1.0;
                LOGGER.info("[SLIDE_DEBUG] Entity recovery timeout (40 ticks). Reset to IDLE while keeping saved shape.");
                recoveryTicks = 0;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return;
        }
        if (movedContraption != null) {
            recoveryTicks = 0;
        }

        // 自动对齐检测：当空闲时周期性检查结构是否需要对齐
        if (state == State.IDLE && !level.isClientSide) {
            if (com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation) {
                return;
            }
            if (settleCooldown > 0) {
                settleCooldown--;
                return;
            }
            if (alignCheckCooldown <= 0) {
                alignCheckCooldown = ALIGN_CHECK_INTERVAL;
                tryAutoAlign();
            } else {
                alignCheckCooldown--;
            }
        }

        if (state == State.SLIDING && movedContraption != null) {
            Vec3 oldPos = movedContraption.position();
            double currentSpeed = com.qiuxtao.create_ugocraft.config.SpeedConfig.getSlideSpeed();
            double nextProgress = currentProgress + currentDirection * currentSpeed;

            // 提前截断，防止因为 speed 步长叠加导致侦测到目标终点外紧贴着的方块
            if (currentDirection > 0 && nextProgress > targetDistance) {
                nextProgress = targetDistance;
            } else if (currentDirection < 0 && nextProgress < 0) {
                nextProgress = 0;
            }

            // 障碍物检测：如果前方有方块阻挡，停在原地悬停等待挖掘（符合 Ugocraft 逻辑）
            if (checkForObstruction(nextProgress)) {
                if (warnSoundCooldown <= 0) {
                    level.playSound(null, this.worldPosition, com.qiuxtao.create_ugocraft.init.ModSounds.SLIDE_CORE_WARN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    warnSoundCooldown = 40; // 40 ticks = 2 秒间隔
                } else {
                    warnSoundCooldown--;
                }
                // 即使悬停，也要持续更新实体位置和碰撞，以防止客户端不同步或Culling缩小
                updateEntityPosition();
                collideWithPlayers(oldPos);
                return;
            } else {
                warnSoundCooldown = 0;
            }

            currentProgress = nextProgress;
            
            if (currentDirection > 0 && currentProgress >= targetDistance) {
                LOGGER.info("[SLIDE_DEBUG] Reached target! Disassembling. targetDistance={}", targetDistance);
                currentProgress = targetDistance;
                updateEntityPosition();
                collideWithPlayers(oldPos);
                disassemble();
            } else if (currentDirection < 0 && currentProgress <= 0) {
                LOGGER.info("[SLIDE_DEBUG] Reached origin! Disassembling. currentProgress <= 0");
                currentProgress = 0;
                updateEntityPosition();
                collideWithPlayers(oldPos);
                disassemble();
            } else {
                updateEntityPosition();
                collideWithPlayers(oldPos);
            }
        }
    }

    /**
     * 自动对齐检测：检查结构是否存在且未对齐，如果需要则触发滑动。
     * 当 Core + OFF + ON 构成有效结构但目标标记未在 Core 连接点时，
     * 根据当前红石状态自动开始位移。
     */
    private void tryAutoAlign() {
        if (com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation) return;

        BlockState coreState = getBlockState();
        if (!coreState.hasProperty(BlockStateProperties.FACING)) return;

        Direction facing = coreState.getValue(BlockStateProperties.FACING);

        // 直接检测实际红石信号，而非依赖 POWERED 属性（在刚放置时可能还没更新）
        boolean powered = false;
        for (Direction dir : Direction.values()) {
            if (dir == facing) continue;
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getSignal(neighborPos, dir) > 0) {
                powered = true;
                break;
            }
        }

        // 同步 POWERED 属性
        if (coreState.hasProperty(BlockStateProperties.POWERED)
                && coreState.getValue(BlockStateProperties.POWERED) != powered) {
            level.setBlock(worldPosition, coreState.setValue(BlockStateProperties.POWERED, powered), 3);
        }

        BlockPos baseConnection = this.worldPosition.relative(facing);
        BlockPos gatherStart = baseConnection;

        Map<BlockPos, BlockState> structure = gatherStructure(baseConnection);
        MarkerPair markers = findAlignedMarkerPair(structure, baseConnection, gatherStart, facing.getAxis());
        
        // 如果 baseConnection 找不到结构，说明结构可能在偏移位置（实体恢复失败后被放到了中途）。
        // 此时沿 slideAxis 逐格扫描，尝试定位远处的结构。
        // 注意：只有 baseConnection 为空时才扫描，防止斜对角抓取 bug！
        if ((structure.isEmpty() || markers == null) && slideAxis != null && targetDistance > 0) {
            int maxScan = (int) Math.ceil(targetDistance);
            for (int scan = 1; scan <= maxScan; scan++) {
                BlockPos scanPos = baseConnection.offset(
                        slideAxis.getStepX() * scan,
                        slideAxis.getStepY() * scan,
                        slideAxis.getStepZ() * scan
                );
                Map<BlockPos, BlockState> scanStructure = gatherStructure(scanPos);
                MarkerPair scanMarkers = findAlignedMarkerPair(scanStructure, baseConnection, scanPos, facing.getAxis());
                if (scanMarkers != null) {
                    structure = scanStructure;
                    gatherStart = scanPos;
                    markers = scanMarkers;
                    LOGGER.info("[SLIDE_DEBUG] tryAutoAlign: found structure at offset={} along slideAxis", scan);
                    break;
                }
            }
        }
        
        if (structure.isEmpty() || markers == null) {
            return;
        }

        BlockPos markerOnPos = markers.onPos;
        BlockPos markerOffPos = markers.offPos;

        // 检查目标标记是否已在 Core 连接点（已对齐则无需移动）
        BlockPos targetMarkerPos = powered ? markerOnPos : markerOffPos;
        if (targetMarkerPos.equals(baseConnection)) {
            LOGGER.info("[SLIDE_DEBUG] targetMarkerPos equals baseConnection. Auto align not needed.");
            currentProgress = 0;
            return;
        }

        // 结构存在但未对齐 -> 自动触发滑动
        LOGGER.info("[SLIDE_DEBUG] Auto-align triggering assembleAndStart! gatherStart: {}, targetMarkerPos: {}", gatherStart, targetMarkerPos);
        boolean success = assembleAndStart(facing, powered, gatherStart);
        if (success) {
            currentDirection = 1.0;
            LOGGER.info("[SLIDE_DEBUG] Auto-align assembleAndStart SUCCESS.");
            if (level != null && !level.isClientSide) {
                level.playSound(null, this.worldPosition, com.qiuxtao.create_ugocraft.init.ModSounds.SLIDE_CORE_OPEN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        } else {
            LOGGER.info("[SLIDE_DEBUG] Auto-align assembleAndStart FAILED.");
        }
    }

    private boolean checkForObstruction(double nextProgress) {
        if (movedContraption == null || movedContraption.getContraption() == null) return false;
        if (level == null || slideAxis == null) return false;

        double t = nextProgress;
        int grid1 = (int) Math.floor(t);
        boolean hasFraction = (t - grid1) > 1e-5;
        int grid2 = grid1 + 1;

        int[] gridsToCheck = hasFraction ? new int[]{grid1, grid2} : new int[]{grid1};

        for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry :
                movedContraption.getContraption().getBlocks().entrySet()) {
            BlockPos localPos = entry.getKey();
            
            // T=0 时方块在世界中的相对本地方块位置转换为绝对世界坐标
            // localPos 是相对于 corePosition 的偏移
            BlockPos basePos = this.worldPosition.offset(localPos);

            for (int objGrid : gridsToCheck) {
                BlockPos targetPos = basePos.offset(
                        slideAxis.getStepX() * objGrid,
                        slideAxis.getStepY() * objGrid,
                        slideAxis.getStepZ() * objGrid
                );

                // 移除忽略核心的逻辑，因为如果结构移动撞向它自己相连的核心，理应被核心这个实体实体方块阻挡！

                BlockState existing = level.getBlockState(targetPos);
                // 非空气、非流体的固体方块视为障碍
                if (!existing.isAir() && !existing.liquid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void collideWithPlayers(Vec3 oldPos) {
        if (movedContraption == null || movedContraption.getContraption() == null) return;
        Vec3 newPos = movedContraption.position();
        Vec3 motion = newPos.subtract(oldPos);
        
        if (motion.equals(Vec3.ZERO)) return;

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : movedContraption.getContraption().getBlocks().entrySet()) {
            blocks.put(entry.getKey(), entry.getValue().state());
        }

        StructureCollider.collideEntities(movedContraption, blocks, newPos, new Matrix3d().asIdentity(), motion);
    }

    @Nullable
    private MarkerPair findAlignedMarkerPair(Map<BlockPos, BlockState> structure, BlockPos baseConnection, BlockPos gatherStart, Direction.Axis blockedAxis) {
        BlockPos offsetVec = gatherStart.subtract(baseConnection);
        List<BlockPos> onMarkers = new ArrayList<>();
        List<BlockPos> offMarkers = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockState> entry : structure.entrySet()) {
            if (entry.getValue().is(ModBlocks.MARKER_ON_BLOCK.get())) {
                onMarkers.add(entry.getKey());
            } else if (entry.getValue().is(ModBlocks.MARKER_OFF_BLOCK.get())) {
                offMarkers.add(entry.getKey());
            }
        }

        MarkerPair best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos onPos : onMarkers) {
            for (BlockPos offPos : offMarkers) {
                BlockPos originalOn = onPos.subtract(offsetVec);
                BlockPos originalOff = offPos.subtract(offsetVec);
                Direction.Axis axis = commonLineAxis(baseConnection, originalOn, originalOff);
                if (axis == null) continue;
                if (axis == blockedAxis) continue;

                double score = originalOn.distSqr(baseConnection) + originalOff.distSqr(baseConnection);
                if (score < bestScore) {
                    bestScore = score;
                    best = new MarkerPair(onPos, offPos);
                }
            }
        }
        return best;
    }

    @Nullable
    private Direction.Axis commonLineAxis(BlockPos anchor, BlockPos a, BlockPos b) {
        Direction.Axis axisA = axisFrom(anchor, a);
        Direction.Axis axisB = axisFrom(anchor, b);
        boolean aAtAnchor = a.equals(anchor);
        boolean bAtAnchor = b.equals(anchor);

        if (axisA == null && !aAtAnchor) return null;
        if (axisB == null && !bAtAnchor) return null;
        if (aAtAnchor && bAtAnchor) return null;
        if (aAtAnchor) return axisB;
        if (bAtAnchor) return axisA;
        return axisA == axisB ? axisA : null;
    }

    @Nullable
    private Direction.Axis axisFrom(BlockPos anchor, BlockPos pos) {
        int dx = pos.getX() - anchor.getX();
        int dy = pos.getY() - anchor.getY();
        int dz = pos.getZ() - anchor.getZ();
        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (axes != 1) return null;
        if (dx != 0) return Direction.Axis.X;
        if (dy != 0) return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    private static final class MarkerPair {
        private final BlockPos onPos;
        private final BlockPos offPos;

        private MarkerPair(BlockPos onPos, BlockPos offPos) {
            this.onPos = onPos;
            this.offPos = offPos;
        }
    }

    private void disassemble() {
        if (!level.isClientSide && movedContraption != null) {
            com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = true;
            try {
                movedContraption.disassemble();
            } finally {
                // 我们不能立刻在 finally 里关闭拦截，因为 Create 的逐个放方块操作极有可能把方块更新塞进这一 tick 末尾的队列里去。
                // 延迟 1 tick 再摘掉发射器的护盾，确保这一瞬间所有方块都已经落定、红石状态均已稳定！
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
        this.isShapeSaved = false; // 用户可以给结构添加新的方块了
        this.currentProgress = 0;
        this.currentDirection = 1.0;
        this.targetDistance = 0;
        this.slideAxis = null;
        this.settleCooldown = 6;
        this.alignCheckCooldown = ALIGN_CHECK_INTERVAL;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private Map<BlockPos, BlockState> gatherStructure(BlockPos start) {
        Map<BlockPos, BlockState> structure = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        
        Set<Long> allowedOffsets = new HashSet<>();
        if (isShapeSaved && originalShape != null) {
            for (long offset : originalShape) {
                allowedOffsets.add(offset);
            }
        }

        if (isGatherableBlock(start)) {
            queue.add(start);
            visited.add(start);
        }

        int max = 1024;
        while (!queue.isEmpty() && structure.size() < max) {
            BlockPos current = queue.poll();
            
            if (isShapeSaved && !allowedOffsets.isEmpty()) {
                long offset = current.subtract(start).asLong();
                if (!allowedOffsets.contains(offset)) {
                    continue; // 拒绝非原始形状的方块（即路上挨着的障碍物）
                }
            }
            
            BlockState blockState = this.level.getBlockState(current);

            structure.put(current, blockState);

            // 【依附方块机制】：如果当前方块是火把、拉杆等脆弱物，它已经成功被附着面抓取了。
            // 此时，制止它继续向周围的 6 个面乱抓（拉杆没有爪子去抓它的邻居）。
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
                if (neighbor.equals(this.worldPosition)) continue; // 跳过当前正在发功的主核心本身
                
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

                    // 如果尝试抓取的邻居是一个“脆弱依附方块”，只有当 `current` 是它的附着面时才能抓取！
                    if (com.qiuxtao.create_ugocraft.config.BlockConfig.isFragileAttachedBlock(neighborState)) {
                        // dir 是从 current 指向 neighbor。那么邻居看向 current 的方向就是 dir.getOpposite()
                        if (!com.qiuxtao.create_ugocraft.config.BlockConfig.isAnchoredTo(neighborState, dir.getOpposite())) {
                            continue; // 邻居并不挂在我们身上，放过它。
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
        tag.putDouble("CurrentProgress", currentProgress);
        tag.putDouble("TargetDistance", targetDistance);
        if (slideAxis != null) {
            tag.putInt("SlideAxis", slideAxis.get3DDataValue());
        }
        tag.putDouble("CurrentDirection", currentDirection);
        
        tag.putBoolean("IsShapeSaved", isShapeSaved);
        if (isShapeSaved && originalShape != null) {
            tag.putLongArray("OriginalShape", originalShape);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("State")) {
            this.state = State.values()[tag.getInt("State")];
        }
        this.currentProgress = tag.getDouble("CurrentProgress");
        this.targetDistance = tag.getDouble("TargetDistance");
        if (tag.contains("SlideAxis")) {
            this.slideAxis = Direction.from3DDataValue(tag.getInt("SlideAxis"));
        }
        if (tag.contains("CurrentDirection")) {
            this.currentDirection = tag.getDouble("CurrentDirection");
        }
        this.isShapeSaved = tag.getBoolean("IsShapeSaved");
        if (isShapeSaved && tag.contains("OriginalShape")) {
            this.originalShape = tag.getLongArray("OriginalShape");
        }
        
        // 当从 NBT 加载（如刚才由 Contraption 还原到世界）时，推迟 2 tick 再进行全量检查。
        // 因为这段时间内容易发生方块没全部落地导致的组装失败，避免失败后陷入漫长的 10 tick 冷却。
        this.alignCheckCooldown = 2;
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

    public void disassembleForBlockRemoval() {
        if (level != null && !level.isClientSide && movedContraption != null) {
            ControlledContraptionEntity contraption = movedContraption;
            movedContraption = null;
            com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = true;
            try {
                if (contraption.isAlive()) {
                    contraption.disassemble();
                    contraption.discard();
                }
            } finally {
                com.qiuxtao.create_ugocraft.CreateUgoCraft.suppressDispenserActivation = false;
            }
        }

        this.state = State.IDLE;
        this.isShapeSaved = false;
        this.currentProgress = 0;
        this.currentDirection = 1.0;
        this.targetDistance = 0;
        this.slideAxis = null;
        setChanged();
    }

    @Override
    public void setRemoved() {
        movedContraption = null;
        super.setRemoved();
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        if (!(contraption instanceof ControlledContraptionEntity)) return false;
        
        // 彻底移除 if (this.state == State.IDLE) return false; 
        // 拥抱更宽容的握手机制：允许客户端处于 IDLE 时也接纳实体，防止发包顺序导致的客户端实体不跟随
        if (this.movedContraption != contraption) {
            this.movedContraption = (ControlledContraptionEntity) contraption;
            this.movedContraption.setSilent(true);
            setChanged();
        }
        return true;
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        this.movedContraption = contraption;
        this.movedContraption.setSilent(true);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onStall() {
        if (!level.isClientSide) {
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
