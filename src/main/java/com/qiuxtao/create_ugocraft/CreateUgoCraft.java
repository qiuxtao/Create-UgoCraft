package com.qiuxtao.create_ugocraft;

import com.mojang.logging.LogUtils;
import com.qiuxtao.create_ugocraft.init.ModBlocks;
import com.qiuxtao.create_ugocraft.init.ModBlockEntities;
import com.qiuxtao.create_ugocraft.init.ModEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

@Mod(CreateUgoCraft.MODID)
public class CreateUgoCraft {
    public static final String MODID = "create_ugocraft";
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 全局阻断发射器状态拦截器，用于结构拆除释放阶段
    public static volatile boolean suppressDispenserActivation = false;

    public CreateUgoCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        com.qiuxtao.create_ugocraft.init.ModSounds.register(modEventBus);

        // 注册速度配置
        com.qiuxtao.create_ugocraft.config.SpeedConfig.register();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.SLIDE_CORE_BLOCK);
            event.accept(ModBlocks.ROTATION_CORE_BLOCK);
            event.accept(ModBlocks.MARKER_ON_BLOCK);
            event.accept(ModBlocks.MARKER_OFF_BLOCK);
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
