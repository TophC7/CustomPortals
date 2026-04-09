package dev.customportalsfoxified.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

public record PortalLinkKey(
    @Nullable ResourceLocation definitionId,
    @Nullable DyeColor legacyColor,
    @Nullable ResourceLocation legacyFrameMaterial) {

  public static PortalLinkKey of(CustomPortal portal) {
    if (portal.getDefinitionId() != null) {
      return new PortalLinkKey(portal.getDefinitionId(), null, null);
    }
    return new PortalLinkKey(null, portal.getColor(), portal.getFrameMaterial());
  }
}
