package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import com.qiuxtao.create_ugocraft.block.SlideCoreBlock;
import com.qiuxtao.create_ugocraft.block.RotationCoreBlock;
import com.qiuxtao.create_ugocraft.block.MarkerOnBlock;
import com.qiuxtao.create_ugocraft.block.MarkerOffBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CreateUgoCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CreateUgoCraft.MODID);

    public static final RegistryObject<Block> SLIDE_CORE_BLOCK = registerBlock("slide_core_block",
            () -> new SlideCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> ROTATION_CORE_BLOCK = registerBlock("rotation_core_block",
            () -> new RotationCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion()));


    public static final RegistryObject<Block> MARKER_ON_BLOCK = registerBlock("marker_on_block",
            () -> new MarkerOnBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(2.0f, 3.0f)));

    public static final RegistryObject<Block> MARKER_OFF_BLOCK = registerBlock("marker_off_block",
            () -> new MarkerOffBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f, 3.0f)));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
