package dev.customportalsfoxified.portal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record PortalDefinition(
    ResourceLocation id,
    DyeColor color,
    BlockSelector frameSelector,
    boolean frameExclusive,
    ItemSelector catalystSelector,
    LinkMode linkMode,
    @Nullable CounterpartRoute counterpartRoute,
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
    if (linkMode == LinkMode.SCALED_DIMENSION_COUNTERPART && counterpartRoute == null) {
      throw new IllegalArgumentException(
          "Portal definition counterpart route is required for scaled_dimension_counterpart");
    }
    if (linkMode != LinkMode.SCALED_DIMENSION_COUNTERPART && counterpartRoute != null) {
      throw new IllegalArgumentException(
          "Portal definition counterpart route is only valid for scaled_dimension_counterpart");
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
    CounterpartRoute counterpartRoute =
        linkMode == LinkMode.SCALED_DIMENSION_COUNTERPART
            ? CounterpartRoute.fromJson(GsonHelper.getAsJsonObject(json, "destination"))
            : null;
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
        counterpartRoute,
        catalystUseMode,
        catalystUseAmount,
        returnCatalystOnBreak,
        priority);
  }

  public boolean usesCounterpartRoute() {
    return linkMode == LinkMode.SCALED_DIMENSION_COUNTERPART;
  }

  public boolean keepsPortalActiveWithoutLink() {
    return usesCounterpartRoute();
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
    LINKED_PAIR("linked_pair"),
    SCALED_DIMENSION_COUNTERPART("scaled_dimension_counterpart");

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

  public record CounterpartRoute(
      ResourceKey<Level> dimensionA,
      ResourceKey<Level> dimensionB,
      double coordinateScale,
      int searchRadius,
      int verticalSearchRadius,
      int maxAutogenPortalWidth,
      int maxAutogenPortalHeight,
      int maxAutogenPortalArea) {

    /**
     * Default maximum portal width for auto-generated counterpart portals.
     * Applies when {@code max_autogen_portal_width} is omitted from the datapack JSON.
     */
    public static final int DEFAULT_MAX_AUTOGEN_WIDTH = 5;
    /**
     * Default maximum portal height for auto-generated counterpart portals.
     * Applies when {@code max_autogen_portal_height} is omitted from the datapack JSON.
     */
    public static final int DEFAULT_MAX_AUTOGEN_HEIGHT = 5;
    /**
     * Default maximum portal area (width * height) for auto-generated counterpart portals.
     * Applies when {@code max_autogen_portal_area} is omitted from the datapack JSON.
     */
    public static final int DEFAULT_MAX_AUTOGEN_AREA = 25;

    public CounterpartRoute {
      if (dimensionA == null || dimensionB == null) {
        throw new IllegalArgumentException("Counterpart route dimensions cannot be null");
      }
      if (dimensionA.equals(dimensionB)) {
        throw new IllegalArgumentException("Counterpart route dimensions must differ");
      }
      if (coordinateScale <= 0.0D) {
        throw new IllegalArgumentException("Counterpart route coordinate scale must be positive");
      }
      if (searchRadius < 0 || verticalSearchRadius < 0) {
        throw new IllegalArgumentException(
            "Counterpart route search radii cannot be negative");
      }
      if (maxAutogenPortalWidth <= 0
          || maxAutogenPortalHeight <= 0
          || maxAutogenPortalArea <= 0) {
        throw new IllegalArgumentException(
            "Counterpart route portal size limits must be positive");
      }
    }

    public static CounterpartRoute fromJson(JsonObject json) {
      ResourceKey<Level> dimensionA =
          ResourceKey.create(
              Registries.DIMENSION,
              ResourceLocation.parse(GsonHelper.getAsString(json, "dimension_a")));
      ResourceKey<Level> dimensionB =
          ResourceKey.create(
              Registries.DIMENSION,
              ResourceLocation.parse(GsonHelper.getAsString(json, "dimension_b")));
      double coordinateScale = GsonHelper.getAsDouble(json, "coordinate_scale", 1.0D);
      int searchRadius = GsonHelper.getAsInt(json, "search_radius", 16);
      int verticalSearchRadius = GsonHelper.getAsInt(json, "vertical_search_radius", 8);
      int maxAutogenPortalWidth =
          GsonHelper.getAsInt(json, "max_autogen_portal_width", CounterpartRoute.DEFAULT_MAX_AUTOGEN_WIDTH);
      int maxAutogenPortalHeight =
          GsonHelper.getAsInt(json, "max_autogen_portal_height", CounterpartRoute.DEFAULT_MAX_AUTOGEN_HEIGHT);
      int maxAutogenPortalArea =
          GsonHelper.getAsInt(json, "max_autogen_portal_area", CounterpartRoute.DEFAULT_MAX_AUTOGEN_AREA);
      return new CounterpartRoute(
          dimensionA,
          dimensionB,
          coordinateScale,
          searchRadius,
          verticalSearchRadius,
          maxAutogenPortalWidth,
          maxAutogenPortalHeight,
          maxAutogenPortalArea);
    }

    public boolean supportsDimensionPair(
        ResourceKey<Level> left, ResourceKey<Level> right) {
      return (dimensionA.equals(left) && dimensionB.equals(right))
          || (dimensionA.equals(right) && dimensionB.equals(left));
    }

    public @Nullable ResourceKey<Level> getCounterpartDimension(ResourceKey<Level> sourceDimension) {
      if (dimensionA.equals(sourceDimension)) return dimensionB;
      if (dimensionB.equals(sourceDimension)) return dimensionA;
      return null;
    }

    public BlockPos transform(BlockPos sourcePos, ResourceKey<Level> sourceDimension) {
      if (dimensionA.equals(sourceDimension)) {
        return new BlockPos(
            Mth.floor(sourcePos.getX() / coordinateScale),
            sourcePos.getY(),
            Mth.floor(sourcePos.getZ() / coordinateScale));
      }
      if (dimensionB.equals(sourceDimension)) {
        return new BlockPos(
            Mth.floor(sourcePos.getX() * coordinateScale),
            sourcePos.getY(),
            Mth.floor(sourcePos.getZ() * coordinateScale));
      }
      throw new IllegalArgumentException(
          "Source dimension " + sourceDimension.location() + " is not part of counterpart route");
    }

    public boolean allowsPortalShape(int width, int height) {
      return width <= maxAutogenPortalWidth
          && height <= maxAutogenPortalHeight
          && width * height <= maxAutogenPortalArea;
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
