package dev.customportalsfoxified.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CPConfig {

    public static final ModConfigSpec SPEC;

    // PORTAL SETTINGS //

    public static final ModConfigSpec.IntValue MAX_PORTAL_SIZE;
    public static final ModConfigSpec.IntValue BASE_RANGE;
    public static final ModConfigSpec.IntValue ENHANCED_RANGE;
    public static final ModConfigSpec.IntValue STRONG_RANGE;
    public static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION;
    public static final ModConfigSpec.BooleanValue PRIVATE_PORTALS;
    public static final ModConfigSpec.BooleanValue MUTE_SOUNDS;

    // REDSTONE //

    public static final ModConfigSpec.EnumValue<RedstoneMode> REDSTONE_MODE;

    public enum RedstoneMode {
        OFF,
        ON,
        NO_EFFECT
    }

    static {
        var builder = new ModConfigSpec.Builder();

        builder.push("portals");
        MAX_PORTAL_SIZE = builder
                .comment("Maximum number of blocks a portal frame can enclose")
                .defineInRange("maxPortalSize", 64, 4, 900);
        BASE_RANGE = builder
                .comment("Base linking range (no enhancer runes)")
                .defineInRange("baseRange", 100, 1, Integer.MAX_VALUE);
        ENHANCED_RANGE = builder
                .comment("Linking range with weak enhancer runes")
                .defineInRange("enhancedRange", 1000, 1, Integer.MAX_VALUE);
        STRONG_RANGE = builder
                .comment("Linking range with strong enhancer runes")
                .defineInRange("strongRange", 10000, 1, Integer.MAX_VALUE);
        ALLOW_CROSS_DIMENSION = builder
                .comment("Whether gate runes can link portals across dimensions")
                .define("allowCrossDimension", true);
        PRIVATE_PORTALS = builder
                .comment("Only the portal creator can use it")
                .define("privatePortals", false);
        MUTE_SOUNDS = builder
                .comment("Mute portal ambient and teleport sounds")
                .define("muteSounds", false);
        REDSTONE_MODE = builder
                .comment("How redstone interacts with portals: OFF = always active, ON = needs signal, NO_EFFECT = ignored")
                .defineEnum("redstoneMode", RedstoneMode.OFF);
        builder.pop();

        SPEC = builder.build();
    }
}
