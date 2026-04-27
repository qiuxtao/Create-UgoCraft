package com.qiuxtao.create_ugocraft;

import com.mojang.logging.LogUtils;
import com.qiuxtao.create_ugocraft.init.ModBlockEntities;
import com.qiuxtao.create_ugocraft.init.ModBlocks;
import com.qiuxtao.create_ugocraft.init.ModEntities;
import com.qiuxtao.create_ugocraft.init.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(CreateUgoCraft.MODID)
public class CreateUgoCraft {
    public static final String MODID = "create_ugocraft";
    public static volatile boolean suppressDispenserActivation = false;

    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateUgoCraft(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);

        com.qiuxtao.create_ugocraft.config.SpeedConfig.register(modContainer);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        NeoForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.SLIDE_CORE_BLOCK.get());
            event.accept(ModBlocks.ROTATION_CORE_BLOCK.get());
            event.accept(ModBlocks.MARKER_ON_BLOCK.get());
            event.accept(ModBlocks.MARKER_OFF_BLOCK.get());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Create-UgoCraft] Common setup initialized.");
        com.qiuxtao.create_ugocraft.config.BlockConfig.load();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Create-UgoCraft] Server starting.");
    }
}
