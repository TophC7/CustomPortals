package dev.customportalsfoxified.portal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record PortalDefinition(
    ResourceLocation id,
    DyeColor color,
    BlockSelector frameSelector,
    boolean frameExclusive,
    ItemSelector catalystSelector,
    LinkMode linkMode,
    CatalystUseMode catalystUseMode,
    int catalystUseAmount,
    boolean returnCatalystOnBreak,
    int priority) {

  public PortalDefinition {
    if (id == null) {
      throw new IllegalArgumentException("Portal definition id cannot be null");
    }
    if (color == null) {
      throw new IllegalArgumentException("Portal definition color cannot be null");
    }
    if (frameSelector == null) {
      throw new IllegalArgumentException("Portal definition frame selector cannot be null");
    }
    if (catalystSelector == null) {
      throw new IllegalArgumentException("Portal definition catalyst selector cannot be null");
    }
    if (linkMode == null) {
      throw new IllegalArgumentException("Portal definition link mode cannot be null");
    }
    if (catalystUseMode == null) {
      throw new IllegalArgumentException("Portal definition catalyst use mode cannot be null");
    }
    if (catalystUseAmount < 0) {
      throw new IllegalArgumentException("Portal definition catalyst use amount cannot be negative");
    }
  }

  public boolean matches(BlockState frameState, ItemStack catalystStack) {
    return frameSelector.matches(frameState) && catalystSelector.matches(catalystStack);
  }

  public boolean matchesFrame(BlockState frameState) {
    return frameSelector.matches(frameState);
  }

  public int specificity() {
    return frameSelector.specificity() * 10 + catalystSelector.specificity();
  }

  public static PortalDefinition fromJson(ResourceLocation id, JsonObject json) {
    DyeColor color = parseColor(GsonHelper.getAsString(json, "color"));
    JsonObject frameJson = GsonHelper.getAsJsonObject(json, "frame");
    BlockSelector frameSelector = BlockSelector.fromJson(json, "frame");
    boolean frameExclusive = GsonHelper.getAsBoolean(frameJson, "exclusive", false);
    JsonObject catalystJson = GsonHelper.getAsJsonObject(json, "catalyst");
    ItemSelector catalystSelector = ItemSelector.fromJson(json, "catalyst");
    LinkMode linkMode =
        json.has("link_mode")
            ? LinkMode.fromSerializedName(GsonHelper.getAsString(json, "link_mode"))
            : LinkMode.LINKED_PAIR;
    CatalystUseMode catalystUseMode =
        catalystJson.has("use")
            ? CatalystUseMode.fromSerializedName(GsonHelper.getAsString(catalystJson, "use"))
            : CatalystUseMode.CONSUME;
    int catalystUseAmount = GsonHelper.getAsInt(catalystJson, "amount", 1);
    boolean returnCatalystOnBreak =
        GsonHelper.getAsBoolean(catalystJson, "return_on_break", true);
    int priority = GsonHelper.getAsInt(json, "priority", 0);
    return new PortalDefinition(
        id,
        color,
        frameSelector,
        frameExclusive,
        catalystSelector,
        linkMode,
        catalystUseMode,
        catalystUseAmount,
        returnCatalystOnBreak,
        priority);
  }

  public boolean canActivateWith(ItemStack catalystStack) {
    if (!matchesFrameOnlyAndCatalyst(catalystStack)) return false;

    return switch (catalystUseMode) {
      case NONE -> true;
      case CONSUME -> catalystStack.getCount() >= catalystUseAmount;
      case DAMAGE -> catalystUseAmount == 0 || catalystStack.isDamageableItem();
    };
  }

  public ItemStack createReturnCatalyst(ItemStack catalystStack) {
    if (catalystUseMode != CatalystUseMode.CONSUME || !returnCatalystOnBreak || catalystUseAmount <= 0) {
      return ItemStack.EMPTY;
    }
    return catalystStack.copyWithCount(catalystUseAmount);
  }

  public void applyCatalystCost(
      ServerLevel level, @Nullable ServerPlayer player, ItemStack catalystStack) {
    switch (catalystUseMode) {
      case NONE -> {
      }
      case CONSUME -> {
        if (catalystUseAmount > 0) {
          catalystStack.shrink(catalystUseAmount);
        }
      }
      case DAMAGE -> {
        if (catalystUseAmount > 0) {
          catalystStack.hurtAndBreak(catalystUseAmount, level, player, Item::getDefaultInstance);
        }
      }
    }
  }

  private boolean matchesFrameOnlyAndCatalyst(ItemStack catalystStack) {
    return catalystSelector.matches(catalystStack);
  }

  private static DyeColor parseColor(String serializedName) {
    for (DyeColor color : DyeColor.values()) {
      if (color.getSerializedName().equals(serializedName)) {
        return color;
      }
    }
    throw new JsonParseException("Unknown portal color '" + serializedName + "'");
  }

  public enum LinkMode {
    LINKED_PAIR("linked_pair");

    private final String serializedName;

    LinkMode(String serializedName) {
      this.serializedName = serializedName;
    }

    public String getSerializedName() {
      return serializedName;
    }

    public static LinkMode fromSerializedName(String serializedName) {
      for (LinkMode mode : values()) {
        if (mode.serializedName.equals(serializedName)) {
          return mode;
        }
      }
      throw new JsonParseException("Unknown link mode '" + serializedName + "'");
    }
  }

  public enum CatalystUseMode {
    CONSUME("consume"),
    DAMAGE("damage"),
    NONE("none");

    private final String serializedName;

    CatalystUseMode(String serializedName) {
      this.serializedName = serializedName;
    }

    public static CatalystUseMode fromSerializedName(String serializedName) {
      for (CatalystUseMode mode : values()) {
        if (mode.serializedName.equals(serializedName)) {
          return mode;
        }
      }
      throw new JsonParseException("Unknown catalyst use mode '" + serializedName + "'");
    }
  }
}
