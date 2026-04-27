package com.qiuxtao.create_ugocraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
        if (state.getDestroySpeed(level, pos) < 0) return false;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return true;

        return !configData.blacklist.contains(blockId.toString());
    }

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

    public static boolean isAnchoredTo(BlockState neighborState, Direction anchorDir) {
        Block block = neighborState.getBlock();

        if (block instanceof TorchBlock) {
            if (block instanceof WallTorchBlock) {
                return neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() == anchorDir;
            }
            return anchorDir == Direction.DOWN;
        }

        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            AttachFace face = neighborState.getValue(BlockStateProperties.ATTACH_FACE);
            Direction facing = neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (face == AttachFace.CEILING) return anchorDir == Direction.UP;
            if (face == AttachFace.FLOOR) return anchorDir == Direction.DOWN;
            if (face == AttachFace.WALL) return anchorDir == facing.getOpposite();
        }

        if (block instanceof LadderBlock || block instanceof TripWireHookBlock || block instanceof WallSignBlock) {
            return neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() == anchorDir;
        }

        return anchorDir == Direction.DOWN;
    }
}
