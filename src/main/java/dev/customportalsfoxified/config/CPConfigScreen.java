package dev.customportalsfoxified.config;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import xyz.kwahson.core.config.KwahsConfigScreen;

public final class CPConfigScreen {

  private CPConfigScreen() {}

  /** Called from mod constructor, guarded by dist check. Keeps IConfigScreenFactory
   *  out of the main mod class so the server never tries to load Screen. */
  public static void register(ModContainer modContainer) {
    modContainer.registerExtensionPoint(IConfigScreenFactory.class,
        (mc, parent) -> create(parent));
  }

  public static Screen create(Screen parent) {
    return KwahsConfigScreen.builder("Custom Portals Foxified", parent,
            CPConfig.CLIENT_SPEC, CPConfig.COMMON_SPEC)
        .tab("Settings", tab -> {
          tab.sections("Sound", "Redstone");
          tab.left(tab.toggle("Mute Sounds", CPConfig.MUTE_SOUNDS));
          tab.right(tab.toggle("Redstone Disables", CPConfig.REDSTONE_DISABLES));
          tab.nextRow();

          tab.sections("Portal Limits", "Linking Range");
          tab.left(tab.intSlider("Max Portal Size", " blocks", 4, 900, 1,
              CPConfig.MAX_PORTAL_SIZE));
          tab.right(tab.intField("Base Range", 1, Integer.MAX_VALUE,
              CPConfig.BASE_RANGE));
          tab.nextRow();

          tab.left(tab.toggle("Cross-Dimension", CPConfig.ALLOW_CROSS_DIMENSION));
          tab.right(tab.intField("Enhanced Range", 1, Integer.MAX_VALUE,
              CPConfig.ENHANCED_RANGE));
          tab.nextRow();

          tab.right(tab.intField("Strong Range", 1, Integer.MAX_VALUE,
              CPConfig.STRONG_RANGE));
          tab.nextRow();
        })
        .build();
  }
}
