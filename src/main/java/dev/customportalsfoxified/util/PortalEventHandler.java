package dev.customportalsfoxified.util;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModAttachments;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import xyz.kwahson.core.config.SafeConfig;

@EventBusSubscriber(modid = CustomPortalsFoxified.MOD_ID)
public class PortalEventHandler {

  private static boolean commonConfigValidated;

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Pre event) {
    if (!commonConfigValidated && CPConfig.COMMON_SPEC.isLoaded()) {
      commonConfigValidated = true;
      SafeConfig.validateOrReset(CustomPortalsFoxified.MOD_ID, CPConfig.COMMON_SPEC,
          "common", CPConfig.MAX_PORTAL_SIZE, CPConfig.MIN_PORTAL_SIZE, CPConfig.BASE_RANGE);
    }
  }

  // reset so config is re-validated on next server start within the same JVM
  // (singleplayer: quit world -> new world without restarting)
  @SubscribeEvent
  public static void onServerStopped(ServerStoppedEvent event) {
    commonConfigValidated = false;
  }

  // NOTE: NeoForge bug #2510
  // synced data attachments aren't re-sent after
  // dimension change. Manually resync portal color when a player changes dimension.
  @SubscribeEvent
  public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) {
      int color = sp.getData(ModAttachments.PORTAL_COLOR.get());
      if (color >= 0) {
        PacketDistributor.sendToPlayer(sp, new SyncPortalColorPayload(color));
      }
    }
  }
}
