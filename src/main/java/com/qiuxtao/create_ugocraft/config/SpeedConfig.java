package com.qiuxtao.create_ugocraft.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

public class SpeedConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SLIDE_SPEED_RPM;
    public static final ModConfigSpec.IntValue ROTATION_SPEED_RPM;

    private static final int DEFAULT_SLIDE_RPM = 96;
    private static final int DEFAULT_ROTATION_RPM = 16;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Create-UgoCraft Speed Settings").push("speed");

        SLIDE_SPEED_RPM = builder
                .comment("Slide Block speed in RPM.",
                        "Create formula: blocks_per_tick = RPM / 512",
                        "Default: 96 RPM = 0.1875 blocks/tick")
                .defineInRange("slideSpeedRPM", DEFAULT_SLIDE_RPM, 1, 256);

        ROTATION_SPEED_RPM = builder
                .comment("Rotation Block speed in RPM.",
                        "Create formula: degrees_per_tick = RPM * 0.3",
                        "Default: 16 RPM = 4.8 degrees/tick")
                .defineInRange("rotationSpeedRPM", DEFAULT_ROTATION_RPM, 1, 256);

        builder.pop();
        SPEC = builder.build();
    }

    public static double getSlideSpeed() {
        try {
            int rpm = SLIDE_SPEED_RPM.get();
            return rpm / 512.0;
        } catch (IllegalStateException e) {
            return DEFAULT_SLIDE_RPM / 512.0;
        }
    }

    public static double getRotationSpeed() {
        try {
            int rpm = ROTATION_SPEED_RPM.get();
            return rpm * 0.3;
        } catch (IllegalStateException e) {
            return DEFAULT_ROTATION_RPM * 0.3;
        }
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
        LOGGER.info("[Create-UgoCraft] Speed config registered. Defaults: slide={}RPM, rotation={}RPM",
                DEFAULT_SLIDE_RPM, DEFAULT_ROTATION_RPM);
    }
}
