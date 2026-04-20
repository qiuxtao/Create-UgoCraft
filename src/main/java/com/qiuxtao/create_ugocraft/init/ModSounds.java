package com.qiuxtao.create_ugocraft.init;

import com.qiuxtao.create_ugocraft.CreateUgoCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CreateUgoCraft.MODID);

    public static final RegistryObject<SoundEvent> SLIDE_CORE_OPEN = registerSoundEvent("block.slide_core.open");
    public static final RegistryObject<SoundEvent> SLIDE_CORE_WARN = registerSoundEvent("block.slide_core.warn");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(CreateUgoCraft.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
