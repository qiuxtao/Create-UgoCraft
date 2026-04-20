package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CreateUgoCraft.MODID);

    // 旧版自研实体已移除，当前使用 Create 的 ControlledContraptionEntity
    // 如需注册新的自定义实体，在此添加

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
