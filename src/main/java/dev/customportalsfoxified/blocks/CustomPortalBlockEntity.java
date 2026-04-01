package dev.customportalsfoxified.blocks;

import dev.customportalsfoxified.ModBlocks;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CustomPortalBlockEntity extends BlockEntity {

  public CustomPortalBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlocks.PORTAL_BLOCK_ENTITY.get(), pos, state);
  }

  public void tick(Level level, BlockPos pos, BlockState state) {
    if (level.isClientSide()) return;

    ServerLevel serverLevel = (ServerLevel) level;
    CustomPortal portal = PortalSavedData.get(serverLevel).getRegistry().getPortalAt(pos);
    boolean shouldBeLit = shouldBeLit(portal, level, pos);
    boolean isLit = state.getValue(CustomPortalBlock.LIT);

    if (shouldBeLit != isLit) {
      level.setBlock(pos, state.setValue(CustomPortalBlock.LIT, shouldBeLit), 3);
    }
  }

  private boolean shouldBeLit(CustomPortal portal, Level level, BlockPos pos) {
    boolean linked = portal != null && portal.isLinked();

    return switch (CPConfig.REDSTONE_MODE.get()) {
      case OFF -> linked;
      case ON -> linked && level.hasNeighborSignal(pos);
      case NO_EFFECT -> true;
    };
  }
}
