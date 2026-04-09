package dev.customportalsfoxified.items;

import dev.customportalsfoxified.util.PortalEventHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    return PortalEventHandler.tryActivatePortal(
        (ServerLevel) context.getLevel(),
        context.getClickedPos(),
        context.getClickedFace(),
        context.getItemInHand(),
        context.getPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer : null);
  }
}
