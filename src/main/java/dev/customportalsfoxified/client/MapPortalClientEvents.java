package dev.customportalsfoxified.client;

import dev.customportalsfoxified.CustomPortalsFoxified;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client-side connection events for optional Map n HUD integration. */
@EventBusSubscriber(modid = CustomPortalsFoxified.MOD_ID, value = Dist.CLIENT)
public final class MapPortalClientEvents {

  private MapPortalClientEvents() {}

  @SubscribeEvent
  public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
    MapPortalSubscriptionClient.subscribeIfMapPresent();
  }
}
