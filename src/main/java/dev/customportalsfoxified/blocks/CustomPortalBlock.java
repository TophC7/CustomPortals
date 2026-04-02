package dev.customportalsfoxified.blocks;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModAttachments;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.ModItems;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import dev.customportalsfoxified.particle.ColoredPortalParticleOptions;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class CustomPortalBlock extends HalfTransparentBlock
    implements Portal, SimpleWaterloggedBlock {

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
    CustomPortal portal = PortalSavedData.get(level).getRegistry().getPortalAt(pos);
    if (portal == null || !portal.isLinked()) return null;

    ServerLevel destLevel = level.getServer().getLevel(portal.getLinkedDimension());
    if (destLevel == null) {
      CustomPortalsFoxified.LOGGER.warn(
          "Linked dimension {} not found", portal.getLinkedDimension());
      return null;
    }

    CustomPortal linked =
        PortalSavedData.get(destLevel).getRegistry().getPortalById(portal.getLinkedPortalId());
    if (linked == null) {
      CustomPortalsFoxified.LOGGER.warn(
          "Linked portal {} not found in {}",
          portal.getLinkedPortalId(),
          portal.getLinkedDimension());
      return null;
    }

    Vec3 destPos = Vec3.atBottomCenterOf(linked.getSpawnPos());
    return new DimensionTransition(
        destLevel,
        destPos,
        Vec3.ZERO,
        entity.getYRot(),
        entity.getXRot(),
        DimensionTransition.PLAY_PORTAL_SOUND);
  }

  @Override
  public int getPortalTransitionTime(ServerLevel level, Entity entity) {
    CustomPortal portal =
        PortalSavedData.get(level).getRegistry().getPortalAt(entity.blockPosition());
    if (portal != null && portal.hasHaste()) return 1;

    // haste on the destination portal also grants instant transition
    if (portal != null && portal.isLinked()) {
      ServerLevel destLevel = level.getServer().getLevel(portal.getLinkedDimension());
      if (destLevel != null) {
        CustomPortal linked =
            PortalSavedData.get(destLevel).getRegistry().getPortalById(portal.getLinkedPortalId());
        if (linked != null && linked.hasHaste()) return 1;
      }
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
        CustomPortal portal = PortalSavedData.get(serverLevel).getRegistry().getPortalAt(pos);
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
        Block.popResource(
            level, pos, new ItemStack(ModItems.CATALYSTS.get(portal.getColor()).get()));

        // unregister BEFORE cascade
        // clears spatial index so reentry
        // from cascaded onRemove calls won't find this portal again
        data.getRegistry().removeAndRelink(portal, serverLevel.getServer());
        data.setDirty();

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

    if (!CPConfig.MUTE_SOUNDS.get() && random.nextInt(100) == 0) {
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
      CustomPortal portal = PortalSavedData.get(level).getRegistry().getPortalAt(pos);
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
   * Update LIT on all blocks of a portal. LIT = linked, simple as that. Redstone is handled
   * separately by unlinking/relinking in neighborChanged.
   */
  public static void updateLitState(ServerLevel level, CustomPortal portal) {
    boolean shouldBeLit = portal.isLinked();
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
    if (level.isClientSide() || !CPConfig.REDSTONE_DISABLES.get()) return;

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
        ServerLevel partnerLevel = server.getLevel(portal.getLinkedDimension());
        CustomPortal partner = null;
        if (partnerLevel != null) {
          partner =
              PortalSavedData.get(partnerLevel)
                  .getRegistry()
                  .getPortalById(portal.getLinkedPortalId());
        }

        portal.unlink();
        if (partner != null) {
          partner.unlink();
          updateLitState(partnerLevel, partner);
          // partner is free, try to find a new match (won't pick this portal, it's disabled)
          PortalSavedData partnerData = PortalSavedData.get(partnerLevel);
          partnerData.getRegistry().tryLinkAcrossAll(partner, server);
          partnerData.setDirty();
        }
      }
      updateLitState(serverLevel, portal);
      data.setDirty();

    } else if (!hasSignal && portal.isRedstoneDisabled()) {
      // redstone removed: clear disabled flag, try to relink
      portal.setRedstoneDisabled(false);
      net.minecraft.server.MinecraftServer server = serverLevel.getServer();
      data.getRegistry().tryLinkAcrossAll(portal, server);
      updateLitState(serverLevel, portal);
      data.setDirty();
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
