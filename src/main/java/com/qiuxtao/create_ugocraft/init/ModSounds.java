package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateUgoCraft.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_CORE_OPEN = registerSoundEvent("block.slide_core.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_CORE_WARN = registerSoundEvent("block.slide_core.warn");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(CreateUgoCraft.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
