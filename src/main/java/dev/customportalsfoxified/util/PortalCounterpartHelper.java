package dev.customportalsfoxified.util;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModBlocks;
import dev.customportalsfoxified.blocks.CustomPortalBlock;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalLinkKey;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.portal.PortalDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public final class PortalCounterpartHelper {

  private static final String LOG_PREFIX = "[counterpart-resolve]";

  /** Forced-placement floor: vanilla never goes below Y=70 in the overworld. */
  private static final int SAFE_FORCED_FLOOR_Y = 70;

  /** Headroom above forced placement before bumping into the build ceiling. */
  private static final int SAFE_FORCED_CEILING_HEADROOM = 4;

  /**
   * Slop added to the find radius beyond {@code searchRadius + maxAutogenPortalWidth}.
   * Vanilla matches find-radius to create-radius which leaves a sliver of dead zone
   * where a player-built portal placed adjacent to an auto-generated one falls just
   * outside the find radius next entry.
   */
  private static final int FIND_RADIUS_BUFFER = 4;

  private PortalCounterpartHelper() {}

  public static @Nullable CustomPortal ensureCounterpartLinked(
      ServerLevel sourceLevel, CustomPortal sourcePortal, PortalDefinition definition) {
    if (!definition.usesCounterpartRoute()) return null;
    PortalDefinition.CounterpartRoute route = definition.counterpartRoute();
    if (route == null) return null;

    ResourceKey<Level> targetDimension = route.getCounterpartDimension(sourcePortal.getDimension());
    if (targetDimension == null) return null;

    ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetDimension);
    if (targetLevel == null) {
      CustomPortalsFoxified.LOGGER.warn(
          "Counterpart target dimension {} is not available for definition {}",
          targetDimension.location(),
          definition.id());
      return null;
    }

    CustomPortalsFoxified.LOGGER.info(
        "{} source={} dim={} spawn={} link={} target={}",
        LOG_PREFIX,
        sourcePortal.getId(),
        sourceLevel.dimension().location(),
        sourcePortal.getSpawnPos(),
        sourcePortal.linkDescriptor(),
        targetDimension.location());

    if (sourcePortal.isLinked()) {
      CustomPortal existingPartner =
          PortalSavedData.resolveLinkedPartner(sourcePortal, sourceLevel.getServer());
      if (existingPartner != null
          && !existingPartner.isDefinitionDisabled()
          && !existingPartner.isRedstoneDisabled()
          && route.supportsDimensionPair(sourcePortal.getDimension(), existingPartner.getDimension())) {
        CustomPortalsFoxified.LOGGER.info(
            "{} existing-partner-valid id={}", LOG_PREFIX, existingPartner.getId());
        return existingPartner;
      }
      CustomPortalsFoxified.LOGGER.info(
          "{} existing-partner-invalid (null={}, disabled={}, redstone={}, dim-pair={}) — unlinking",
          LOG_PREFIX,
          existingPartner == null,
          existingPartner != null && existingPartner.isDefinitionDisabled(),
          existingPartner != null && existingPartner.isRedstoneDisabled(),
          existingPartner == null
              ? "n/a"
              : route.supportsDimensionPair(sourcePortal.getDimension(), existingPartner.getDimension()));
      unlinkSourcePair(sourceLevel, sourcePortal);
    }

    BlockPos desiredTargetSpawn = route.transform(sourcePortal.getSpawnPos(), sourcePortal.getDimension());
    CustomPortal counterpart =
        findExistingCounterpart(targetLevel, sourcePortal, desiredTargetSpawn, route);
    CustomPortalsFoxified.LOGGER.info(
        "{} find-existing desired={} found={}",
        LOG_PREFIX,
        desiredTargetSpawn,
        counterpart != null ? counterpart.getId() + " @ " + counterpart.getSpawnPos() : "null");
    if (counterpart == null) {
      counterpart = createCounterpartPortal(sourceLevel, targetLevel, sourcePortal, definition, desiredTargetSpawn, route);
      CustomPortalsFoxified.LOGGER.info(
          "{} create-new result={}",
          LOG_PREFIX,
          counterpart != null ? counterpart.getId() + " @ " + counterpart.getSpawnPos() : "null");
    }
    if (counterpart == null) {
      CustomPortalBlock.updateLitState(sourceLevel, sourcePortal);
      PortalSavedData.get(sourceLevel).setDirty();
      return null;
    }

    // Many-to-one convergence: when the counterpart already points at another source,
    // use a unilateral forward link so multiple sources can share one destination
    // without clobbering its existing back-link (mirrors vanilla nether behavior).
    if (counterpart.isLinked()) {
      sourcePortal.linkTo(counterpart);
      CustomPortalsFoxified.LOGGER.info(
          "{} unilateral-linkTo source={} -> counterpart={} (counterpart back-link={})",
          LOG_PREFIX,
          sourcePortal.getId(),
          counterpart.getId(),
          counterpart.getLinkedPortalId());
    } else {
      sourcePortal.link(counterpart);
      CustomPortalsFoxified.LOGGER.info(
          "{} bilateral-link source={} <-> counterpart={}",
          LOG_PREFIX,
          sourcePortal.getId(),
          counterpart.getId());
    }
    PortalSavedData.get(sourceLevel).setDirty();
    PortalSavedData.get(targetLevel).setDirty();
    CustomPortalBlock.updateLitState(sourceLevel, sourcePortal);
    CustomPortalBlock.updateLitState(targetLevel, counterpart);
    return counterpart;
  }

  /**
   * Search for an existing counterpart portal in the target dimension. Mirrors vanilla
   * nether-portal many-to-one convergence: unlinked candidates are preferred, but an
   * already-linked candidate will be reused rather than carving a duplicate next to
   * it (which would risk destroying the existing portal's frame). Find radius is
   * asymmetric — see {@link #findRadiusFor}.
   */
  private static @Nullable CustomPortal findExistingCounterpart(
      ServerLevel targetLevel,
      CustomPortal sourcePortal,
      BlockPos desiredTargetSpawn,
      PortalDefinition.CounterpartRoute route) {
    int findRadius = findRadiusFor(route, targetLevel.dimension());
    PortalLinkKey lookupKey = PortalLinkKey.of(sourcePortal);
    List<CustomPortal> bucket =
        PortalSavedData.registry(targetLevel).getByLinkKey(lookupKey);
    CustomPortalsFoxified.LOGGER.info(
        "{} find-existing radius={} key={} bucket-size={}",
        LOG_PREFIX, findRadius, lookupKey, bucket.size());

    CustomPortal bestUnlinkedInY = null;
    double bestUnlinkedInYDistSq = Double.MAX_VALUE;
    CustomPortal bestLinkedInY = null;
    double bestLinkedInYDistSq = Double.MAX_VALUE;
    CustomPortal bestUnlinkedAnyY = null;
    double bestUnlinkedAnyYDistSq = Double.MAX_VALUE;
    CustomPortal bestLinkedAnyY = null;
    double bestLinkedAnyYDistSq = Double.MAX_VALUE;

    for (CustomPortal candidate : bucket) {
      if (candidate.getId().equals(sourcePortal.getId())) continue;
      String skipReason =
          candidateSkipReason(candidate, sourcePortal, route, desiredTargetSpawn, findRadius);
      if (skipReason != null) {
        CustomPortalsFoxified.LOGGER.info(
            "{} skip candidate {} ({})", LOG_PREFIX, candidate.getId(), skipReason);
        continue;
      }

      double distSq = candidate.getSpawnPos().distSqr(desiredTargetSpawn);
      boolean inVerticalWindow = isWithinVerticalWindow(candidate.getSpawnPos(), desiredTargetSpawn, route);
      if (!candidate.isLinked() && inVerticalWindow) {
        if (distSq < bestUnlinkedInYDistSq) {
          bestUnlinkedInY = candidate;
          bestUnlinkedInYDistSq = distSq;
        }
      } else if (candidate.isLinked() && inVerticalWindow) {
        if (distSq < bestLinkedInYDistSq) {
          bestLinkedInY = candidate;
          bestLinkedInYDistSq = distSq;
        }
      } else if (!candidate.isLinked()) {
        if (distSq < bestUnlinkedAnyYDistSq) {
          bestUnlinkedAnyY = candidate;
          bestUnlinkedAnyYDistSq = distSq;
        }
      } else {
        if (distSq < bestLinkedAnyYDistSq) {
          bestLinkedAnyY = candidate;
          bestLinkedAnyYDistSq = distSq;
        }
      }
    }

    if (bestUnlinkedInY != null) return bestUnlinkedInY;
    if (bestLinkedInY != null) return bestLinkedInY;
    if (bestUnlinkedAnyY != null) return bestUnlinkedAnyY;
    return bestLinkedAnyY;
  }

  private static @Nullable String candidateSkipReason(
      CustomPortal candidate,
      CustomPortal sourcePortal,
      PortalDefinition.CounterpartRoute route,
      BlockPos desiredTargetSpawn,
      int findRadius) {
    if (candidate.isDefinitionDisabled() || candidate.isRedstoneDisabled()) {
      return "disabled-def="
          + candidate.isDefinitionDisabled()
          + ", redstone="
          + candidate.isRedstoneDisabled();
    }
    if (!route.supportsDimensionPair(sourcePortal.getDimension(), candidate.getDimension())) {
      return "dim-pair mismatch source="
          + sourcePortal.getDimension().location()
          + " candidate="
          + candidate.getDimension().location();
    }
    if (!isWithinHorizontalRadius(candidate.getSpawnPos(), desiredTargetSpawn, findRadius)) {
      return "out of radius="
          + findRadius
          + ", candidate="
          + candidate.getSpawnPos()
          + ", desired="
          + desiredTargetSpawn;
    }
    return null;
  }

  private static @Nullable CustomPortal createCounterpartPortal(
      ServerLevel sourceLevel,
      ServerLevel targetLevel,
      CustomPortal sourcePortal,
      PortalDefinition definition,
      BlockPos desiredTargetSpawn,
      PortalDefinition.CounterpartRoute route) {
    PortalTemplate template = PortalTemplate.capture(sourceLevel, sourcePortal);
    if (template == null) return null;
    if (!route.allowsPortalShape(template.width(), template.height())) {
      CustomPortalsFoxified.LOGGER.debug(
          "Portal {} shape {}x{} exceeds counterpart auto-generation limits {}x{} (area {})",
          sourcePortal.getId(),
          template.width(),
          template.height(),
          route.maxAutogenPortalWidth(),
          route.maxAutogenPortalHeight(),
          route.maxAutogenPortalArea());
      return null;
    }

    Block frameMaterial = BuiltInRegistries.BLOCK.get(sourcePortal.getFrameMaterial());
    if (frameMaterial == null) return null;

    BlockPos desiredMin = desiredTargetSpawn.subtract(template.spawnOffset());
    PlacementPlan placement = findVanillaPlacement(targetLevel, template, desiredMin, route);
    if (placement == null) {
      CustomPortalsFoxified.LOGGER.debug(
          "No safe counterpart placement found for portal {} in {}",
          sourcePortal.getId(),
          targetLevel.dimension().location());
      return null;
    }

    BlockPos placementMin = placement.minPos();
    Set<BlockPos> portalBlocks = translateOffsets(placementMin, template.portalOffsets());
    Set<BlockPos> frameBlocks = translateOffsets(placementMin, template.frameOffsets());

    BlockState frameState = frameMaterial.defaultBlockState();

    // Forced placement: mirror vanilla PortalForcer.createPortal's no-spot-found branch.
    // Build a 1-block-thick floor under the frame footprint, then air-clear the frame
    // and portal volume + a 1-block clearing on each side along the front/back direction
    // so the counterpart is reachable. Frame and portal blocks are written afterwards
    // and overwrite whatever the clearing left behind.
    if (placement.forced()) {
      BlockState airState = Blocks.AIR.defaultBlockState();
      for (BlockPos pos : translateOffsets(placementMin, computeForcedFloorOffsets(template))) {
        targetLevel.setBlock(pos, frameState, 3);
      }
      for (BlockPos pos : translateOffsets(placementMin, computeForcedClearOffsets(template))) {
        targetLevel.setBlock(pos, airState, 3);
      }
    }

    for (BlockPos pos : frameBlocks) {
      targetLevel.setBlock(pos, frameState, 3);
    }

    BlockState portalState =
        ModBlocks.CUSTOM_PORTAL
            .get()
            .defaultBlockState()
            .setValue(CustomPortalBlock.COLOR, definition.color())
            .setValue(CustomPortalBlock.AXIS, template.axis())
            .setValue(CustomPortalBlock.LIT, false);
    for (BlockPos pos : portalBlocks) {
      targetLevel.setBlock(pos, portalState, 3);
    }

    CustomPortal counterpart =
        new CustomPortal(
            UUID.randomUUID(),
            definition.id(),
            definition.color(),
            sourcePortal.getFrameMaterial(),
            targetLevel.dimension(),
            placementMin.offset(template.spawnOffset()),
            template.axis(),
            portalBlocks,
            frameBlocks,
            net.minecraft.world.item.ItemStack.EMPTY);
    PortalSavedData data = PortalSavedData.get(targetLevel);
    data.getRegistry().registerPortal(counterpart);
    data.setDirty();
    return counterpart;
  }

  /**
   * Faithful port of vanilla {@link net.minecraft.world.level.portal.PortalForcer#createPortal}
   * generalized for variable portal shapes captured from the source portal. Falls back to
   * forced placement (clamped Y, caller air-clears) when no natural spot is found.
   */
  private static @Nullable PlacementPlan findVanillaPlacement(
      ServerLevel level,
      PortalTemplate template,
      BlockPos desiredMin,
      PortalDefinition.CounterpartRoute route) {
    WorldBorder border = level.getWorldBorder();
    int minY = level.getMinBuildHeight();
    int logicalTop =
        Math.min(level.getMaxBuildHeight(), minY + level.getLogicalHeight()) - 1;
    int maxYExclusive = logicalTop + 1;
    int searchRadius = Math.max(1, route.searchRadius());
    int portalHeight = template.height();

    BlockPos best = null;
    double bestDist = -1.0D;
    BlockPos fallback = null;
    double fallbackDist = -1.0D;

    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (BlockPos.MutableBlockPos spiralPos :
        BlockPos.spiralAround(desiredMin, searchRadius, Direction.EAST, Direction.SOUTH)) {
      if (!border.isWithinBounds(spiralPos)) continue;
      int columnTop =
          Math.min(logicalTop, level.getHeight(Heightmap.Types.MOTION_BLOCKING, spiralPos.getX(), spiralPos.getZ()));

      for (int l = columnTop; l >= minY; l--) {
        cursor.set(spiralPos.getX(), l, spiralPos.getZ());
        if (!canPortalReplaceBlock(level, cursor)) continue;

        // Slide down through the contiguous replaceable run; vanilla bottoms out on
        // a flat floor (run == 0) or accepts an open shaft taller than the portal.
        int top = l;
        while (l > minY) {
          cursor.set(spiralPos.getX(), l - 1, spiralPos.getZ());
          if (!canPortalReplaceBlock(level, cursor)) break;
          l--;
        }
        if (l + portalHeight + 1 > logicalTop) continue;
        int run = top - l;
        if (run > 0 && run < portalHeight) continue;

        BlockPos anchor = new BlockPos(spiralPos.getX(), l, spiralPos.getZ());
        if (canHostFrame(level, template, anchor, 0, minY, maxYExclusive)) {
          double d2 = desiredMin.distSqr(anchor);
          if (canHostFrame(level, template, anchor, -1, minY, maxYExclusive)
              && canHostFrame(level, template, anchor, 1, minY, maxYExclusive)
              && (bestDist == -1.0D || d2 < bestDist)) {
            bestDist = d2;
            best = anchor;
          }
          if (bestDist == -1.0D && (fallbackDist == -1.0D || d2 < fallbackDist)) {
            fallbackDist = d2;
            fallback = anchor;
          }
        }
      }
    }

    BlockPos chosen = best != null ? best : fallback;
    if (chosen != null) {
      return new PlacementPlan(chosen, false);
    }

    int safeFloorY = Math.max(minY + 1, SAFE_FORCED_FLOOR_Y);
    int safeCeilY = logicalTop - (portalHeight + SAFE_FORCED_CEILING_HEADROOM);
    if (safeCeilY < safeFloorY) return null;
    int forcedY = Mth.clamp(desiredMin.getY(), safeFloorY, safeCeilY);
    BlockPos forcedAnchor = new BlockPos(desiredMin.getX(), forcedY, desiredMin.getZ());
    if (!border.isWithinBounds(forcedAnchor)) return null;
    return new PlacementPlan(forcedAnchor, true);
  }

  /**
   * Mirrors vanilla {@code PortalForcer.canHostFrame}. For vertical portals the frame's
   * bottom row must be solid (its floor — overwritten by frame placement afterwards);
   * every other frame slot + portal slot must be replaceable. For Y-axis portals all
   * slots are required to be replaceable. {@code lateralOffsetScale} shifts the test
   * along the front/back direction so the chosen spot has standing room on both sides.
   */
  private static boolean canHostFrame(
      ServerLevel level,
      PortalTemplate template,
      BlockPos anchor,
      int lateralOffsetScale,
      int minY,
      int maxYExclusive) {
    BlockPos shifted = anchor.relative(template.frontDirection(), lateralOffsetScale);
    boolean hasFloorRow = template.axis() != Direction.Axis.Y;
    int floorRowY = template.lowestOccupiedY();

    for (BlockPos offset : template.frameOffsets()) {
      BlockPos pos = shifted.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return false;

      if (hasFloorRow && offset.getY() == floorRowY) {
        if (!level.getBlockState(pos).isSolid()) return false;
      } else {
        if (!canPortalReplaceBlock(level, pos)) return false;
      }
    }
    for (BlockPos offset : template.portalOffsets()) {
      BlockPos pos = shifted.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return false;
      if (!canPortalReplaceBlock(level, pos)) return false;
    }
    return true;
  }

  /** Mirrors vanilla {@code PortalForcer.canPortalReplaceBlock}; rejects existing portals so we never carve through one. */
  private static boolean canPortalReplaceBlock(ServerLevel level, BlockPos pos) {
    BlockState state = level.getBlockState(pos);
    if (state.getBlock() instanceof CustomPortalBlock) return false;
    return state.canBeReplaced() && state.getFluidState().isEmpty();
  }

  /** Frame-bottom-row offsets the forced placement writes as solid floor; empty for Y-axis portals. */
  private static Set<BlockPos> computeForcedFloorOffsets(PortalTemplate template) {
    if (template.axis() == Direction.Axis.Y) return Set.of();
    int floorRowY = template.lowestOccupiedY();
    Set<BlockPos> floor = new HashSet<>();
    for (BlockPos offset : template.frameOffsets()) {
      if (offset.getY() == floorRowY) floor.add(offset);
    }
    return floor;
  }

  /**
   * Air-clear offsets for the forced placement: the frame+portal bounding box at
   * {@code y >= 0}, expanded one block front/back for vertical portals so the player
   * has standing space. Frame and portal placements overwrite the relevant slots after.
   */
  private static Set<BlockPos> computeForcedClearOffsets(PortalTemplate template) {
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
    for (BlockPos o : template.frameOffsets()) {
      minX = Math.min(minX, o.getX()); maxX = Math.max(maxX, o.getX());
      minY = Math.min(minY, o.getY()); maxY = Math.max(maxY, o.getY());
      minZ = Math.min(minZ, o.getZ()); maxZ = Math.max(maxZ, o.getZ());
    }
    for (BlockPos o : template.portalOffsets()) {
      minX = Math.min(minX, o.getX()); maxX = Math.max(maxX, o.getX());
      minY = Math.min(minY, o.getY()); maxY = Math.max(maxY, o.getY());
      minZ = Math.min(minZ, o.getZ()); maxZ = Math.max(maxZ, o.getZ());
    }

    int frontX = template.frontDirection().getStepX();
    int frontZ = template.frontDirection().getStepZ();
    boolean horizontalPortal = template.axis() != Direction.Axis.Y;

    Set<BlockPos> clear = new HashSet<>();
    for (int y = Math.max(minY, 0); y <= maxY; y++) {
      for (int x = minX; x <= maxX; x++) {
        for (int z = minZ; z <= maxZ; z++) {
          clear.add(new BlockPos(x, y, z));
          if (horizontalPortal) {
            clear.add(new BlockPos(x + frontX, y, z + frontZ));
            clear.add(new BlockPos(x - frontX, y, z - frontZ));
          }
        }
      }
    }
    return clear;
  }

  /**
   * Find radius for existing counterparts. Asymmetric to mirror vanilla (128 in
   * overworld vs 16 in nether for 8× scale): the "larger" side scales by
   * {@code coordinateScale}. {@link #FIND_RADIUS_BUFFER} closes the dead-zone where
   * an adjacent player-built portal would otherwise fall outside the find radius
   * after auto-gen drifted to the spiral edge.
   */
  private static int findRadiusFor(
      PortalDefinition.CounterpartRoute route, ResourceKey<Level> targetDim) {
    int smallSideRadius =
        route.searchRadius() + route.maxAutogenPortalWidth() + FIND_RADIUS_BUFFER;
    if (route.dimensionA().equals(targetDim)) {
      return Math.max(1, (int) Math.ceil(smallSideRadius * route.coordinateScale()));
    }
    return Math.max(1, smallSideRadius);
  }

  private static boolean isWithinHorizontalRadius(BlockPos candidate, BlockPos desired, int radius) {
    int dx = Math.abs(candidate.getX() - desired.getX());
    int dz = Math.abs(candidate.getZ() - desired.getZ());
    return dx <= radius && dz <= radius;
  }

  private static boolean isWithinVerticalWindow(
      BlockPos candidate, BlockPos desired, PortalDefinition.CounterpartRoute route) {
    return Math.abs(candidate.getY() - desired.getY()) <= route.verticalSearchRadius();
  }

  private static void unlinkSourcePair(ServerLevel level, CustomPortal portal) {
    CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, level.getServer());
    if (partner != null) {
      portal.unlinkFrom(partner);
      ServerLevel partnerLevel = level.getServer().getLevel(partner.getDimension());
      if (partnerLevel != null) {
        CustomPortalBlock.updateLitState(partnerLevel, partner);
        PortalSavedData.get(partnerLevel).setDirty();
      }
    } else {
      portal.unlink();
    }
    CustomPortalBlock.updateLitState(level, portal);
  }

  private static Set<BlockPos> translateOffsets(BlockPos minPos, Set<BlockPos> offsets) {
    Set<BlockPos> positions = new HashSet<>();
    for (BlockPos offset : offsets) {
      positions.add(minPos.offset(offset));
    }
    return positions;
  }

  private record PortalTemplate(
      Direction.Axis axis,
      int width,
      int height,
      int lowestOccupiedY,
      Set<BlockPos> portalOffsets,
      Set<BlockPos> frameOffsets,
      Direction frontDirection,
      BlockPos spawnOffset) {

    private static @Nullable PortalTemplate capture(ServerLevel level, CustomPortal portal) {
      Direction.Axis axis = null;
      for (BlockPos portalPos : portal.getPortalBlocks()) {
        BlockState state = level.getBlockState(portalPos);
        if (state.getBlock() instanceof CustomPortalBlock) {
          axis = state.getValue(CustomPortalBlock.AXIS);
          break;
        }
      }
      if (axis == null) return null;

      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      for (BlockPos pos : portal.getPortalBlocks()) {
        minX = Math.min(minX, pos.getX());
        minY = Math.min(minY, pos.getY());
        minZ = Math.min(minZ, pos.getZ());
      }

      BlockPos minPos = new BlockPos(minX, minY, minZ);
      Set<BlockPos> portalOffsets = new HashSet<>();
      for (BlockPos pos : portal.getPortalBlocks()) {
        portalOffsets.add(pos.subtract(minPos));
      }

      Set<BlockPos> frameOffsets = new HashSet<>();
      if (!portal.getFrameBlocks().isEmpty()) {
        for (BlockPos pos : portal.getFrameBlocks()) {
          frameOffsets.add(pos.subtract(minPos));
        }
      } else {
        Set<BlockPos> portalBlocks = new HashSet<>(portal.getPortalBlocks());
        for (BlockPos portalPos : portal.getPortalBlocks()) {
          for (BlockPos neighbor : getAdjacentOnPlane(portalPos, axis)) {
            if (!portalBlocks.contains(neighbor)) {
              frameOffsets.add(neighbor.subtract(minPos));
            }
          }
        }
      }

      int minPortalX = Integer.MAX_VALUE;
      int maxPortalX = Integer.MIN_VALUE;
      int minPortalY = Integer.MAX_VALUE;
      int maxPortalY = Integer.MIN_VALUE;
      int minPortalZ = Integer.MAX_VALUE;
      int maxPortalZ = Integer.MIN_VALUE;
      for (BlockPos pos : portal.getPortalBlocks()) {
        minPortalX = Math.min(minPortalX, pos.getX());
        maxPortalX = Math.max(maxPortalX, pos.getX());
        minPortalY = Math.min(minPortalY, pos.getY());
        maxPortalY = Math.max(maxPortalY, pos.getY());
        minPortalZ = Math.min(minPortalZ, pos.getZ());
        maxPortalZ = Math.max(maxPortalZ, pos.getZ());
      }

      addPortalCorners(frameOffsets, minPos, axis, minPortalX, maxPortalX, minPortalY, maxPortalY, minPortalZ, maxPortalZ);

      int lowestOccupiedOffsetY = Integer.MAX_VALUE;
      for (BlockPos offset : frameOffsets) {
        lowestOccupiedOffsetY = Math.min(lowestOccupiedOffsetY, offset.getY());
      }
      for (BlockPos offset : portalOffsets) {
        lowestOccupiedOffsetY = Math.min(lowestOccupiedOffsetY, offset.getY());
      }

      Direction frontDirection =
          switch (axis) {
            case X -> Direction.SOUTH;
            case Z -> Direction.EAST;
            case Y -> Direction.UP;
          };

      int[] dimensions =
          switch (axis) {
            case X -> new int[] {maxPortalX - minPortalX + 1, maxPortalY - minPortalY + 1};
            case Z -> new int[] {maxPortalZ - minPortalZ + 1, maxPortalY - minPortalY + 1};
            case Y -> new int[] {maxPortalX - minPortalX + 1, maxPortalZ - minPortalZ + 1};
          };

      return new PortalTemplate(
          axis,
          dimensions[0],
          dimensions[1],
          lowestOccupiedOffsetY,
          portalOffsets,
          frameOffsets,
          frontDirection,
          portal.getSpawnPos().subtract(minPos));
    }

    private static void addPortalCorners(
        Set<BlockPos> frameOffsets,
        BlockPos minPos,
        Direction.Axis axis,
        int minPortalX,
        int maxPortalX,
        int minPortalY,
        int maxPortalY,
        int minPortalZ,
        int maxPortalZ) {
      switch (axis) {
        case X -> {
          int z = minPortalZ;
          frameOffsets.add(new BlockPos(minPortalX - 1, minPortalY - 1, z).subtract(minPos));
          frameOffsets.add(new BlockPos(maxPortalX + 1, minPortalY - 1, z).subtract(minPos));
          frameOffsets.add(new BlockPos(minPortalX - 1, maxPortalY + 1, z).subtract(minPos));
          frameOffsets.add(new BlockPos(maxPortalX + 1, maxPortalY + 1, z).subtract(minPos));
        }
        case Z -> {
          int x = minPortalX;
          frameOffsets.add(new BlockPos(x, minPortalY - 1, minPortalZ - 1).subtract(minPos));
          frameOffsets.add(new BlockPos(x, minPortalY - 1, maxPortalZ + 1).subtract(minPos));
          frameOffsets.add(new BlockPos(x, maxPortalY + 1, minPortalZ - 1).subtract(minPos));
          frameOffsets.add(new BlockPos(x, maxPortalY + 1, maxPortalZ + 1).subtract(minPos));
        }
        case Y -> {
          int y = minPortalY;
          frameOffsets.add(new BlockPos(minPortalX - 1, y, minPortalZ - 1).subtract(minPos));
          frameOffsets.add(new BlockPos(maxPortalX + 1, y, minPortalZ - 1).subtract(minPos));
          frameOffsets.add(new BlockPos(minPortalX - 1, y, maxPortalZ + 1).subtract(minPos));
          frameOffsets.add(new BlockPos(maxPortalX + 1, y, maxPortalZ + 1).subtract(minPos));
        }
      }
    }

    private static List<BlockPos> getAdjacentOnPlane(BlockPos pos, Direction.Axis axis) {
      return switch (axis) {
        case X -> List.of(pos.above(), pos.below(), pos.east(), pos.west());
        case Z -> List.of(pos.above(), pos.below(), pos.north(), pos.south());
        case Y -> List.of(pos.east(), pos.west(), pos.north(), pos.south());
      };
    }
  }

  private record PlacementPlan(BlockPos minPos, boolean forced) {}
}
