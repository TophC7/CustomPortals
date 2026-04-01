package dev.customportalsfoxified.util;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModAttachments;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CustomPortalsFoxified.MOD_ID)
public class PortalEventHandler {

    // NOTE: NeoForge bug #2510 — synced data attachments aren't re-sent after
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
