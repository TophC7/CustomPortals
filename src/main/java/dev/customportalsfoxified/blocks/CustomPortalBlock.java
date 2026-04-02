package dev.customportalsfoxified.blocks;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModAttachments;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.ModBlocks;
import dev.customportalsfoxified.ModItems;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class CustomPortalBlock extends HalfTransparentBlock
    implements Portal, EntityBlock, SimpleWaterloggedBlock {

  public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
  public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
  public static final BooleanProperty LIT = BlockStateProperties.LIT;
  public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

  private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
  private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

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
            .noLootTable());
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
    return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
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
    Direction.Axis portalAxis = state.getValue(AXIS);
    Direction.Axis thinAxis = portalAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
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

    // TODO: spawn colored particles based on state.getValue(COLOR)
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

  // BLOCK ENTITY //

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CustomPortalBlockEntity(pos, state);
  }

  @Override
  public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> blockEntityType) {
    if (level.isClientSide()) return null;
    return blockEntityType == ModBlocks.PORTAL_BLOCK_ENTITY.get()
        ? (lvl, pos, st, be) -> ((CustomPortalBlockEntity) be).tick(lvl, pos, st)
        : null;
  }
}
