package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CreateUgoCraft.MODID);

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
