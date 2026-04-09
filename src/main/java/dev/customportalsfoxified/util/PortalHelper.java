package dev.customportalsfoxified.util;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModBlocks;
import dev.customportalsfoxified.blocks.AbstractRuneBlock;
import dev.customportalsfoxified.blocks.CustomPortalBlock;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.data.RuneType;
import dev.customportalsfoxified.portal.PortalDefinition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.kwahson.core.config.SafeConfig;

public class PortalHelper {

  private PortalHelper() {}

  /**
   * Attempt to build a portal starting from an air block adjacent to a frame block.
   *
   * @param level the server level
   * @param airPos the air block position to start the DFS from
   * @param framePos a known frame block position (to identify the frame material)
   * @param definition resolved portal definition
   * @return true if a portal was successfully built
   */
  public static boolean buildPortal(
      ServerLevel level,
      BlockPos airPos,
      BlockPos framePos,
      PortalDefinition definition,
      ItemStack storedCatalystStack) {
    BlockState frameState = level.getBlockState(framePos);
    if (!definition.matchesFrame(frameState)) return false;

    Block frameMaterial = frameState.getBlock();

    // try both axes, pick whichever finds a valid enclosed frame
    Direction.Axis axis = null;
    Set<BlockPos> portalBlocks = null;
    Set<BlockPos> frameBlocks = null;

    for (Direction.Axis candidate : Direction.Axis.values()) {
      Set<BlockPos> tryPortal = new HashSet<>();
      Set<BlockPos> tryFrame = new HashSet<>();
      if (detectFrame(level, airPos, frameMaterial, candidate, tryPortal, tryFrame)
          && !tryPortal.isEmpty()
          && tryPortal.size() >= SafeConfig.getInt(CPConfig.MIN_PORTAL_SIZE, 1)
          && tryPortal.size() <= SafeConfig.getInt(CPConfig.MAX_PORTAL_SIZE, 64)) {
        // prefer the axis that finds more portal blocks (larger valid frame)
        if (portalBlocks == null || tryPortal.size() > portalBlocks.size()) {
          axis = candidate;
          portalBlocks = tryPortal;
          frameBlocks = tryFrame;
        }
      }
    }
    if (axis == null) return false;

    // scan frame for rune blocks
    Map<RuneType, Integer> runes = scanRunes(level, frameBlocks, axis);

    // calculate spawn position (center-bottom of the portal area)
    BlockPos spawnPos = calculateSpawnPos(portalBlocks);

    // place portal blocks
    BlockState portalState =
        ModBlocks.CUSTOM_PORTAL
            .get()
            .defaultBlockState()
            .setValue(CustomPortalBlock.COLOR, definition.color())
            .setValue(CustomPortalBlock.AXIS, axis)
            .setValue(CustomPortalBlock.LIT, false);

    for (BlockPos pos : portalBlocks) {
      level.setBlock(pos, portalState, 3);
    }

    // register portal
    ResourceLocation frameMaterialId = BuiltInRegistries.BLOCK.getKey(frameMaterial);
    CustomPortal portal =
        new CustomPortal(
            UUID.randomUUID(),
            definition.id(),
            definition.color(),
            frameMaterialId,
            level.dimension(),
            spawnPos,
            portalBlocks,
            storedCatalystStack);

    // apply detected runes
    for (var entry : runes.entrySet()) {
      for (int i = 0; i < entry.getValue(); i++) {
        portal.addRune(entry.getKey());
      }
    }

    PortalSavedData data = PortalSavedData.get(level);
    data.getRegistry().registerPortal(portal);

    CustomPortalsFoxified.LOGGER.debug(
        "Portal created: color={}, frame={}, pos={}, blocks={}",
        definition.color().getSerializedName(),
        frameMaterialId,
        spawnPos,
        portalBlocks.size());

    // tryLinkAcrossAll pushes LIT=true on both portals if a match is found;
    // blocks start LIT=false so no correction needed on failure
    CustomPortal linked = data.getRegistry().tryLinkAcrossAll(portal, level.getServer());
    if (linked != null) {
      CustomPortalsFoxified.LOGGER.debug(
          "Linked to portal at {} in {}", linked.getSpawnPos(), linked.getDimension());
    } else {
      CustomPortalsFoxified.LOGGER.debug("No matching portal found to link with");
    }

    data.setDirty();

    return true;
  }

  // DFS FRAME DETECTION //

  /**
   * DFS to find all air blocks enclosed by the frame material on a 2D plane. Returns false if the
   * area is not fully enclosed or exceeds size limits.
   */
  private static boolean detectFrame(
      ServerLevel level,
      BlockPos start,
      Block frameMaterial,
      Direction.Axis axis,
      Set<BlockPos> portalBlocks,
      Set<BlockPos> frameBlocks) {
    ArrayDeque<BlockPos> stack = new ArrayDeque<>();
    Set<BlockPos> visited = new HashSet<>();
    stack.push(start);

    int maxSize = SafeConfig.getInt(CPConfig.MAX_PORTAL_SIZE, 64);

    while (!stack.isEmpty()) {
      BlockPos current = stack.pop();
      if (!visited.add(current)) continue;

      // safety valve for when portal is too large
      if (visited.size() > maxSize) return false;

      List<BlockPos> neighbors = getAdjacentOnPlane(current, axis);
      for (BlockPos neighbor : neighbors) {
        BlockState neighborState = level.getBlockState(neighbor);

        if (neighborState.isAir() || neighborState.getBlock() instanceof CustomPortalBlock) {
          if (!visited.contains(neighbor)) {
            stack.push(neighbor);
          }
        } else if (neighborState.is(frameMaterial)) {
          frameBlocks.add(neighbor);
        } else {
          // hit a non-frame, non-air block so frame is not valid
          return false;
        }
      }

      portalBlocks.add(current);
    }

    return !portalBlocks.isEmpty();
  }

  /**
   * Get the 4 adjacent positions on the portal plane.
   * X axis: portal on X-Y plane → move in X and Y
   * Z axis: portal on Z-Y plane → move in Z and Y
   * Y axis: portal on X-Z plane → move in X and Z (horizontal portal)
   */
  private static List<BlockPos> getAdjacentOnPlane(BlockPos pos, Direction.Axis axis) {
    return switch (axis) {
      case X -> List.of(pos.above(), pos.below(), pos.east(), pos.west());
      case Z -> List.of(pos.above(), pos.below(), pos.north(), pos.south());
      case Y -> List.of(pos.east(), pos.west(), pos.north(), pos.south());
    };
  }

  // RUNE SCANNING //

  /** Scan frame blocks for adjacent rune blocks placed on the frame. */
  private static Map<RuneType, Integer> scanRunes(
      ServerLevel level, Set<BlockPos> frameBlocks, Direction.Axis axis) {
    Map<RuneType, Integer> runes = new EnumMap<>(RuneType.class);
    Set<BlockPos> checkedRunes = new HashSet<>();

    for (BlockPos framePos : frameBlocks) {
      // check all 6 faces of the frame block for runes
      for (Direction dir : Direction.values()) {
        BlockPos runePos = framePos.relative(dir);
        if (checkedRunes.contains(runePos)) continue;

        BlockState runeState = level.getBlockState(runePos);
        if (runeState.getBlock() instanceof AbstractRuneBlock runeBlock) {
          checkedRunes.add(runePos);
          RuneType type = runeBlock.getRuneType();
          runes.merge(type, 1, Integer::sum);
        }
      }
    }

    return runes;
  }

  // SPAWN POSITION //

  /** Calculate spawn position: horizontal center, lowest Y with 2 blocks headroom. */
  private static BlockPos calculateSpawnPos(Set<BlockPos> portalBlocks) {
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

    for (BlockPos pos : portalBlocks) {
      minX = Math.min(minX, pos.getX());
      maxX = Math.max(maxX, pos.getX());
      minY = Math.min(minY, pos.getY());
      minZ = Math.min(minZ, pos.getZ());
      maxZ = Math.max(maxZ, pos.getZ());
    }

    int centerX = (minX + maxX) / 2;
    int centerZ = (minZ + maxZ) / 2;

    return new BlockPos(centerX, minY, centerZ);
  }
}
