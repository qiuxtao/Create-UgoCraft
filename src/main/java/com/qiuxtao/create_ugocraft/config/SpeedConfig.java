package com.qiuxtao.create_ugocraft.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 模组配置 - 速度参数
 *
 * 使用 Create 的官方公式将 RPM 转换为实际速度：
 * - 线性速度 (格/tick) = RPM / 512
 * - 旋转速度 (°/tick)  = RPM × 0.3
 */
public class SpeedConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SLIDE_SPEED_RPM;
    public static final ForgeConfigSpec.IntValue ROTATION_SPEED_RPM;

    // 默认值常量，用于配置未就绪时的回退
    private static final int DEFAULT_SLIDE_RPM = 96;
    private static final int DEFAULT_ROTATION_RPM = 16;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Create-UgoCraft Speed Settings",
                        "速度设置，单位为 RPM（与机械动力转速体系对标）")
               .push("speed");

        SLIDE_SPEED_RPM = builder
                .comment("滑动方块的速度（RPM）",
                         "Slide Block speed in RPM.",
                         "Create formula: blocks_per_tick = RPM / 512",
                         "Default: 96 RPM = 0.1875 blocks/tick")
                .defineInRange("slideSpeedRPM", DEFAULT_SLIDE_RPM, 1, 256);

        ROTATION_SPEED_RPM = builder
                .comment("转轴方块的速度（RPM）",
                         "Rotation Block speed in RPM.",
                         "Create formula: degrees_per_tick = RPM * 0.3",
                         "Default: 16 RPM = 4.8 degrees/tick")
                .defineInRange("rotationSpeedRPM", DEFAULT_ROTATION_RPM, 1, 256);

        builder.pop();
        SPEC = builder.build();
    }

    /**
     * 获取滑动速度（格/tick），使用 Create 线性公式：RPM / 512
     */
    public static double getSlideSpeed() {
        try {
            int rpm = SLIDE_SPEED_RPM.get();
            return rpm / 512.0;
        } catch (IllegalStateException e) {
            // 配置尚未加载，使用默认值
            return DEFAULT_SLIDE_RPM / 512.0;
        }
    }

    /**
     * 获取旋转速度（°/tick），使用 Create 旋转公式：RPM × 0.3
     */
    public static double getRotationSpeed() {
        try {
            int rpm = ROTATION_SPEED_RPM.get();
            return rpm * 0.3;
        } catch (IllegalStateException e) {
            // 配置尚未加载，使用默认值
            return DEFAULT_ROTATION_RPM * 0.3;
        }
    }

    /**
     * 在模组主类构造函数中调用此方法注册配置
     */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        LOGGER.info("[Create-UgoCraft] Speed config registered. Defaults: slide={}RPM, rotation={}RPM",
                DEFAULT_SLIDE_RPM, DEFAULT_ROTATION_RPM);
    }
}
