package dev.customportalsfoxified.blocks;

import com.mojang.serialization.MapCodec;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.data.RuneType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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

    // thin slab shapes for each attachment face
    private static final VoxelShape FLOOR_SHAPE = Block.box(2, 0, 2, 14, 2, 14);
    private static final VoxelShape CEILING_SHAPE = Block.box(2, 14, 2, 14, 16, 14);
    private static final VoxelShape NORTH_SHAPE = Block.box(2, 2, 14, 14, 14, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(2, 2, 0, 14, 14, 2);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 2, 2, 2, 14, 14);
    private static final VoxelShape WEST_SHAPE = Block.box(14, 2, 2, 16, 14, 14);

    protected AbstractRuneBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.AMETHYST_CLUSTER)
                .lightLevel(s -> 2)
                .noCollission()
                .instabreak());
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH));
    }

    public abstract RuneType getRuneType();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> switch (state.getValue(FACING)) {
                case NORTH -> NORTH_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                default -> FLOOR_SHAPE;
            };
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // break if support block is removed
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // PORTAL REGISTRATION //

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos mountedOn = getMountedBlockPos(pos, state);
        registerOnAdjacentPortal(serverLevel, mountedOn);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
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
                data.setDirty();
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
