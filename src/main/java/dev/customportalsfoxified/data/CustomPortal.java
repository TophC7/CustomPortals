package dev.customportalsfoxified.data;

import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.portal.PortalDefinitions;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.kwahson.core.config.SafeConfig;

public class CustomPortal {

  private final UUID id;
  private final @Nullable ResourceLocation definitionId;
  private final DyeColor color;
  private final ResourceLocation frameMaterial;
  private final ResourceKey<Level> dimension;
  private final BlockPos spawnPos;
  private final Direction.Axis axis;
  private final Set<BlockPos> portalBlocks;
  private final Set<BlockPos> frameBlocks;
  private final ItemStack storedCatalystStack;
  // linking
  private @Nullable UUID linkedPortalId;
  private @Nullable ResourceKey<Level> linkedDimension;

  // RUNE STATE //
  // all rune types use counters so removing one of N identical runes
  // doesn't incorrectly disable the effect

  private int hasteCount;
  private int gateCount;
  private int infinityCount;
  private int weakEnhancerCount;
  private int strongEnhancerCount;

  private boolean redstoneDisabled;
  private boolean definitionDisabled;

  public CustomPortal(
      UUID id,
      @Nullable ResourceLocation definitionId,
      DyeColor color,
      ResourceLocation frameMaterial,
      ResourceKey<Level> dimension,
      BlockPos spawnPos,
      Direction.Axis axis,
      Set<BlockPos> portalBlocks,
      Set<BlockPos> frameBlocks,
      ItemStack storedCatalystStack) {
    this.id = id;
    this.definitionId = definitionId;
    this.color = color;
    this.frameMaterial = frameMaterial;
    this.dimension = dimension;
    this.spawnPos = spawnPos;
    this.axis = axis;
    this.portalBlocks = new HashSet<>(portalBlocks);
    this.frameBlocks = new HashSet<>(frameBlocks);
    this.storedCatalystStack = storedCatalystStack.copy();
  }

  public CustomPortal(
      UUID id,
      DyeColor color,
      ResourceLocation frameMaterial,
      ResourceKey<Level> dimension,
      BlockPos spawnPos,
      Set<BlockPos> portalBlocks) {
    this(
        id,
        null,
        color,
        frameMaterial,
        dimension,
        spawnPos,
        Direction.Axis.X,
        portalBlocks,
        Set.of(),
        ItemStack.EMPTY);
  }

  public CustomPortal(
      UUID id,
      @Nullable ResourceLocation definitionId,
      DyeColor color,
      ResourceLocation frameMaterial,
      ResourceKey<Level> dimension,
      BlockPos spawnPos,
      Set<BlockPos> portalBlocks) {
    this(
        id,
        definitionId,
        color,
        frameMaterial,
        dimension,
        spawnPos,
        Direction.Axis.X,
        portalBlocks,
        Set.of(),
        ItemStack.EMPTY);
  }

  // GETTERS //

  public UUID getId() {
    return id;
  }

  public DyeColor getColor() {
    return color;
  }

  public @Nullable ResourceLocation getDefinitionId() {
    return definitionId;
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

  public Direction.Axis getAxis() {
    return axis;
  }

  public Set<BlockPos> getPortalBlocks() {
    return Collections.unmodifiableSet(portalBlocks);
  }

  public Set<BlockPos> getFrameBlocks() {
    return Collections.unmodifiableSet(frameBlocks);
  }

  public ItemStack getStoredCatalystStack() {
    return storedCatalystStack.copy();
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

  public boolean isRedstoneDisabled() {
    return redstoneDisabled;
  }

  public void setRedstoneDisabled(boolean disabled) {
    this.redstoneDisabled = disabled;
  }

  public boolean isDefinitionDisabled() {
    return definitionDisabled;
  }

  public void setDefinitionDisabled(boolean disabled) {
    this.definitionDisabled = disabled;
  }

  public boolean hasHaste() {
    return hasteCount > 0;
  }

  public boolean hasGate() {
    return gateCount > 0;
  }

  public boolean hasInfinity() {
    return infinityCount > 0;
  }

  // LINKING //

  public void link(CustomPortal other) {
    this.linkedPortalId = other.id;
    this.linkedDimension = other.dimension;
    other.linkedPortalId = this.id;
    other.linkedDimension = this.dimension;
  }

  /**
   * Unilateral forward link: sets this portal's destination to the target without
   * modifying the target's back-link. Used for many-to-one counterpart convergence
   * where multiple source portals share a single destination portal, mirroring
   * vanilla nether portal behavior.
   */
  public void linkTo(CustomPortal target) {
    this.linkedPortalId = target.id;
    this.linkedDimension = target.dimension;
  }

  /**
   * Clears this portal's link fields. Also clears the partner's link fields only if
   * the partner's back-link points to this portal (bilateral). Safe for many-to-one
   * counterpart convergence: unlinking a secondary source won't disrupt the
   * counterpart's existing bilateral connection to its primary source.
   */
  public void unlinkFrom(CustomPortal partner) {
    if (this.id.equals(partner.linkedPortalId)) {
      partner.unlink();
    }
    this.unlink();
  }

  /** Unilateral: clears only this portal's link fields. */
  public void unlink() {
    this.linkedPortalId = null;
    this.linkedDimension = null;
  }

  public boolean canLinkWith(CustomPortal other) {
    if (this.isLinked() || other.isLinked()) return false;
    if (this.redstoneDisabled || other.redstoneDisabled) return false;
    if (this.definitionDisabled || other.definitionDisabled) return false;
    return isCompatibleWith(other);
  }

  /**
   * Checks compatibility (color, frame, dimension rules, range) without considering link state.
   * Used for initial linking (via canLinkWith) and for revalidating existing links after rune
   * changes.
   */
  public boolean isCompatibleWith(CustomPortal other) {
    if (this.id.equals(other.id)) return false;
    if (this.definitionDisabled || other.definitionDisabled) return false;
    if (!hasMatchingPortalType(other)) return false;

    if (PortalDefinitions.areDefinitionsCompatibleForLink(this, other)) {
      return true;
    }

    // cross-dimension requires gate rune on at least one side
    boolean crossDimension = !this.dimension.equals(other.dimension);
    if (crossDimension) {
      if (!SafeConfig.getBool(CPConfig.ALLOW_CROSS_DIMENSION, true)) return false;
      if (!this.hasGate() && !other.hasGate()) return false;
    }

    return isInRange(other);
  }

  private boolean hasMatchingPortalType(CustomPortal other) {
    if (this.definitionId != null || other.definitionId != null) {
      return this.definitionId != null && this.definitionId.equals(other.definitionId);
    }

    if (this.color != other.color) return false;
    return this.frameMaterial.equals(other.frameMaterial);
  }

  private boolean isInRange(CustomPortal other) {
    int maxTier = Math.max(this.getEnhancementTier(), other.getEnhancementTier());
    int range = getRangeForTier(maxTier);
    if (range == Integer.MAX_VALUE) return true;

    long distSq = calculateDistanceSquared(other);
    return distSq <= (long) range * range;
  }

  long calculateDistanceSquared(CustomPortal other) {
    long x1 = this.spawnPos.getX();
    long z1 = this.spawnPos.getZ();
    long x2 = other.spawnPos.getX();
    long z2 = other.spawnPos.getZ();

    // nether 8:1 ratio; scale nether coords to overworld for comparison
    boolean thisIsNether = this.dimension.equals(Level.NETHER);
    boolean otherIsNether = other.dimension.equals(Level.NETHER);

    if (thisIsNether && !otherIsNether) {
      x1 *= 8;
      z1 *= 8;
    } else if (!thisIsNether && otherIsNether) {
      x2 *= 8;
      z2 *= 8;
    }

    long dx = x2 - x1;
    long dz = z2 - z1;
    return dx * dx + dz * dz;
  }

  // ENHANCEMENT //

  public int getEnhancementTier() {
    if (infinityCount > 0) return 3;
    int tier = 0;
    tier += weakEnhancerCount;
    tier += strongEnhancerCount * 2;
    return Math.min(tier, 2);
  }

  private static int getRangeForTier(int tier) {
    return switch (tier) {
      case 1 -> SafeConfig.getInt(CPConfig.ENHANCED_RANGE, 1000);
      case 2 -> SafeConfig.getInt(CPConfig.STRONG_RANGE, 10000);
      case 3 -> Integer.MAX_VALUE;
      default -> SafeConfig.getInt(CPConfig.BASE_RANGE, 100);
    };
  }

  // RUNE MUTATIONS //

  public void addRune(RuneType type) {
    switch (type) {
      case HASTE -> hasteCount++;
      case GATE -> gateCount++;
      case INFINITY -> infinityCount++;
      case WEAK_ENHANCER -> weakEnhancerCount++;
      case STRONG_ENHANCER -> strongEnhancerCount++;
    }
  }

  public void removeRune(RuneType type) {
    switch (type) {
      case HASTE -> hasteCount = Math.max(0, hasteCount - 1);
      case GATE -> gateCount = Math.max(0, gateCount - 1);
      case INFINITY -> infinityCount = Math.max(0, infinityCount - 1);
      case WEAK_ENHANCER -> weakEnhancerCount = Math.max(0, weakEnhancerCount - 1);
      case STRONG_ENHANCER -> strongEnhancerCount = Math.max(0, strongEnhancerCount - 1);
    }
  }

  // SERIALIZATION //

  public CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
    CompoundTag tag = new CompoundTag();
    tag.putUUID("id", id);
    if (definitionId != null) {
      tag.putString("definitionId", definitionId.toString());
    }
    tag.putInt("color", color.getId());
    tag.putString("frameMaterial", frameMaterial.toString());
    tag.putString("dimension", dimension.location().toString());
    tag.putInt("spawnX", spawnPos.getX());
    tag.putInt("spawnY", spawnPos.getY());
    tag.putInt("spawnZ", spawnPos.getZ());
    tag.putString("axis", axis.getSerializedName());

    ListTag blocksList = new ListTag();
    for (BlockPos pos : portalBlocks) {
      // NbtUtils.writeBlockPos returns IntArrayTag [x, y, z]
      blocksList.add(NbtUtils.writeBlockPos(pos));
    }
    tag.put("portalBlocks", blocksList);

    if (!frameBlocks.isEmpty()) {
      ListTag frameList = new ListTag();
      for (BlockPos pos : frameBlocks) {
        frameList.add(NbtUtils.writeBlockPos(pos));
      }
      tag.put("frameBlocks", frameList);
    }

    if (!storedCatalystStack.isEmpty()) {
      tag.put("storedCatalyst", storedCatalystStack.saveOptional(registries));
    }

    if (linkedPortalId != null) {
      tag.putUUID("linkedPortalId", linkedPortalId);
      tag.putString("linkedDimension", linkedDimension.location().toString());
    }

    tag.putInt("hasteCount", hasteCount);
    tag.putInt("gateCount", gateCount);
    tag.putInt("infinityCount", infinityCount);
    tag.putInt("weakEnhancers", weakEnhancerCount);
    tag.putInt("strongEnhancers", strongEnhancerCount);
    tag.putBoolean("redstoneDisabled", redstoneDisabled);
    tag.putBoolean("definitionDisabled", definitionDisabled);

    return tag;
  }

  /**
   * Deserialize a portal from NBT. Returns null if the data is malformed
   * so callers can skip the entry instead of crashing.
   */
  public static @Nullable CustomPortal load(
      CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    try {
      UUID id = tag.getUUID("id");
      ResourceLocation definitionId =
          tag.contains("definitionId")
              ? ResourceLocation.parse(tag.getString("definitionId"))
              : null;
      DyeColor color = DyeColor.byId(tag.getInt("color"));
      ResourceLocation frameMaterial = ResourceLocation.parse(tag.getString("frameMaterial"));
      ResourceKey<Level> dimension =
          ResourceKey.create(
              net.minecraft.core.registries.Registries.DIMENSION,
              ResourceLocation.parse(tag.getString("dimension")));

      BlockPos spawnPos =
          new BlockPos(tag.getInt("spawnX"), tag.getInt("spawnY"), tag.getInt("spawnZ"));
      Direction.Axis axis =
          tag.contains("axis") ? Direction.Axis.byName(tag.getString("axis")) : Direction.Axis.X;
      if (axis == null) {
        axis = Direction.Axis.X;
      }

      Set<BlockPos> portalBlocks = new HashSet<>();
      // portalBlocks is a list of IntArrayTag [x, y, z] NOT CompoundTag
      ListTag blocksList = tag.getList("portalBlocks", Tag.TAG_INT_ARRAY);
      for (int i = 0; i < blocksList.size(); i++) {
        int[] coords = blocksList.getIntArray(i);
        if (coords.length == 3) {
          portalBlocks.add(new BlockPos(coords[0], coords[1], coords[2]));
        }
      }

      Set<BlockPos> frameBlocks = new HashSet<>();
      ListTag frameList = tag.getList("frameBlocks", Tag.TAG_INT_ARRAY);
      for (int i = 0; i < frameList.size(); i++) {
        int[] coords = frameList.getIntArray(i);
        if (coords.length == 3) {
          frameBlocks.add(new BlockPos(coords[0], coords[1], coords[2]));
        }
      }

      CustomPortal portal =
          new CustomPortal(
              id,
              definitionId,
              color,
              frameMaterial,
              dimension,
              spawnPos,
              axis,
              portalBlocks,
              frameBlocks,
              ItemStack.parseOptional(registries, tag.getCompound("storedCatalyst")));

      if (tag.contains("linkedPortalId")) {
        portal.linkedPortalId = tag.getUUID("linkedPortalId");
        portal.linkedDimension =
            ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("linkedDimension")));
      }

      // rune counts; backward-compatible with old boolean format
      portal.hasteCount = tag.contains("hasteCount")
          ? tag.getInt("hasteCount")
          : (tag.getBoolean("hasHaste") ? 1 : 0);
      portal.gateCount = tag.contains("gateCount")
          ? tag.getInt("gateCount")
          : (tag.getBoolean("hasGate") ? 1 : 0);
      portal.infinityCount = tag.contains("infinityCount")
          ? tag.getInt("infinityCount")
          : (tag.getBoolean("hasInfinity") ? 1 : 0);
      portal.weakEnhancerCount = tag.getInt("weakEnhancers");
      portal.strongEnhancerCount = tag.getInt("strongEnhancers");
      portal.redstoneDisabled = tag.getBoolean("redstoneDisabled");
      portal.definitionDisabled = tag.getBoolean("definitionDisabled");

      return portal;
    } catch (Exception e) {
      return null;
    }
  }
}
