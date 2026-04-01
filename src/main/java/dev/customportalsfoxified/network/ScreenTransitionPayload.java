package dev.customportalsfoxified.network;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.client.ClientPortalState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScreenTransitionPayload(boolean active) implements CustomPacketPayload {

  public static final Type<ScreenTransitionPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(CustomPortalsFoxified.MOD_ID, "screen_transition"));

  public static final StreamCodec<FriendlyByteBuf, ScreenTransitionPayload> STREAM_CODEC =
      StreamCodec.of(
          (buf, p) -> buf.writeBoolean(p.active),
          buf -> new ScreenTransitionPayload(buf.readBoolean()));

  public static void handle(ScreenTransitionPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> ClientPortalState.setTransitioning(payload.active));
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
