package dev.customportalsfoxified.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * Bucket key for the portal link index. Frame material is always part of the key
 * because two portals with different frames must never link, even when they share
 * the same definition id (e.g. the built-in {@code pink_portal_catalyst} definition
 * uses an "any frame" selector, so a planks-frame pink portal and an obsidian-frame
 * pink portal share a definition but must remain distinct linking groups).
 */
public record PortalLinkKey(
    @Nullable ResourceLocation definitionId,
    @Nullable DyeColor legacyColor,
    ResourceLocation frameMaterial) {

  public static PortalLinkKey of(CustomPortal portal) {
    if (portal.getDefinitionId() != null) {
      return new PortalLinkKey(portal.getDefinitionId(), null, portal.getFrameMaterial());
    }
    return new PortalLinkKey(null, portal.getColor(), portal.getFrameMaterial());
  }
}
