package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import com.qiuxtao.create_ugocraft.block.entity.RotationCoreBlockEntity;
import com.qiuxtao.create_ugocraft.block.entity.SlideCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateUgoCraft.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SlideCoreBlockEntity>> SLIDE_CORE_BE =
            BLOCK_ENTITIES.register("slide_core", () ->
                    BlockEntityType.Builder.of(SlideCoreBlockEntity::new, ModBlocks.SLIDE_CORE_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RotationCoreBlockEntity>> ROTATION_CORE_BE =
            BLOCK_ENTITIES.register("rotation_core", () ->
                    BlockEntityType.Builder.of(RotationCoreBlockEntity::new, ModBlocks.ROTATION_CORE_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
