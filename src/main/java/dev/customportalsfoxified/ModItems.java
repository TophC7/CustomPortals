package dev.customportalsfoxified;

import dev.customportalsfoxified.items.PortalCatalyst;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(Registries.ITEM, CustomPortalsFoxified.MOD_ID);

  public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CustomPortalsFoxified.MOD_ID);

  // CATALYSTS //

  public static final Map<DyeColor, Supplier<PortalCatalyst>> CATALYSTS =
      new EnumMap<>(DyeColor.class);

  static {
    for (DyeColor color : DyeColor.values()) {
      CATALYSTS.put(
          color,
          ITEMS.register(
              color.getSerializedName() + "_portal_catalyst",
              () -> new PortalCatalyst(new Item.Properties().stacksTo(16), color)));
    }
  }

  // RUNE ITEMS //

  public static final Supplier<BlockItem> HASTE_RUNE_ITEM =
      ITEMS.register(
          "haste_rune", () -> new BlockItem(ModBlocks.HASTE_RUNE.get(), new Item.Properties()));

  public static final Supplier<BlockItem> GATE_RUNE_ITEM =
      ITEMS.register(
          "gate_rune", () -> new BlockItem(ModBlocks.GATE_RUNE.get(), new Item.Properties()));

  public static final Supplier<BlockItem> ENHANCER_RUNE_ITEM =
      ITEMS.register(
          "enhancer_rune",
          () -> new BlockItem(ModBlocks.ENHANCER_RUNE.get(), new Item.Properties()));

  public static final Supplier<BlockItem> STRONG_ENHANCER_RUNE_ITEM =
      ITEMS.register(
          "strong_enhancer_rune",
          () -> new BlockItem(ModBlocks.STRONG_ENHANCER_RUNE.get(), new Item.Properties()));

  public static final Supplier<BlockItem> INFINITY_RUNE_ITEM =
      ITEMS.register(
          "infinity_rune",
          () -> new BlockItem(ModBlocks.INFINITY_RUNE.get(), new Item.Properties()));

  // CREATIVE TAB //

  public static final Supplier<CreativeModeTab> TAB =
      CREATIVE_TABS.register(
          "custom_portals",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.custom_portals_foxified"))
                  .icon(() -> CATALYSTS.get(DyeColor.PURPLE).get().getDefaultInstance())
                  .displayItems(
                      (params, output) -> {
                        // catalysts
                        for (DyeColor color : DyeColor.values()) {
                          output.accept(CATALYSTS.get(color).get());
                        }
                        // runes
                        output.accept(HASTE_RUNE_ITEM.get());
                        output.accept(GATE_RUNE_ITEM.get());
                        output.accept(ENHANCER_RUNE_ITEM.get());
                        output.accept(STRONG_ENHANCER_RUNE_ITEM.get());
                        output.accept(INFINITY_RUNE_ITEM.get());
                      })
                  .build());
}
