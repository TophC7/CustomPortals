package dev.customportalsfoxified.network;

import dev.customportalsfoxified.CustomPortalsFoxified;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client opt-in for optional Map n HUD portal snapshots. */
public record MapPortalSubscriptionPayload(boolean subscribed) implements CustomPacketPayload {

  public static final Type<MapPortalSubscriptionPayload> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath(CustomPortalsFoxified.MOD_ID,
          "map_portal_subscription"));

  public static final StreamCodec<FriendlyByteBuf, MapPortalSubscriptionPayload> STREAM_CODEC =
      StreamCodec.of(
          (buf, payload) -> buf.writeBoolean(payload.subscribed),
          buf -> new MapPortalSubscriptionPayload(buf.readBoolean()));

  public static void handle(MapPortalSubscriptionPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
      if (context.player() instanceof ServerPlayer player) {
        MapPortalSnapshotSync.setSubscribed(player, payload.subscribed);
      }
    });
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
