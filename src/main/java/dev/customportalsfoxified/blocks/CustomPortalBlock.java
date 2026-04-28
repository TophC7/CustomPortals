package dev.customportalsfoxified.blocks;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModAttachments;
import dev.customportalsfoxified.ModItems;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.network.MapPortalSnapshotSync;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import dev.customportalsfoxified.particle.ColoredPortalParticleOptions;
import dev.customportalsfoxified.portal.PortalDefinition;
import dev.customportalsfoxified.portal.PortalDefinitions;
import dev.customportalsfoxified.util.PortalLinkHelper;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import xyz.kwahson.core.config.SafeConfig;
import org.joml.Vector3f;

public class CustomPortalBlock extends HalfTransparentBlock
    implements Portal, SimpleWaterloggedBlock {

  private static final String LOG_PREFIX_DEST = "[portal-dest]";

  public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
  public static final EnumProperty<Direction.Axis> AXIS =
      EnumProperty.create("axis", Direction.Axis.class);
  public static final BooleanProperty LIT = BlockStateProperties.LIT;
  public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

  private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
  private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
  private static final VoxelShape Y_SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 10.0, 16.0);

  // pre-built particle options per dye color to avoid allocation in animateTick
  private static final Map<DyeColor, ColoredPortalParticleOptions> PARTICLE_OPTIONS_BY_COLOR;

  static {
    PARTICLE_OPTIONS_BY_COLOR = new EnumMap<>(DyeColor.class);
    for (DyeColor dye : DyeColor.values()) {
      int rgb = dye.getTextColor();
      float r = ((rgb >> 16) & 0xFF) / 255.0F;
      float g = ((rgb >> 8) & 0xFF) / 255.0F;
      float b = (rgb & 0xFF) / 255.0F;
      PARTICLE_OPTIONS_BY_COLOR.put(dye, new ColoredPortalParticleOptions(new Vector3f(r, g, b)));
    }
  }

  public CustomPortalBlock() {
    super(
        BlockBehaviour.Properties.of()
            .noCollission()
            .strength(-1.0F)
            .lightLevel(
                state -> {
                  if (!state.getValue(LIT)) return 0;
                  return state.getValue(COLOR) == DyeColor.BLACK ? 0 : 11;
                })
            .noLootTable()
            .randomTicks());
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(COLOR, DyeColor.PURPLE)
            .setValue(AXIS, Direction.Axis.X)
            .setValue(LIT, true)
            .setValue(WATERLOGGED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(COLOR, AXIS, LIT, WATERLOGGED);
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(AXIS)) {
      case X -> X_SHAPE;
      case Z -> Z_SHAPE;
      case Y -> Y_SHAPE;
    };
  }

  // PORTAL INTERFACE //

  // NOTE: uses vanilla Portal interface so Entity.changeDimension() handles all
  // state sync
  // backpack mods and other entity data survive teleportation cleanly.
  // This replaces the Fabric ServerPlayerMixin approach

  @Override
  public @Nullable DimensionTransition getPortalDestination(
      ServerLevel level, Entity entity, BlockPos pos) {
    CustomPortal portal = PortalSavedData.registry(level).getPortalAt(pos);
    if (portal == null) {
      CustomPortalsFoxified.LOGGER.info(
          "{} no portal data at {} in {}", LOG_PREFIX_DEST, pos, level.dimension().location());
      return null;
    }
    if (portal.isDefinitionDisabled()) {
      CustomPortalsFoxified.LOGGER.info(
          "{} portal {} disabled-def at {}", LOG_PREFIX_DEST, portal.getId(), pos);
      return null;
    }

    PortalDefinition definition =
        portal.getDefinitionId() != null ? PortalDefinitions.get(portal.getDefinitionId()) : null;
    CustomPortalsFoxified.LOGGER.info(
        "{} entry portal={} dim={} link={} def={} counterpart-route={}",
        LOG_PREFIX_DEST,
        portal.getId(),
        level.dimension().location(),
        portal.linkDescriptor(),
        definition != null ? definition.id() : "null",
        definition != null && definition.usesCounterpartRoute());

    // Stale link: linkedPortalId points at a partner that no longer exists (partner
    // was removed via a path that didn't unlink us — mod conflict, /setblock, etc.).
    // Clear so the lazy resolve below can re-fire.
    CustomPortal linked = PortalSavedData.resolveLinkedPartner(portal, level.getServer());
    if (portal.isLinked() && linked == null) {
      CustomPortalsFoxified.LOGGER.warn(
          "{} stale link on portal {} -> {} not found, clearing",
          LOG_PREFIX_DEST, portal.getId(), portal.linkDescriptor());
      portal.unlink();
      PortalSavedData.get(level).setDirty();
    }

    if (definition != null && definition.usesCounterpartRoute() && !portal.isLinked()) {
      CustomPortal resolved = PortalLinkHelper.tryResolveLink(level, portal);
      if (resolved != null) {
        MapPortalSnapshotSync.sendToInterestedPlayers(level.getServer());
        linked = resolved;
      }
      CustomPortalsFoxified.LOGGER.info(
          "{} post-resolve portal={} link={}",
          LOG_PREFIX_DEST, portal.getId(), portal.linkDescriptor());
    }
    if (!portal.isLinked()) {
      CustomPortalsFoxified.LOGGER.info("{} still unlinked, returning null", LOG_PREFIX_DEST);
      return null;
    }

    if (linked == null) {
      // Linked flag set but partner unresolvable — already warned above when we
      // detected the stale state, no further log needed.
      return null;
    }
    if (linked.isDefinitionDisabled()) {
      CustomPortalsFoxified.LOGGER.info(
          "{} linked portal {} disabled-def, returning null", LOG_PREFIX_DEST, linked.getId());
      return null;
    }

    ServerLevel destLevel = level.getServer().getLevel(portal.getLinkedDimension());
    if (destLevel == null) {
      CustomPortalsFoxified.LOGGER.warn(
          "{} linked dimension {} not found", LOG_PREFIX_DEST, portal.getLinkedDimension());
      return null;
    }

    // Vanilla-style safe exit positioning: compute source/dest portal rectangles,
    // map the entity's relative position from source into destination, then apply
    // PortalShape.findCollisionFreePosition for collision-safe placement.
    // Mirrors NetherPortalBlock.getDimensionTransitionFromExit / createDimensionTransition.
    return createSafeTransition(destLevel, entity, portal, linked);
  }

  @Override
  public int getPortalTransitionTime(ServerLevel level, Entity entity) {
    CustomPortal portal = PortalSavedData.registry(level).getPortalAt(entity.blockPosition());
    if (portal != null && portal.hasHaste()) return 1;

    // haste on the destination portal also grants instant transition
    if (portal != null && portal.isLinked()) {
      CustomPortal linked = PortalSavedData.resolveLinkedPartner(portal, level.getServer());
      if (linked != null && linked.hasHaste()) return 1;
    }

    // match vanilla nether portal behavior: creative=1 tick, survival=80,
    // non-player=0
    if (entity instanceof Player player) {
      return Math.max(
          1,
          level
              .getGameRules()
              .getInt(
                  player.getAbilities().invulnerable
                      ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                      : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY));
    }
    return 0;
  }

  // SAFE EXIT POSITIONING //

  /**
   * Build a {@link BlockUtil.FoundRectangle} from a portal's block set.
   *
   * <p>For vertical portals (axis X/Z), {@code axis1Size} is the width along the
   * portal's horizontal axis and {@code axis2Size} is the height along Y — matching
   * vanilla's convention. For horizontal portals (axis Y), {@code axis1Size} is
   * width along X and {@code axis2Size} is depth along Z.
   */
  private static BlockUtil.FoundRectangle computePortalRectangle(CustomPortal portal) {
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
    for (BlockPos p : portal.getPortalBlocks()) {
      minX = Math.min(minX, p.getX());
      maxX = Math.max(maxX, p.getX());
      minY = Math.min(minY, p.getY());
      maxY = Math.max(maxY, p.getY());
      minZ = Math.min(minZ, p.getZ());
      maxZ = Math.max(maxZ, p.getZ());
    }
    return switch (portal.getAxis()) {
      case X -> new BlockUtil.FoundRectangle(
          new BlockPos(minX, minY, minZ), maxX - minX + 1, maxY - minY + 1);
      case Z -> new BlockUtil.FoundRectangle(
          new BlockPos(minX, minY, minZ), maxZ - minZ + 1, maxY - minY + 1);
      case Y -> new BlockUtil.FoundRectangle(
          new BlockPos(minX, minY, minZ), maxX - minX + 1, maxZ - minZ + 1);
    };
  }

  /**
   * Mirrors vanilla {@code NetherPortalBlock.getDimensionTransitionFromExit} and
   * {@code createDimensionTransition}: computes the entity's relative position within
   * the source portal, maps it into the destination portal accounting for entity
   * dimensions and axis rotation, then calls
   * {@link PortalShape#findCollisionFreePosition} for collision-safe placement.
   *
   * <p>Vertical→vertical transitions use the exact vanilla formulas. Horizontal→horizontal
   * transitions extend the logic to the X-Z plane. Mixed orientations fall back to
   * center-based placement with collision safety (no vanilla equivalent exists).
   */
  private static DimensionTransition createSafeTransition(
      ServerLevel destLevel, Entity entity, CustomPortal source, CustomPortal dest) {
    BlockUtil.FoundRectangle sourceRect = computePortalRectangle(source);
    BlockUtil.FoundRectangle destRect = computePortalRectangle(dest);
    Direction.Axis sourceAxis = source.getAxis();
    Direction.Axis destAxis = dest.getAxis();
    EntityDimensions dims = entity.getDimensions(entity.getPose());

    Vec3 destPos;
    int rotationDeg = 0;
    Vec3 movement = entity.getDeltaMovement();

    if (sourceAxis != Direction.Axis.Y && destAxis != Direction.Axis.Y) {
      // Both vertical: use vanilla logic exactly.
      Vec3 relativePos = entity.getRelativePortalPosition(sourceAxis, sourceRect);

      // Rotation when source and dest horizontal axes differ (vanilla: 90 degree turn)
      if (sourceAxis != destAxis) {
        rotationDeg = 90;
        movement = new Vec3(movement.z, movement.y, -movement.x);
      }

      double d2 = dims.width() / 2.0
          + ((double) destRect.axis1Size - dims.width()) * relativePos.x();
      double d3 = ((double) destRect.axis2Size - dims.height()) * relativePos.y();
      double d4 = 0.5 + relativePos.z();
      boolean flag = destAxis == Direction.Axis.X;
      destPos = new Vec3(
          destRect.minCorner.getX() + (flag ? d2 : d4),
          destRect.minCorner.getY() + d3,
          destRect.minCorner.getZ() + (flag ? d4 : d2));
    } else if (sourceAxis == Direction.Axis.Y && destAxis == Direction.Axis.Y) {
      // Both horizontal: map relative X-Z position, place above portal plane.
      Vec3 relativePos = getHorizontalRelativePos(entity, sourceRect, dims);
      double d2 = dims.width() / 2.0
          + ((double) destRect.axis1Size - dims.width()) * relativePos.x();
      double d3 = dims.width() / 2.0
          + ((double) destRect.axis2Size - dims.width()) * relativePos.y();
      destPos = new Vec3(
          destRect.minCorner.getX() + d2,
          destRect.minCorner.getY() + 1.0,
          destRect.minCorner.getZ() + d3);
    } else {
      // Mixed vertical/horizontal: no vanilla equivalent. Place at destination
      // spawn center with appropriate vertical offset; collision safety below
      // handles the rest.
      destPos = Vec3.atBottomCenterOf(dest.getSpawnPos());
      if (destAxis == Direction.Axis.Y) {
        destPos = new Vec3(destPos.x, destRect.minCorner.getY() + 1.0, destPos.z);
      }
    }

    Vec3 safe = PortalShape.findCollisionFreePosition(destPos, destLevel, entity, dims);
    // Vanilla uses PLAY_PORTAL_SOUND.then(PLACE_PORTAL_TICKET) to keep the
    // destination chunk loaded during teleportation (important for non-player entities).
    return new DimensionTransition(
        destLevel, safe, movement,
        entity.getYRot() + rotationDeg, entity.getXRot(),
        DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET));
  }

  /**
   * Relative position for horizontal (Y-axis) portals where vanilla's
   * {@link PortalShape#getRelativePosition} doesn't apply (it assumes a vertical
   * portal with height along Y). Returns (relX along X, relZ along Z, vertical
   * offset from portal plane).
   */
  private static Vec3 getHorizontalRelativePos(
      Entity entity, BlockUtil.FoundRectangle rect, EntityDimensions dims) {
    Vec3 pos = entity.position();
    double d0 = rect.axis1Size - dims.width();
    double relX = d0 > 0.0
        ? Mth.clamp(
            Mth.inverseLerp(
                pos.x - (rect.minCorner.getX() + dims.width() / 2.0), 0.0, d0),
            0.0, 1.0)
        : 0.5;
    double d1 = rect.axis2Size - dims.width();
    double relZ = d1 > 0.0
        ? Mth.clamp(
            Mth.inverseLerp(
                pos.z - (rect.minCorner.getZ() + dims.width() / 2.0), 0.0, d1),
            0.0, 1.0)
        : 0.5;
    double relY = pos.y - (rect.minCorner.getY() + 0.5);
    return new Vec3(relX, relZ, relY);
  }

  // ENTITY INTERACTION //

  @Override
  public Portal.Transition getLocalTransition() {
    return Portal.Transition.CONFUSION;
  }

  @Override
  public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    // inactive (unlinked) portals shouldn't trigger confusion/wobble effects
    if (!state.getValue(LIT)) return;

    if (entity.canUsePortal(false)) {
      // client: skip if on our post-teleport cooldown to prevent
      // re-entering the destination portal immediately
      if (level.isClientSide() && entity instanceof net.minecraft.client.player.LocalPlayer) {
        if (dev.customportalsfoxified.client.ClientPortalState.isOnCooldown()) return;
      }

      entity.setAsInsidePortal(this, pos);

      // sends color on every tick; a tiny packet (1 varint) and
      // only fires while the player is in the portal. Change detection
      // was unreliable because the attachment persists across sessions.
      if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
        int colorId = state.getValue(COLOR).getId();
        PacketDistributor.sendToPlayer(sp, new SyncPortalColorPayload(colorId));
      }
    }
  }

  // FRAME VALIDATION //

  @Override
  public BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState neighborState,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    if (state.getValue(WATERLOGGED)) {
      level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
    }

    // axis=X → portal plane is X-Y, thin axis is Z (skip north/south)
    // axis=Z → portal plane is Z-Y, thin axis is X (skip east/west)
    // axis=Y → portal plane is X-Z, thin axis is Y (skip up/down)
    Direction.Axis portalAxis = state.getValue(AXIS);
    Direction.Axis thinAxis =
        switch (portalAxis) {
          case X -> Direction.Axis.Z;
          case Z -> Direction.Axis.X;
          case Y -> Direction.Axis.Y;
        };
    if (direction.getAxis() != thinAxis
        && !(neighborState.getBlock() instanceof CustomPortalBlock)) {
      if (level instanceof ServerLevel serverLevel) {
        CustomPortal portal = PortalSavedData.registry(serverLevel).getPortalAt(pos);
        if (portal != null) {
          // forward lookup (ResourceLocation → Block) is cheaper than
          // reverse lookup (Block → ResourceLocation) on every neighbor change
          Block expectedBlock = BuiltInRegistries.BLOCK.get(portal.getFrameMaterial());
          if (neighborState.getBlock() != expectedBlock) {
            return Blocks.AIR.defaultBlockState();
          }
        }
      }
    }

    return state;
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock()) && !level.isClientSide()) {
      ServerLevel serverLevel = (ServerLevel) level;
      PortalSavedData data = PortalSavedData.get(serverLevel);
      CustomPortal portal = data.getRegistry().getPortalAt(pos);
      if (portal != null) {
        ItemStack storedCatalyst = portal.getStoredCatalystStack();
        if (!storedCatalyst.isEmpty()) {
          Block.popResource(level, pos, storedCatalyst);
        } else if (portal.getDefinitionId() == null) {
          Block.popResource(
              level, pos, new ItemStack(ModItems.CATALYSTS.get(portal.getColor()).get()));
        }

        // unregister BEFORE cascade
        // clears spatial index so reentry
        // from cascaded onRemove calls won't find this portal again
        data.getRegistry().removeAndRelink(portal, serverLevel.getServer());
        data.setDirty();
        MapPortalSnapshotSync.sendToInterestedPlayers(serverLevel.getServer());

        // snapshot to avoid iterating a set that cascade removals might touch
        for (BlockPos portalPos : new ArrayList<>(portal.getPortalBlocks())) {
          if (!portalPos.equals(pos)
              && level.getBlockState(portalPos).getBlock() instanceof CustomPortalBlock) {
            level.removeBlock(portalPos, false);
          }
        }
      }
    }
    super.onRemove(state, level, pos, newState, movedByPiston);
  }

  // AMBIENT //

  @Override
  public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    if (!state.getValue(LIT)) return;

    if (!SafeConfig.getBool(CPConfig.MUTE_SOUNDS, false) && random.nextInt(100) == 0) {
      level.playLocalSound(
          pos.getX() + 0.5,
          pos.getY() + 0.5,
          pos.getZ() + 0.5,
          SoundEvents.PORTAL_AMBIENT,
          SoundSource.BLOCKS,
          0.5F,
          random.nextFloat() * 0.4F + 0.8F,
          false);
    }

    // colored portal particles with same vanilla spawn pattern: particles appear at portal
    // edge with high inward velocity, creating the "pulled into the void" effect
    ColoredPortalParticleOptions particleOptions = PARTICLE_OPTIONS_BY_COLOR.get(state.getValue(COLOR));

    for (int i = 0; i < 4; i++) {
      double x = pos.getX() + random.nextDouble();
      double y = pos.getY() + random.nextDouble();
      double z = pos.getZ() + random.nextDouble();
      double vx = (random.nextFloat() - 0.5) * 0.5;
      double vy = (random.nextFloat() - 0.5) * 0.5;
      double vz = (random.nextFloat() - 0.5) * 0.5;

      // spawn at portal edge with high inward velocity along thin axis
      int j = random.nextInt(2) * 2 - 1; // -1 or +1
      switch (state.getValue(AXIS)) {
        case X -> {
          // portal spans X-Y, thin on Z; particles fly along Z
          z = pos.getZ() + 0.5 + 0.25 * j;
          vz = random.nextFloat() * 2.0F * j;
        }
        case Z -> {
          // portal spans Z-Y, thin on X; particles fly along X
          x = pos.getX() + 0.5 + 0.25 * j;
          vx = random.nextFloat() * 2.0F * j;
        }
        case Y -> {
          // portal spans X-Z, thin on Y; particles fly up/down
          y = pos.getY() + 0.5 + 0.25 * j;
          vy = random.nextFloat() * 2.0F * j;
        }
      }

      level.addParticle(particleOptions, x, y, z, vx, vy, vz);
    }
  }

  @Override
  public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!state.getValue(LIT)) return;

    // easter egg: very rarely, a colored sheep wanders out of an active portal
    // randomTick fires ~1/4096 per block per tick; 1/1000 on top of that makes
    // this roughly once every ~3.4 real-time hours per portal block
    if (random.nextInt(1000) == 0
        && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
      CustomPortal portal = PortalSavedData.registry(level).getPortalAt(pos);
      if (portal != null) {
        BlockPos spawnPos = portal.getSpawnPos();

        // cap: don't spawn if 4+ sheep already nearby
        AABB area = new AABB(spawnPos).inflate(16.0);
        if (level.getEntitiesOfClass(Sheep.class, area).size() >= 4) return;

        Sheep sheep = EntityType.SHEEP.create(level);
        if (sheep != null) {
          sheep.setColor(state.getValue(COLOR));
          sheep.moveTo(
              spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
              random.nextFloat() * 360.0F, 0.0F);
          // prevent the sheep from teleporting through the portal it spawned in
          sheep.setPortalCooldown();
          level.addFreshEntity(sheep);
        }
      }
    }
  }

  // WATERLOGGING //

  @Override
  public FluidState getFluidState(BlockState state) {
    return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
  }

  @Override
  public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
    FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
    return defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
  }

  // LIT STATE | event-driven, no ticker //

  /**
   * Update LIT on all blocks of a portal. Disabled portals stay dark even if they still have a
   * stale link recorded while revalidation is in progress.
   */
  public static void updateLitState(ServerLevel level, CustomPortal portal) {
    boolean shouldBeLit = PortalDefinitions.shouldPortalStayLit(portal);
    for (BlockPos pos : portal.getPortalBlocks()) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof CustomPortalBlock && state.getValue(LIT) != shouldBeLit) {
        // flag 2 (UPDATE_CLIENTS) instead of 3 (UPDATE_CLIENTS | NOTIFY_NEIGHBORS)
        // to avoid cascading neighborChanged calls on adjacent portal blocks
        level.setBlock(pos, state.setValue(LIT, shouldBeLit), 2);
      }
    }
  }

  @Override
  public void neighborChanged(
      BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
    if (level.isClientSide() || !SafeConfig.getBool(CPConfig.REDSTONE_DISABLES, true)) return;

    ServerLevel serverLevel = (ServerLevel) level;
    PortalSavedData data = PortalSavedData.get(serverLevel);
    CustomPortal portal = data.getRegistry().getPortalAt(pos);
    if (portal == null) return;

    boolean hasSignal = isPortalPowered(portal, level);

    if (hasSignal && !portal.isRedstoneDisabled()) {
      // redstone applied: mark disabled, unlink, let partner find a new match
      portal.setRedstoneDisabled(true);

      if (portal.isLinked()) {
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, server);
        if (partner != null) {
          portal.unlinkFrom(partner);
          ServerLevel partnerLevel = server.getLevel(partner.getDimension());
          if (partnerLevel != null) {
            updateLitState(partnerLevel, partner);
            PortalSavedData partnerData = PortalSavedData.get(partnerLevel);
            if (!PortalDefinitions.usesCounterpartRoute(partner.getDefinitionId())) {
              PortalLinkHelper.tryResolveLink(partnerLevel, partner);
            }
            partnerData.setDirty();
          }
        } else {
          portal.unlink();
        }
      }
      updateLitState(serverLevel, portal);
      data.setDirty();
      MapPortalSnapshotSync.sendToInterestedPlayers(serverLevel.getServer());

    } else if (!hasSignal && portal.isRedstoneDisabled()) {
      // redstone removed: clear disabled flag, try to relink
      portal.setRedstoneDisabled(false);
      PortalLinkHelper.tryResolveLink(serverLevel, portal);
      updateLitState(serverLevel, portal);
      data.setDirty();
      MapPortalSnapshotSync.sendToInterestedPlayers(serverLevel.getServer());
    }
  }

  /** Check if any block in the portal is receiving a redstone signal. */
  private static boolean isPortalPowered(CustomPortal portal, Level level) {
    for (BlockPos pos : portal.getPortalBlocks()) {
      if (level.hasNeighborSignal(pos)) return true;
    }
    return false;
  }
}
