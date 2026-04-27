package dev.customportalsfoxified.util;

import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.portal.PortalDefinition;
import dev.customportalsfoxified.portal.PortalDefinitions;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class PortalLinkHelper {

  private PortalLinkHelper() {}

  public static @Nullable CustomPortal tryResolveLink(ServerLevel level, CustomPortal portal) {
    if (portal.isDefinitionDisabled() || portal.isRedstoneDisabled()) {
      return null;
    }

    PortalDefinition definition =
        portal.getDefinitionId() != null ? PortalDefinitions.get(portal.getDefinitionId()) : null;
    if (definition == null || !definition.usesCounterpartRoute()) {
      return PortalSavedData.get(level).getRegistry().tryLinkAcrossAll(portal, level.getServer());
    }

    return PortalCounterpartHelper.ensureCounterpartLinked(level, portal, definition);
  }
}
