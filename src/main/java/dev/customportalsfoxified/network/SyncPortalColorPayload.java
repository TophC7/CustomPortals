package dev.customportalsfoxified.network;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.client.ClientPortalState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPortalColorPayload(int colorId) implements CustomPacketPayload {

    public static final Type<SyncPortalColorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CustomPortalsFoxified.MOD_ID, "sync_portal_color"));

    public static final StreamCodec<FriendlyByteBuf, SyncPortalColorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.colorId),
                    buf -> new SyncPortalColorPayload(buf.readVarInt()));

    public static void handle(SyncPortalColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPortalState.setOverlayColor(payload.colorId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
