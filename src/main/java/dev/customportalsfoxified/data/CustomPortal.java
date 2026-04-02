package dev.customportalsfoxified.data;

import dev.customportalsfoxified.config.CPConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CustomPortal {

  private final UUID id;
  private final DyeColor color;
  private final ResourceLocation frameMaterial;
  private final ResourceKey<Level> dimension;
  private final BlockPos spawnPos;
  private final Set<BlockPos> portalBlocks;
  private final @Nullable UUID creatorId;

  // linking
  private @Nullable UUID linkedPortalId;
  private @Nullable ResourceKey<Level> linkedDimension;

  // RUNE STATE //

  private boolean hasHaste;
  private boolean hasGate;
  private boolean hasInfinity;
  private int weakEnhancerCount;
  private int strongEnhancerCount;

  public CustomPortal(
      UUID id,
      DyeColor color,
      ResourceLocation frameMaterial,
      ResourceKey<Level> dimension,
      BlockPos spawnPos,
      Set<BlockPos> portalBlocks,
      @Nullable UUID creatorId) {
    this.id = id;
    this.color = color;
    this.frameMaterial = frameMaterial;
    this.dimension = dimension;
    this.spawnPos = spawnPos;
    this.portalBlocks = new HashSet<>(portalBlocks);
    this.creatorId = creatorId;
  }

  // GETTERS //

  public UUID getId() {
    return id;
  }

  public DyeColor getColor() {
    return color;
  }

  public ResourceLocation getFrameMaterial() {
    return frameMaterial;
  }

  public ResourceKey<Level> getDimension() {
    return dimension;
  }

  public BlockPos getSpawnPos() {
    return spawnPos;
  }

  public Set<BlockPos> getPortalBlocks() {
    return Collections.unmodifiableSet(portalBlocks);
  }

  public @Nullable UUID getCreatorId() {
    return creatorId;
  }

  public @Nullable UUID getLinkedPortalId() {
    return linkedPortalId;
  }

  public @Nullable ResourceKey<Level> getLinkedDimension() {
    return linkedDimension;
  }

  public boolean isLinked() {
    return linkedPortalId != null;
  }

  public boolean hasHaste() {
    return hasHaste;
  }

  public boolean hasGate() {
    return hasGate;
  }

  public boolean hasInfinity() {
    return hasInfinity;
  }

  // LINKING //

  public void link(CustomPortal other) {
    this.linkedPortalId = other.id;
    this.linkedDimension = other.dimension;
    other.linkedPortalId = this.id;
    other.linkedDimension = this.dimension;
  }

  public void unlink() {
    this.linkedPortalId = null;
    this.linkedDimension = null;
  }

  public boolean canLinkWith(CustomPortal other) {
    if (this.isLinked() || other.isLinked()) return false;
    return isCompatibleWith(other);
  }

  /**
   * Checks compatibility (color, frame, dimension rules, range) without considering link state.
   * Used for initial linking (via canLinkWith) and for revalidating existing links after rune
   * changes.
   */
  public boolean isCompatibleWith(CustomPortal other) {
    if (this.id.equals(other.id)) return false;
    if (this.color != other.color) return false;
    if (!this.frameMaterial.equals(other.frameMaterial)) return false;

    // cross-dimension requires gate rune on at least one side
    boolean crossDimension = !this.dimension.equals(other.dimension);
    if (crossDimension) {
      if (!CPConfig.ALLOW_CROSS_DIMENSION.get()) return false;
      if (!this.hasGate && !other.hasGate) return false;
    }

    // private portals must share creator
    if (CPConfig.PRIVATE_PORTALS.get()) {
      if (this.creatorId != null
          && other.creatorId != null
          && !this.creatorId.equals(other.creatorId)) {
        return false;
      }
    }

    return isInRange(other);
  }

  private boolean isInRange(CustomPortal other) {
    int maxTier = Math.max(this.getEnhancementTier(), other.getEnhancementTier());
    int range = getRangeForTier(maxTier);
    if (range == Integer.MAX_VALUE) return true;

    double distance = calculateDistance(other);
    return distance <= range;
  }

  private double calculateDistance(CustomPortal other) {
    double x1 = this.spawnPos.getX();
    double z1 = this.spawnPos.getZ();
    double x2 = other.spawnPos.getX();
    double z2 = other.spawnPos.getZ();

    // NOTE: nether 8:1 ratio; scale nether coords to overworld for comparison
    boolean thisIsNether = this.dimension.equals(Level.NETHER);
    boolean otherIsNether = other.dimension.equals(Level.NETHER);

    if (thisIsNether && !otherIsNether) {
      x1 *= 8;
      z1 *= 8;
    } else if (!thisIsNether && otherIsNether) {
      x2 *= 8;
      z2 *= 8;
    }

    return Math.sqrt((x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1));
  }

  // ENHANCEMENT //

  public int getEnhancementTier() {
    if (hasInfinity) return 3;
    int tier = 0;
    tier += weakEnhancerCount;
    tier += strongEnhancerCount * 2;
    return Math.min(tier, 2);
  }

  private static int getRangeForTier(int tier) {
    return switch (tier) {
      case 1 -> CPConfig.ENHANCED_RANGE.get();
      case 2 -> CPConfig.STRONG_RANGE.get();
      case 3 -> Integer.MAX_VALUE;
      default -> CPConfig.BASE_RANGE.get();
    };
  }

  // RUNE MUTATIONS //

  public void addRune(RuneType type) {
    switch (type) {
      case HASTE -> hasHaste = true;
      case GATE -> hasGate = true;
      case INFINITY -> hasInfinity = true;
      case WEAK_ENHANCER -> weakEnhancerCount++;
      case STRONG_ENHANCER -> strongEnhancerCount++;
    }
  }

  public void removeRune(RuneType type) {
    switch (type) {
      case HASTE -> hasHaste = false;
      case GATE -> hasGate = false;
      case INFINITY -> hasInfinity = false;
      case WEAK_ENHANCER -> weakEnhancerCount = Math.max(0, weakEnhancerCount - 1);
      case STRONG_ENHANCER -> strongEnhancerCount = Math.max(0, strongEnhancerCount - 1);
    }
  }

  // SERIALIZATION //

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    tag.putUUID("id", id);
    tag.putInt("color", color.getId());
    tag.putString("frameMaterial", frameMaterial.toString());
    tag.putString("dimension", dimension.location().toString());
    tag.putInt("spawnX", spawnPos.getX());
    tag.putInt("spawnY", spawnPos.getY());
    tag.putInt("spawnZ", spawnPos.getZ());

    ListTag blocksList = new ListTag();
    for (BlockPos pos : portalBlocks) {
      // NbtUtils.writeBlockPos returns IntArrayTag [x, y, z]
      blocksList.add(NbtUtils.writeBlockPos(pos));
    }
    tag.put("portalBlocks", blocksList);

    if (creatorId != null) tag.putUUID("creatorId", creatorId);
    if (linkedPortalId != null) {
      tag.putUUID("linkedPortalId", linkedPortalId);
      tag.putString("linkedDimension", linkedDimension.location().toString());
    }

    tag.putBoolean("hasHaste", hasHaste);
    tag.putBoolean("hasGate", hasGate);
    tag.putBoolean("hasInfinity", hasInfinity);
    tag.putInt("weakEnhancers", weakEnhancerCount);
    tag.putInt("strongEnhancers", strongEnhancerCount);

    return tag;
  }

  public static CustomPortal load(CompoundTag tag) {
    UUID id = tag.getUUID("id");
    DyeColor color = DyeColor.byId(tag.getInt("color"));
    ResourceLocation frameMaterial = ResourceLocation.parse(tag.getString("frameMaterial"));
    ResourceKey<Level> dimension =
        ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse(tag.getString("dimension")));

    BlockPos spawnPos =
        new BlockPos(tag.getInt("spawnX"), tag.getInt("spawnY"), tag.getInt("spawnZ"));

    Set<BlockPos> portalBlocks = new HashSet<>();
    // portalBlocks is a list of IntArrayTag [x, y, z] NOT CompoundTag
    ListTag blocksList = tag.getList("portalBlocks", Tag.TAG_INT_ARRAY);
    for (int i = 0; i < blocksList.size(); i++) {
      int[] coords = blocksList.getIntArray(i);
      if (coords.length == 3) {
        portalBlocks.add(new BlockPos(coords[0], coords[1], coords[2]));
      }
    }

    UUID creatorId = tag.contains("creatorId") ? tag.getUUID("creatorId") : null;

    CustomPortal portal =
        new CustomPortal(id, color, frameMaterial, dimension, spawnPos, portalBlocks, creatorId);

    if (tag.contains("linkedPortalId")) {
      portal.linkedPortalId = tag.getUUID("linkedPortalId");
      portal.linkedDimension =
          ResourceKey.create(
              net.minecraft.core.registries.Registries.DIMENSION,
              ResourceLocation.parse(tag.getString("linkedDimension")));
    }

    portal.hasHaste = tag.getBoolean("hasHaste");
    portal.hasGate = tag.getBoolean("hasGate");
    portal.hasInfinity = tag.getBoolean("hasInfinity");
    portal.weakEnhancerCount = tag.getInt("weakEnhancers");
    portal.strongEnhancerCount = tag.getInt("strongEnhancers");

    return portal;
  }
}
