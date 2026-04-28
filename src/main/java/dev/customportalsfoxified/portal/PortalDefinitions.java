package dev.customportalsfoxified.portal;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.data.CustomPortal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PortalDefinitions {

  private static final int BUILTIN_PRIORITY = -1000;
  public static final TagKey<Block> FRAME_ALLOWLIST_TAG =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(
              CustomPortalsFoxified.MOD_ID, "portal_frame_allowlist"));
  public static final TagKey<Block> FRAME_BLOCKLIST_TAG =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(
              CustomPortalsFoxified.MOD_ID, "portal_frame_blocklist"));

  private static final Map<ResourceLocation, PortalDefinition> BUILTIN_DEFINITIONS =
      new LinkedHashMap<>();
  private static final Map<ResourceLocation, PortalDefinition> API_DEFINITIONS =
      new LinkedHashMap<>();

  private static Map<ResourceLocation, PortalDefinition> datapackDefinitions = Map.of();
  private static volatile Map<ResourceLocation, PortalDefinition> activeDefinitions = Map.of();
  private static volatile boolean builtInAllowlistEnabled;
  private static volatile boolean validationPending;
  private static boolean builtInsRegistered;

  private PortalDefinitions() {}

  public static synchronized void registerApi(PortalDefinition definition) {
    PortalDefinition previous = API_DEFINITIONS.putIfAbsent(definition.id(), definition);
    if (previous != null && !previous.equals(definition)) {
      throw new IllegalStateException(
          "Runtime portal definition id already registered: " + definition.id());
    }
    validationPending = true;
    rebuildActiveDefinitions();
  }

  public static synchronized void registerRuntime(PortalDefinition definition) {
    registerApi(definition);
  }

  public static synchronized void replaceDatapackDefinitions(
      Map<ResourceLocation, PortalDefinition> definitions, boolean builtInAllowlistEnabled) {
    datapackDefinitions = new LinkedHashMap<>(definitions);
    PortalDefinitions.builtInAllowlistEnabled = builtInAllowlistEnabled;
    validationPending = true;
    rebuildActiveDefinitions();
  }

  public static synchronized void clearDatapackDefinitions() {
    datapackDefinitions = Map.of();
    builtInAllowlistEnabled = false;
    rebuildActiveDefinitions();
  }

  public static synchronized boolean consumeValidationPending() {
    boolean pending = validationPending;
    validationPending = false;
    return pending;
  }

  public static synchronized void clearValidationPending() {
    validationPending = false;
  }

  public static Collection<PortalDefinition> getAll() {
    return Collections.unmodifiableCollection(activeDefinitions.values());
  }

  public static @Nullable PortalDefinition get(ResourceLocation id) {
    return activeDefinitions.get(id);
  }

  public static @Nullable PortalDefinition resolveActivation(
      BlockState frameState, ItemStack catalystStack) {
    if (catalystStack.isEmpty()) return null;

    List<PortalDefinition> matches = new ArrayList<>();
    for (PortalDefinition definition : datapackDefinitions.values()) {
      if (definition.matches(frameState, catalystStack)) {
        matches.add(definition);
      }
    }

    for (PortalDefinition definition : API_DEFINITIONS.values()) {
      if (definition.matches(frameState, catalystStack)) {
        matches.add(definition);
      }
    }

    if (isBuiltInFrameAllowed(frameState)) {
      for (PortalDefinition definition : BUILTIN_DEFINITIONS.values()) {
        if (definition.matches(frameState, catalystStack)) {
          matches.add(definition);
        }
      }
    }

    if (matches.isEmpty()) return null;

    matches.sort(
        (left, right) -> {
          int priorityCompare = Integer.compare(right.priority(), left.priority());
          if (priorityCompare != 0) return priorityCompare;

          int specificityCompare = Integer.compare(right.specificity(), left.specificity());
          if (specificityCompare != 0) return specificityCompare;

          return left.id().compareNamespaced(right.id());
        });

    PortalDefinition best = matches.get(0);
    for (int i = 1; i < matches.size(); i++) {
      PortalDefinition candidate = matches.get(i);
      if (candidate.priority() != best.priority()
          || candidate.specificity() != best.specificity()) {
        break;
      }

      CustomPortalsFoxified.LOGGER.warn(
          "Ambiguous portal definitions for frame={} catalyst={}: {} and {}",
          net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(frameState.getBlock()),
          net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(catalystStack.getItem()),
          best.id(),
          candidate.id());
      return null;
    }

    return best;
  }

  public static synchronized void registerBuiltInCatalysts() {
    if (builtInsRegistered) return;
    builtInsRegistered = true;

    for (DyeColor color : DyeColor.values()) {
      String catalystPath = color.getSerializedName() + "_portal_catalyst";
      PortalDefinition definition =
          new PortalDefinition(
              ResourceLocation.fromNamespaceAndPath(
                  CustomPortalsFoxified.MOD_ID, "builtin/" + catalystPath),
              color,
              BlockSelector.any(),
              false,
              ItemSelector.exact(
                  ResourceLocation.fromNamespaceAndPath(
                      CustomPortalsFoxified.MOD_ID, catalystPath)),
              PortalDefinition.LinkMode.LINKED_PAIR,
              null,
              PortalDefinition.CatalystUseMode.CONSUME,
              1,
              true,
              BUILTIN_PRIORITY);
      PortalDefinition previous = BUILTIN_DEFINITIONS.putIfAbsent(definition.id(), definition);
      if (previous != null && !previous.equals(definition)) {
        throw new IllegalStateException(
            "Built-in portal definition id already registered: " + definition.id());
      }
    }
    rebuildActiveDefinitions();
  }

  public static boolean isPortalAllowed(
      @Nullable ResourceLocation definitionId, ResourceLocation frameMaterial) {
    if (!BuiltInRegistries.BLOCK.containsKey(frameMaterial)) return false;

    Block frameBlock = BuiltInRegistries.BLOCK.get(frameMaterial);
    BlockState frameState = frameBlock.defaultBlockState();
    if (definitionId == null) {
      return isBuiltInFrameAllowed(frameState);
    }

    PortalDefinition definition = activeDefinitions.get(definitionId);
    if (definition == null || !definition.matchesFrame(frameState)) {
      return false;
    }

    if (BUILTIN_DEFINITIONS.containsKey(definitionId)) {
      return isBuiltInFrameAllowed(frameState);
    }

    return true;
  }

  public static boolean usesCounterpartRoute(@Nullable ResourceLocation definitionId) {
    PortalDefinition definition = definitionId != null ? activeDefinitions.get(definitionId) : null;
    return definition != null && definition.usesCounterpartRoute();
  }

  public static @Nullable PortalDefinition.CounterpartRoute getCounterpartRoute(
      @Nullable ResourceLocation definitionId) {
    PortalDefinition definition = definitionId != null ? activeDefinitions.get(definitionId) : null;
    return definition != null ? definition.counterpartRoute() : null;
  }

  public static boolean shouldPortalStayLit(CustomPortal portal) {
    if (portal.isDefinitionDisabled() || portal.isRedstoneDisabled()) {
      return false;
    }

    PortalDefinition definition =
        portal.getDefinitionId() != null ? activeDefinitions.get(portal.getDefinitionId()) : null;
    if (definition == null) {
      return portal.isLinked();
    }

    return definition.keepsPortalActiveWithoutLink() || portal.isLinked();
  }

  private static void rebuildActiveDefinitions() {
    LinkedHashMap<ResourceLocation, PortalDefinition> merged = new LinkedHashMap<>();
    for (PortalDefinition definition : sortedValues(BUILTIN_DEFINITIONS)) {
      merged.put(definition.id(), definition);
    }
    for (PortalDefinition definition : sortedValues(API_DEFINITIONS)) {
      merged.put(definition.id(), definition);
    }
    for (PortalDefinition definition : sortedValues(datapackDefinitions)) {
      merged.put(definition.id(), definition);
    }
    activeDefinitions = Collections.unmodifiableMap(merged);
  }

  private static List<PortalDefinition> sortedValues(Map<ResourceLocation, PortalDefinition> map) {
    List<PortalDefinition> values = new ArrayList<>(map.values());
    values.sort((left, right) -> left.id().compareNamespaced(right.id()));
    return values;
  }

  private static boolean isBuiltInFrameAllowed(BlockState frameState) {
    for (PortalDefinition definition : API_DEFINITIONS.values()) {
      if (definition.frameExclusive() && definition.matchesFrame(frameState)) {
        return false;
      }
    }

    for (PortalDefinition definition : datapackDefinitions.values()) {
      if (definition.frameExclusive() && definition.matchesFrame(frameState)) {
        return false;
      }
    }

    if (frameState.is(FRAME_BLOCKLIST_TAG)) return false;

    if (builtInAllowlistEnabled && !frameState.is(FRAME_ALLOWLIST_TAG)) {
      return false;
    }

    return true;
  }
}
