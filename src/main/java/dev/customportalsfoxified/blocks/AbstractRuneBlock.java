package dev.customportalsfoxified.blocks;

import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.data.RuneType;
import dev.customportalsfoxified.network.MapPortalSnapshotSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractRuneBlock extends FaceAttachedHorizontalDirectionalBlock {

  // 1px thick plate shapes; flush against attachment surface
  private static final VoxelShape FLOOR_SHAPE = Block.box(2, 0, 2, 14, 1, 14);
  private static final VoxelShape CEILING_SHAPE = Block.box(2, 15, 2, 14, 16, 14);
  private static final VoxelShape NORTH_SHAPE = Block.box(2, 2, 15, 14, 14, 16);
  private static final VoxelShape SOUTH_SHAPE = Block.box(2, 2, 0, 14, 14, 1);
  private static final VoxelShape EAST_SHAPE = Block.box(0, 2, 2, 1, 14, 14);
  private static final VoxelShape WEST_SHAPE = Block.box(15, 2, 2, 16, 14, 14);

  protected AbstractRuneBlock() {
    super(
        BlockBehaviour.Properties.of()
            .sound(SoundType.AMETHYST_CLUSTER)
            .lightLevel(s -> 2)
            .noCollission()
            .instabreak());
    registerDefaultState(
        stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH));
  }

  public abstract RuneType getRuneType();

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACE, FACING);
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(FACE)) {
      case FLOOR -> FLOOR_SHAPE;
      case CEILING -> CEILING_SHAPE;
      case WALL ->
          switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> FLOOR_SHAPE;
          };
    };
  }

  // PORTAL REGISTRATION //

  @Override
  public void setPlacedBy(
      Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (level.isClientSide()) return;

    ServerLevel serverLevel = (ServerLevel) level;
    BlockPos mountedOn = getMountedBlockPos(pos, state);
    registerOnAdjacentPortal(serverLevel, mountedOn);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock()) && !level.isClientSide()) {
      ServerLevel serverLevel = (ServerLevel) level;
      BlockPos mountedOn = getMountedBlockPos(pos, state);
      unregisterFromAdjacentPortal(serverLevel, mountedOn);
    }
    super.onRemove(state, level, pos, newState, movedByPiston);
  }

  private void registerOnAdjacentPortal(ServerLevel level, BlockPos mountedOn) {
    PortalSavedData data = PortalSavedData.get(level);
    // check all neighbors of the mounted block for portal blocks
    for (Direction dir : Direction.values()) {
      CustomPortal portal = data.getRegistry().getPortalAt(mountedOn.relative(dir));
      if (portal != null) {
        portal.addRune(getRuneType());
        // relink in case the new rune changes linking capabilities
        if (!portal.isLinked()) {
          data.getRegistry().tryLinkAcrossAll(portal, level.getServer());
        }
        data.setDirty();
        MapPortalSnapshotSync.sendToInterestedPlayers(level.getServer());
        return;
      }
    }
  }

  private void unregisterFromAdjacentPortal(ServerLevel level, BlockPos mountedOn) {
    PortalSavedData data = PortalSavedData.get(level);
    for (Direction dir : Direction.values()) {
      CustomPortal portal = data.getRegistry().getPortalAt(mountedOn.relative(dir));
      if (portal != null) {
        portal.removeRune(getRuneType());

        // rune removal may invalidate the current link
        // (gate removed from cross-dim pair, or enhancer removed and now out of range)
        if (portal.isLinked()) {
          net.minecraft.server.MinecraftServer server = level.getServer();
          CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, server);
          if (partner != null && !portal.isCompatibleWith(partner)) {
            portal.unlinkFrom(partner);
            CustomPortalBlock.updateLitState(level, portal);
            ServerLevel partnerLevel = server.getLevel(partner.getDimension());
            if (partnerLevel != null) {
              CustomPortalBlock.updateLitState(partnerLevel, partner);
              PortalSavedData partnerData = PortalSavedData.get(partnerLevel);
              partnerData.getRegistry().tryLinkAcrossAll(partner, server);
              partnerData.setDirty();
            }
            data.getRegistry().tryLinkAcrossAll(portal, server);
          }
        }

        data.setDirty();
        MapPortalSnapshotSync.sendToInterestedPlayers(level.getServer());
        return;
      }
    }
  }

  // get the block position this rune is mounted on
  private BlockPos getMountedBlockPos(BlockPos runePos, BlockState state) {
    return switch (state.getValue(FACE)) {
      case FLOOR -> runePos.below();
      case CEILING -> runePos.above();
      case WALL -> runePos.relative(state.getValue(FACING).getOpposite());
    };
  }
}
