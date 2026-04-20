package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import com.qiuxtao.create_ugocraft.block.entity.SlideCoreBlockEntity;
import com.qiuxtao.create_ugocraft.block.entity.RotationCoreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CreateUgoCraft.MODID);

    public static final RegistryObject<BlockEntityType<SlideCoreBlockEntity>> SLIDE_CORE_BE =
            BLOCK_ENTITIES.register("slide_core", () ->
                    BlockEntityType.Builder.of(SlideCoreBlockEntity::new, ModBlocks.SLIDE_CORE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<RotationCoreBlockEntity>> ROTATION_CORE_BE =
            BLOCK_ENTITIES.register("rotation_core", () ->
                    BlockEntityType.Builder.of(RotationCoreBlockEntity::new, ModBlocks.ROTATION_CORE_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
