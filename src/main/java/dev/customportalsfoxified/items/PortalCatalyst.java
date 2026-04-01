package dev.customportalsfoxified.items;

import dev.customportalsfoxified.util.PortalHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class PortalCatalyst extends Item {

  private final DyeColor color;

  public PortalCatalyst(Properties properties, DyeColor color) {
    super(properties);
    this.color = color;
  }

  public DyeColor getColor() {
    return color;
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;

    ServerLevel level = (ServerLevel) context.getLevel();
    BlockPos clickedPos = context.getClickedPos();
    Direction face = context.getClickedFace();
    BlockPos airPos = clickedPos.relative(face);

    if (!level.getBlockState(airPos).isAir()) return InteractionResult.PASS;

    boolean built =
        PortalHelper.buildPortal(
            level,
            airPos,
            clickedPos,
            color,
            context.getPlayer() != null ? context.getPlayer().getUUID() : null);

    if (built) {
      level.playSound(null, airPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
      if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
        context.getItemInHand().shrink(1);
      }
      return InteractionResult.SUCCESS;
    }

    return InteractionResult.PASS;
  }
}
