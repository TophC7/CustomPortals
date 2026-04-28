package dev.customportalsfoxified.client;

import dev.customportalsfoxified.network.MapPortalSubscriptionPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends CustomPortals' optional Map n HUD subscription when Map is installed. */
public final class MapPortalSubscriptionClient {

  private static final String MAP_PORTAL_MANAGER = "dev.mapnhud.client.portal.PortalManager";

  private MapPortalSubscriptionClient() {}

  public static void subscribeIfMapPresent() {
    if (!isMapPresent()) return;
    PacketDistributor.sendToServer(new MapPortalSubscriptionPayload(true));
  }

  private static boolean isMapPresent() {
    try {
      Class.forName(MAP_PORTAL_MANAGER, false,
          Thread.currentThread().getContextClassLoader());
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }
}
