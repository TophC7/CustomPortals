package dev.customportalsfoxified.config;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import xyz.kwahson.core.config.ConfigBinding;
import xyz.kwahson.core.ui.binding.ValueBinding;
import xyz.kwahson.core.ui.layout.FormLayout;
import xyz.kwahson.core.ui.screen.TabbedScreen;
import xyz.kwahson.core.ui.widget.SliderField;
import xyz.kwahson.core.ui.widget.ToggleField;

public final class CPConfigScreen {

  private CPConfigScreen() {}

  /** Called from mod constructor, guarded by dist check. Keeps IConfigScreenFactory
   *  out of the main mod class so the server never tries to load Screen. */
  public static void register(ModContainer modContainer) {
    modContainer.registerExtensionPoint(IConfigScreenFactory.class,
        (mc, parent) -> create(parent));
  }

  public static Screen create(Screen parent) {
    return TabbedScreen.builder("Custom Portals Foxified", parent)
        .tab("Settings", CPConfigScreen::buildSettingsTab)
        .build();
  }

  private static void buildSettingsTab(FormLayout tab) {
    tab.sections("Sound", "Redstone");
    tab.left(ToggleField.of(clientBool("Mute Sounds", CPConfig.MUTE_SOUNDS, false)));
    tab.right(ToggleField.of(commonBool("Redstone Disables", CPConfig.REDSTONE_DISABLES, true)));
    tab.nextRow();

    tab.sections("Portal Limits", "Linking Range");
    tab.left(SliderField.intRange(
        commonInt("Max Portal Size", CPConfig.MAX_PORTAL_SIZE, 64),
        4, 900, 1, " blocks"));
    tab.right(SliderField.intRange(
        commonInt("Base Range", CPConfig.BASE_RANGE, 100),
        1, Integer.MAX_VALUE, 1, ""));
    tab.nextRow();

    tab.left(SliderField.intRange(
        commonInt("Min Portal Size", CPConfig.MIN_PORTAL_SIZE, 1),
        1, 900, 1, " blocks"));
    tab.right(SliderField.intRange(
        commonInt("Enhanced Range", CPConfig.ENHANCED_RANGE, 1000),
        1, Integer.MAX_VALUE, 1, ""));
    tab.nextRow();

    tab.left(ToggleField.of(commonBool(
        "Cross-Dimension", CPConfig.ALLOW_CROSS_DIMENSION, true)));
    tab.right(SliderField.intRange(
        commonInt("Strong Range", CPConfig.STRONG_RANGE, 10000),
        1, Integer.MAX_VALUE, 1, ""));
  }

  private static ValueBinding<Boolean> clientBool(
      String label, ModConfigSpec.BooleanValue value, boolean fallback) {
    return ConfigBinding.saving(
        ConfigBinding.bool(Component.literal(label), value, fallback),
        CPConfig.CLIENT_SPEC);
  }

  private static ValueBinding<Boolean> commonBool(
      String label, ModConfigSpec.BooleanValue value, boolean fallback) {
    return ConfigBinding.saving(
        ConfigBinding.bool(Component.literal(label), value, fallback),
        CPConfig.COMMON_SPEC);
  }

  private static ValueBinding<Integer> commonInt(
      String label, ModConfigSpec.IntValue value, int fallback) {
    return ConfigBinding.saving(
        ConfigBinding.intValue(Component.literal(label), value, fallback),
        CPConfig.COMMON_SPEC);
  }
}
