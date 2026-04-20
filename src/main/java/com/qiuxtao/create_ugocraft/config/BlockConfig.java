package com.qiuxtao.create_ugocraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

public class BlockConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("create_ugocraft-blocks.json").toFile();

    private static ConfigData configData = new ConfigData();

    public static class ConfigData {
        public Set<String> blacklist = new HashSet<>(Arrays.asList(
            "minecraft:bedrock",
            "minecraft:obsidian",
            "minecraft:crying_obsidian",
            "minecraft:end_portal_frame",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:barrier",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            // Natural generation blocks commonly excluded to prevent large-scale gathering
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:podzol",
            "minecraft:mycelium",
            "minecraft:sand",
            "minecraft:red_sand",
            "minecraft:gravel",
            "minecraft:stone",
            "minecraft:diorite",
            "minecraft:andesite",
            "minecraft:granite",
            "minecraft:tuff",
            "minecraft:deepslate"
        ));
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                if (loaded != null) {
                    configData = loaded;
                }
            } catch (Exception e) {
                LOGGER.error("[Create-UgoCraft] Failed to load block config file, using defaults.", e);
            }
        }
        save();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(configData, writer);
        } catch (Exception e) {
            LOGGER.error("[Create-UgoCraft] Failed to save block config file.", e);
        }
    }

    public static boolean canMove(Level level, BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        
        // 1. Hardcoded limit: bedrock, etc.
        if (state.getDestroySpeed(level, pos) < 0) return false;

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return true;
        
        String idStr = blockId.toString();

        // 3. Blacklist
        if (configData.blacklist.contains(idStr)) {
            return false;
        }

        return true;
    }

    /**
     * 判断一个方块是否为"脆弱依赖型方块"（如火把、拉杆、红石）。
     */
    public static boolean isFragileAttachedBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof TorchBlock
            || block instanceof FaceAttachedHorizontalDirectionalBlock
            || block instanceof LadderBlock
            || block instanceof TripWireHookBlock
            || block instanceof WallSignBlock
            || block instanceof BasePressurePlateBlock
            || block instanceof RedStoneWireBlock
            || block instanceof DiodeBlock
            || block instanceof DoorBlock
            || block instanceof BedBlock
            || block instanceof BaseRailBlock
            || block instanceof FlowerBlock
            || block instanceof SaplingBlock
            || block instanceof TallGrassBlock
            || block instanceof CarpetBlock
            || block instanceof StandingSignBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock;
    }

    /**
     * 判断脆弱方块（neighborState）是否附着在 anchorDir 方向的面（即它依靠 anchorDir 面存活）。
     * anchorDir 指的是该方块"指向其支撑方块"的方向。
     */
    public static boolean isAnchoredTo(BlockState neighborState, Direction anchorDir) {
        Block block = neighborState.getBlock();

        // 1. 火把类
        if (block instanceof TorchBlock) {
            if (block instanceof WallTorchBlock) {
                return neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() == anchorDir;
            }
            return anchorDir == Direction.DOWN;
        }

        // 2. 贴面方块（按钮、拉杆、砂轮等）
        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            AttachFace face = neighborState.getValue(BlockStateProperties.ATTACH_FACE);
            Direction facing = neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (face == AttachFace.CEILING) return anchorDir == Direction.UP;
            if (face == AttachFace.FLOOR) return anchorDir == Direction.DOWN;
            if (face == AttachFace.WALL) return anchorDir == facing.getOpposite();
        }

        // 3. 墙面附着方块（梯子、绊线钩、墙牌）
        if (block instanceof LadderBlock || block instanceof TripWireHookBlock || block instanceof WallSignBlock) {
            return neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() == anchorDir;
        }

        // 4. 其余所有地面方块（红石粉、压力板、门、铁轨、花草等地表物）
        return anchorDir == Direction.DOWN;
    }
}
