package dev.customportalsfoxified.util;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModBlocks;
import dev.customportalsfoxified.blocks.CustomPortalBlock;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalLinkKey;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.portal.PortalDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public final class PortalCounterpartHelper {

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

    if (sourcePortal.isLinked()) {
      CustomPortal existingPartner =
          PortalSavedData.resolveLinkedPartner(sourcePortal, sourceLevel.getServer());
      if (existingPartner != null
          && !existingPartner.isDefinitionDisabled()
          && !existingPartner.isRedstoneDisabled()
          && route.supportsDimensionPair(sourcePortal.getDimension(), existingPartner.getDimension())) {
        return existingPartner;
      }
      unlinkSourcePair(sourceLevel, sourcePortal);
    }

    BlockPos desiredTargetSpawn = route.transform(sourcePortal.getSpawnPos(), sourcePortal.getDimension());
    CustomPortal counterpart =
        findExistingCounterpart(targetLevel, sourcePortal, desiredTargetSpawn, route);
    if (counterpart == null) {
      counterpart = createCounterpartPortal(sourceLevel, targetLevel, sourcePortal, definition, desiredTargetSpawn, route);
    }
    if (counterpart == null) {
      CustomPortalBlock.updateLitState(sourceLevel, sourcePortal);
      PortalSavedData.get(sourceLevel).setDirty();
      return null;
    }

    // Many-to-one convergence: if the counterpart is already linked to another
    // source portal, use a unilateral forward link so multiple sources share one
    // destination without clobbering the counterpart's existing back-link.
    // Mirrors vanilla nether portal behavior.
    if (counterpart.isLinked()) {
      sourcePortal.linkTo(counterpart);
    } else {
      sourcePortal.link(counterpart);
    }
    PortalSavedData.get(sourceLevel).setDirty();
    PortalSavedData.get(targetLevel).setDirty();
    CustomPortalBlock.updateLitState(sourceLevel, sourcePortal);
    CustomPortalBlock.updateLitState(targetLevel, counterpart);
    return counterpart;
  }

  /**
   * Search for an existing counterpart portal in the target dimension. Mirrors vanilla
   * nether portal behavior: multiple source portals may converge on a single
   * destination portal (many-to-one). Unlinked portals are preferred, but an
   * already-linked portal will be reused rather than creating a duplicate.
   */
  private static @Nullable CustomPortal findExistingCounterpart(
      ServerLevel targetLevel,
      CustomPortal sourcePortal,
      BlockPos desiredTargetSpawn,
      PortalDefinition.CounterpartRoute route) {
    CustomPortal bestUnlinkedInY = null;
    double bestUnlinkedInYDistSq = Double.MAX_VALUE;
    CustomPortal bestLinkedInY = null;
    double bestLinkedInYDistSq = Double.MAX_VALUE;
    CustomPortal bestUnlinkedAnyY = null;
    double bestUnlinkedAnyYDistSq = Double.MAX_VALUE;
    CustomPortal bestLinkedAnyY = null;
    double bestLinkedAnyYDistSq = Double.MAX_VALUE;

    for (CustomPortal candidate :
        PortalSavedData.registry(targetLevel).getByLinkKey(PortalLinkKey.of(sourcePortal))) {
      if (candidate.getId().equals(sourcePortal.getId())) continue;
      if (candidate.isDefinitionDisabled() || candidate.isRedstoneDisabled()) continue;
      if (!route.supportsDimensionPair(sourcePortal.getDimension(), candidate.getDimension())) continue;
      if (!isWithinRouteColumn(candidate.getSpawnPos(), desiredTargetSpawn, route)) continue;

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
    PlacementPlan placement = findPlacement(targetLevel, template, frameMaterial, desiredMin, route);
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

    BlockState airState = Blocks.AIR.defaultBlockState();
    for (BlockPos pos : translateOffsets(placementMin, placement.clearOffsets())) {
      targetLevel.setBlock(pos, airState, 3);
    }

    BlockState frameState = frameMaterial.defaultBlockState();
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

  private static @Nullable PlacementPlan findPlacement(
      ServerLevel level,
      PortalTemplate template,
      Block frameMaterial,
      BlockPos desiredMin,
      PortalDefinition.CounterpartRoute route) {
    List<BlockPos> horizontalOffsets = buildHorizontalOffsets(route.searchRadius());
    BlockPos bestPos = null;
    PlacementScore bestScore = null;

    for (BlockPos horizontalOffset : horizontalOffsets) {
      int candidateX = desiredMin.getX() + horizontalOffset.getX();
      int candidateZ = desiredMin.getZ() + horizontalOffset.getZ();
      int logicalTopY =
          Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 1;
      int topY =
          Math.min(
              logicalTopY,
              level.getHeight(Heightmap.Types.MOTION_BLOCKING, candidateX, candidateZ));

      for (int candidateY = topY; candidateY >= level.getMinBuildHeight(); candidateY--) {
        BlockPos candidateMin = new BlockPos(candidateX, candidateY, candidateZ);
        PlacementScore score = scorePlacement(level, template, frameMaterial, candidateMin, desiredMin);
        if (score == null) {
          continue;
        }

        if (bestScore == null || score.compareTo(bestScore) > 0) {
          bestScore = score;
          bestPos = candidateMin;
        }
      }
    }

    if (bestPos == null && route.verticalSearchRadius() > 0) {
      List<Integer> verticalOffsets = buildVerticalOffsets(route.verticalSearchRadius());
      for (BlockPos horizontalOffset : horizontalOffsets) {
        for (int verticalOffset : verticalOffsets) {
          BlockPos candidateMin =
              desiredMin.offset(horizontalOffset.getX(), verticalOffset, horizontalOffset.getZ());
          PlacementScore score = scorePlacement(level, template, frameMaterial, candidateMin, desiredMin);
          if (score == null) {
            continue;
          }

          if (bestScore == null || score.compareTo(bestScore) > 0) {
            bestScore = score;
            bestPos = candidateMin;
          }
        }
      }
    }

    if (bestPos != null) {
      return new PlacementPlan(bestPos, Set.of());
    }
    return findCarvedPlacement(level, template, frameMaterial, desiredMin, route);
  }

  private static @Nullable PlacementScore scorePlacement(
      ServerLevel level,
      PortalTemplate template,
      Block frameMaterial,
      BlockPos minPos,
      BlockPos desiredMin) {
    int minY = level.getMinBuildHeight();
    int maxYExclusive =
        Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight());
    int carvedBlocks = 0;

    for (BlockPos offset : template.frameOffsets()) {
      BlockPos pos = minPos.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return null;

      BlockState state = level.getBlockState(pos);
      if (state.is(frameMaterial)) continue;
      if (!canReplaceForCounterpart(level, pos, state)) return null;
      if (!state.isAir()) carvedBlocks++;
    }

    for (BlockPos offset : template.portalOffsets()) {
      BlockPos pos = minPos.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return null;

      BlockState state = level.getBlockState(pos);
      if (!canReplaceForCounterpart(level, pos, state)) return null;
      if (!state.isAir()) carvedBlocks++;
    }

    int supportScore = computeSupportScore(level, template, minPos);
    if (supportScore < template.requiredSupportCount()) {
      return null;
    }

    int exitScore = computeExitSafetyScore(level, template, minPos);
    if (template.axis() != Direction.Axis.Y && exitScore <= 0) {
      return null;
    }

    int distancePenalty =
        Math.abs(minPos.getX() - desiredMin.getX())
            + Math.abs(minPos.getY() - desiredMin.getY())
            + Math.abs(minPos.getZ() - desiredMin.getZ());

    return new PlacementScore(exitScore, supportScore, -carvedBlocks, -distancePenalty);
  }

  /**
   * Mirrors vanilla {@code PortalForcer.canPortalReplaceBlock}: only blocks that are
   * explicitly replaceable (air, snow layers, tall grass, etc.) and contain no fluid
   * may be overwritten. This prevents the placer from carving through solid terrain
   * (stone, netherrack, etc.) to brute-force a technically valid but practically
   * unusable placement.
   */
  private static boolean canReplaceForCounterpart(
      ServerLevel level, BlockPos pos, BlockState state) {
    if (state.getBlock() instanceof CustomPortalBlock) return false;
    return state.canBeReplaced() && state.getFluidState().isEmpty();
  }

  private static @Nullable PlacementPlan findCarvedPlacement(
      ServerLevel level,
      PortalTemplate template,
      Block frameMaterial,
      BlockPos desiredMin,
      PortalDefinition.CounterpartRoute route) {
    List<BlockPos> horizontalOffsets = buildHorizontalOffsets(route.searchRadius());
    List<Integer> verticalOffsets = buildVerticalOffsets(route.verticalSearchRadius());
    PlacementPlan bestPlan = null;
    PlacementScore bestScore = null;

    for (BlockPos horizontalOffset : horizontalOffsets) {
      for (int verticalOffset : verticalOffsets) {
        BlockPos candidateMin =
            desiredMin.offset(horizontalOffset.getX(), verticalOffset, horizontalOffset.getZ());
        PlacementPlanScore planScore =
            scoreCarvedPlacement(level, template, frameMaterial, candidateMin, desiredMin);
        if (planScore == null) {
          continue;
        }

        if (bestScore == null || planScore.score().compareTo(bestScore) > 0) {
          bestScore = planScore.score();
          bestPlan = planScore.plan();
        }
      }
    }

    return bestPlan;
  }

  private static @Nullable PlacementPlanScore scoreCarvedPlacement(
      ServerLevel level,
      PortalTemplate template,
      Block frameMaterial,
      BlockPos minPos,
      BlockPos desiredMin) {
    int minY = level.getMinBuildHeight();
    int maxYExclusive =
        Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight());
    int carvedBlocks = 0;

    for (BlockPos offset : template.frameOffsets()) {
      BlockPos pos = minPos.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return null;

      BlockState state = level.getBlockState(pos);
      if (state.is(frameMaterial)) continue;
      if (!canCarveForCounterpart(level, pos, state)) return null;
      if (!state.isAir()) carvedBlocks++;
    }

    for (BlockPos offset : template.portalOffsets()) {
      BlockPos pos = minPos.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return null;

      BlockState state = level.getBlockState(pos);
      if (!canCarveForCounterpart(level, pos, state)) return null;
      if (!state.isAir()) carvedBlocks++;
    }

    int supportScore = computeSupportScore(level, template, minPos);
    if (supportScore < template.requiredSupportCount()) {
      return null;
    }

    Set<BlockPos> clearOffsets = findCarvedExitOffsets(level, template, minPos);
    if (clearOffsets == null) {
      return null;
    }

    for (BlockPos offset : clearOffsets) {
      BlockPos pos = minPos.offset(offset);
      if (pos.getY() < minY || pos.getY() >= maxYExclusive) return null;

      BlockState state = level.getBlockState(pos);
      if (!canCarveForCounterpart(level, pos, state)) return null;
      if (!state.isAir()) carvedBlocks++;
    }

    int distancePenalty =
        Math.abs(minPos.getX() - desiredMin.getX())
            + Math.abs(minPos.getY() - desiredMin.getY())
            + Math.abs(minPos.getZ() - desiredMin.getZ());
    PlacementScore score =
        new PlacementScore(clearOffsets.size(), supportScore, -carvedBlocks, -distancePenalty);
    return new PlacementPlanScore(new PlacementPlan(minPos, clearOffsets), score);
  }

  private static boolean canCarveForCounterpart(ServerLevel level, BlockPos pos, BlockState state) {
    if (state.getBlock() instanceof CustomPortalBlock) return false;
    if (state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced())) return true;
    if (!state.getFluidState().isEmpty()) return false;
    if (state.hasBlockEntity()) return false;
    return state.getDestroySpeed(level, pos) >= 0.0F;
  }

  private static @Nullable Set<BlockPos> findCarvedExitOffsets(
      ServerLevel level, PortalTemplate template, BlockPos minPos) {
    if (template.axis() == Direction.Axis.Y) {
      Set<BlockPos> clearOffsets = buildHorizontalExitOffsets(template);
      return canCarveAll(level, minPos, clearOffsets) ? clearOffsets : null;
    }

    Set<BlockPos> frontOffsets = buildVerticalExitOffsets(template, template.frontDirection());
    boolean frontClear = canCarveAll(level, minPos, frontOffsets);
    Set<BlockPos> backOffsets = buildVerticalExitOffsets(template, template.frontDirection().getOpposite());
    boolean backClear = canCarveAll(level, minPos, backOffsets);

    if (frontClear && backClear) {
      return countCarvedBlocks(level, minPos, frontOffsets) <= countCarvedBlocks(level, minPos, backOffsets)
          ? frontOffsets
          : backOffsets;
    }
    if (frontClear) return frontOffsets;
    return backClear ? backOffsets : null;
  }

  private static Set<BlockPos> buildHorizontalExitOffsets(PortalTemplate template) {
    Set<BlockPos> clearOffsets = new HashSet<>();
    for (BlockPos portalOffset : template.portalOffsets()) {
      for (int dy = 1; dy <= 3; dy++) {
        clearOffsets.add(portalOffset.above(dy));
      }
    }
    return clearOffsets;
  }

  private static Set<BlockPos> buildVerticalExitOffsets(PortalTemplate template, Direction side) {
    Set<BlockPos> clearOffsets = new HashSet<>();
    BlockPos spawn = template.spawnOffset();
    Direction lateral = template.lateralDirection();
    for (int depth = 1; depth <= 3; depth++) {
      for (int lateralOffset = -1; lateralOffset <= 1; lateralOffset++) {
        for (int dy = 0; dy <= 2; dy++) {
          clearOffsets.add(spawn.relative(side, depth).relative(lateral, lateralOffset).above(dy));
        }
      }
    }
    return clearOffsets;
  }

  private static boolean canCarveAll(ServerLevel level, BlockPos minPos, Set<BlockPos> offsets) {
    for (BlockPos offset : offsets) {
      BlockPos pos = minPos.offset(offset);
      if (!canCarveForCounterpart(level, pos, level.getBlockState(pos))) {
        return false;
      }
    }
    return true;
  }

  private static int countCarvedBlocks(ServerLevel level, BlockPos minPos, Set<BlockPos> offsets) {
    int carvedBlocks = 0;
    for (BlockPos offset : offsets) {
      if (!level.getBlockState(minPos.offset(offset)).isAir()) {
        carvedBlocks++;
      }
    }
    return carvedBlocks;
  }

  private static int computeSupportScore(ServerLevel level, PortalTemplate template, BlockPos minPos) {
    int support = 0;
    for (BlockPos offset : template.supportOffsets()) {
      BlockPos belowPos = minPos.offset(offset);
      if (level.getBlockState(belowPos).isSolid()) {
        support++;
      }
    }
    return support;
  }

  private static int computeExitSafetyScore(ServerLevel level, PortalTemplate template, BlockPos minPos) {
    if (template.axis() == Direction.Axis.Y) {
      BlockPos spawn = minPos.offset(template.spawnOffset());
      int score = 0;
      for (int dy = 1; dy <= 3; dy++) {
        BlockState state = level.getBlockState(spawn.above(dy));
        if (state.isAir() || state.canBeReplaced()) {
          score++;
        }
      }
      return score;
    }

    Direction normalA = template.frontDirection();
    Direction normalB = normalA.getOpposite();
    return Math.max(
        scoreExitSide(level, template, minPos, normalA), scoreExitSide(level, template, minPos, normalB));
  }

  private static int scoreExitSide(
      ServerLevel level, PortalTemplate template, BlockPos minPos, Direction side) {
    BlockPos spawn = minPos.offset(template.spawnOffset());
    Direction lateral = template.lateralDirection();
    int score = 0;

    for (int lateralOffset = -1; lateralOffset <= 1; lateralOffset++) {
      BlockPos floorPos = spawn.relative(side).relative(lateral, lateralOffset);
      if (!level.getBlockState(floorPos.below()).isSolid()) {
        return -1;
      }

      for (int dy = 0; dy <= 1; dy++) {
        BlockState state = level.getBlockState(floorPos.above(dy));
        if (!(state.isAir() || state.canBeReplaced())) {
          return -1;
        }
        score++;
      }
    }

    for (int depth = 2; depth <= 3; depth++) {
      BlockPos ahead = spawn.relative(side, depth);
      for (int dy = 0; dy <= 1; dy++) {
        BlockState state = level.getBlockState(ahead.above(dy));
        if (state.isAir() || state.canBeReplaced()) {
          score++;
        }
      }
    }

    return score;
  }

  private static boolean isWithinRouteColumn(
      BlockPos candidate, BlockPos desired, PortalDefinition.CounterpartRoute route) {
    int dx = Math.abs(candidate.getX() - desired.getX());
    int dz = Math.abs(candidate.getZ() - desired.getZ());
    return dx <= route.searchRadius() && dz <= route.searchRadius();
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

  private static List<BlockPos> buildHorizontalOffsets(int radius) {
    List<BlockPos> offsets = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        offsets.add(new BlockPos(dx, 0, dz));
      }
    }
    offsets.sort(Comparator.comparingInt(offset -> offset.getX() * offset.getX() + offset.getZ() * offset.getZ()));
    return offsets;
  }

  private static List<Integer> buildVerticalOffsets(int radius) {
    List<Integer> offsets = new ArrayList<>();
    offsets.add(0);
    for (int delta = 1; delta <= radius; delta++) {
      offsets.add(delta);
      offsets.add(-delta);
    }
    return offsets;
  }

  private record PortalTemplate(
      Direction.Axis axis,
      int width,
      int height,
      Set<BlockPos> portalOffsets,
      Set<BlockPos> frameOffsets,
      Set<BlockPos> supportOffsets,
      Direction frontDirection,
      Direction lateralDirection,
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

      Set<BlockPos> supportOffsets = new HashSet<>();
      int lowestOccupiedOffsetY = Integer.MAX_VALUE;
      for (BlockPos offset : frameOffsets) {
        lowestOccupiedOffsetY = Math.min(lowestOccupiedOffsetY, offset.getY());
      }
      for (BlockPos offset : portalOffsets) {
        lowestOccupiedOffsetY = Math.min(lowestOccupiedOffsetY, offset.getY());
      }

      Set<BlockPos> supportTargets = new HashSet<>();
      supportTargets.addAll(frameOffsets);
      supportTargets.addAll(portalOffsets);
      for (BlockPos offset : supportTargets) {
        if (offset.getY() == lowestOccupiedOffsetY) {
          supportOffsets.add(offset.below());
        }
      }

      Direction frontDirection =
          switch (axis) {
            case X -> Direction.SOUTH;
            case Z -> Direction.EAST;
            case Y -> Direction.UP;
          };
      Direction lateralDirection =
          switch (axis) {
            case X -> Direction.EAST;
            case Z -> Direction.SOUTH;
            case Y -> Direction.EAST;
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
          portalOffsets,
          frameOffsets,
          supportOffsets,
          frontDirection,
          lateralDirection,
          portal.getSpawnPos().subtract(minPos));
    }

    private int requiredSupportCount() {
      return supportOffsets.size();
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

  private record PlacementScore(
      int exitScore, int supportScore, int carveScore, int distanceScore) implements Comparable<PlacementScore> {

    @Override
    public int compareTo(PlacementScore other) {
      int compare = Integer.compare(exitScore, other.exitScore);
      if (compare != 0) return compare;

      compare = Integer.compare(supportScore, other.supportScore);
      if (compare != 0) return compare;

      compare = Integer.compare(carveScore, other.carveScore);
      if (compare != 0) return compare;

      return Integer.compare(distanceScore, other.distanceScore);
    }
  }

  private record PlacementPlan(BlockPos minPos, Set<BlockPos> clearOffsets) {}

  private record PlacementPlanScore(PlacementPlan plan, PlacementScore score) {}
}
