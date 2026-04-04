package dev.customportalsfoxified.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CPConfig {

  public static final ModConfigSpec CLIENT_SPEC;
  public static final ModConfigSpec COMMON_SPEC;

  // CLIENT: per-player preferences //

  public static final ModConfigSpec.BooleanValue MUTE_SOUNDS;

  // COMMON: server-authoritative gameplay settings //

  public static final ModConfigSpec.IntValue MAX_PORTAL_SIZE;
  public static final ModConfigSpec.IntValue BASE_RANGE;
  public static final ModConfigSpec.IntValue ENHANCED_RANGE;
  public static final ModConfigSpec.IntValue STRONG_RANGE;
  public static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION;
  public static final ModConfigSpec.BooleanValue REDSTONE_DISABLES;

  static {
    // Client spec: settings that only affect the local player
    var client = new ModConfigSpec.Builder();
    MUTE_SOUNDS =
        client.comment("Mute portal ambient and teleport sounds").define("muteSounds", false);
    CLIENT_SPEC = client.build();

    // Common spec: gameplay rules shared between client and server
    var common = new ModConfigSpec.Builder();
    common.push("portals");
    MAX_PORTAL_SIZE =
        common
            .comment("Maximum number of blocks a portal frame can enclose")
            .defineInRange("maxPortalSize", 64, 4, 900);
    BASE_RANGE =
        common
            .comment("Base linking range (no enhancer runes)")
            .defineInRange("baseRange", 100, 1, Integer.MAX_VALUE);
    ENHANCED_RANGE =
        common
            .comment("Linking range with weak enhancer runes")
            .defineInRange("enhancedRange", 1000, 1, Integer.MAX_VALUE);
    STRONG_RANGE =
        common
            .comment("Linking range with strong enhancer runes")
            .defineInRange("strongRange", 10000, 1, Integer.MAX_VALUE);
    ALLOW_CROSS_DIMENSION =
        common
            .comment("Whether gate runes can link portals across dimensions")
            .define("allowCrossDimension", true);
    REDSTONE_DISABLES =
        common
            .comment("When true, a redstone signal turns off adjacent portal blocks")
            .define("redstoneDisables", true);
    common.pop();
    COMMON_SPEC = common.build();
  }
}
