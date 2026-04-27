package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import com.qiuxtao.create_ugocraft.block.MarkerOffBlock;
import com.qiuxtao.create_ugocraft.block.MarkerOnBlock;
import com.qiuxtao.create_ugocraft.block.RotationCoreBlock;
import com.qiuxtao.create_ugocraft.block.SlideCoreBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CreateUgoCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CreateUgoCraft.MODID);

    public static final DeferredHolder<Block, SlideCoreBlock> SLIDE_CORE_BLOCK = registerBlock("slide_core_block",
            () -> new SlideCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredHolder<Block, RotationCoreBlock> ROTATION_CORE_BLOCK = registerBlock("rotation_core_block",
            () -> new RotationCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredHolder<Block, MarkerOnBlock> MARKER_ON_BLOCK = registerBlock("marker_on_block",
            () -> new MarkerOnBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(2.0f, 3.0f)));

    public static final DeferredHolder<Block, MarkerOffBlock> MARKER_OFF_BLOCK = registerBlock("marker_off_block",
            () -> new MarkerOffBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f, 3.0f)));

    private static <T extends Block> DeferredHolder<Block, T> registerBlock(String name, Supplier<T> block) {
        DeferredHolder<Block, T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> DeferredHolder<Item, Item> registerBlockItem(String name, DeferredHolder<Block, T> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
