package dev.customportalsfoxified.network;

import dev.customportalsfoxified.CustomPortalsFoxified;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Optional Map n HUD portal snapshot stream. */
public record MapPortalSnapshotsPayload(
    ResourceLocation dimensionId,
    double centerX,
    double centerZ,
    int radiusBlocks,
    List<Entry> entries) implements CustomPacketPayload {

  public static final int MAX_ENTRIES = 4096;
  private static final int MAX_SHORT_TEXT_LENGTH = 32;
  private static boolean mapReceiverChecked;
  private static Method mapReceiver;

  public static final Type<MapPortalSnapshotsPayload> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath(CustomPortalsFoxified.MOD_ID,
          "map_portal_snapshots"));

  public static final StreamCodec<FriendlyByteBuf, MapPortalSnapshotsPayload> STREAM_CODEC =
      StreamCodec.of(MapPortalSnapshotsPayload::write, MapPortalSnapshotsPayload::read);

  public static void handle(MapPortalSnapshotsPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
      try {
        Method method = mapReceiver();
        if (method == null) return;
        method.invoke(null, payload.dimensionId, payload.centerX, payload.centerZ,
            payload.radiusBlocks, payload.entries);
      } catch (ReflectiveOperationException ex) {
        CustomPortalsFoxified.LOGGER.warn("Failed to deliver portal snapshots to Map n HUD", ex);
      }
    });
  }

  private static Method mapReceiver() {
    if (mapReceiverChecked) return mapReceiver;
    mapReceiverChecked = true;
    try {
      Class<?> manager = Class.forName("dev.mapnhud.client.portal.PortalManager");
      mapReceiver = manager.getMethod("applyCustomPortalSnapshots",
          ResourceLocation.class, double.class, double.class, int.class, List.class);
    } catch (ClassNotFoundException ex) {
      mapReceiver = null;
    } catch (ReflectiveOperationException ex) {
      CustomPortalsFoxified.LOGGER.warn("Map n HUD portal receiver is unavailable", ex);
      mapReceiver = null;
    }
    return mapReceiver;
  }

  private static void write(FriendlyByteBuf buf, MapPortalSnapshotsPayload payload) {
    buf.writeResourceLocation(payload.dimensionId);
    buf.writeDouble(payload.centerX);
    buf.writeDouble(payload.centerZ);
    buf.writeVarInt(payload.radiusBlocks);
    if (payload.entries.size() > MAX_ENTRIES) {
      throw new IllegalArgumentException(
          "Portal snapshot has too many entries: " + payload.entries.size());
    }
    buf.writeVarInt(payload.entries.size());
    for (Entry entry : payload.entries) writeEntry(buf, entry);
  }

  private static MapPortalSnapshotsPayload read(FriendlyByteBuf buf) {
    ResourceLocation dimensionId = buf.readResourceLocation();
    double centerX = buf.readDouble();
    double centerZ = buf.readDouble();
    int radiusBlocks = buf.readVarInt();
    int count = buf.readVarInt();
    if (count < 0 || count > MAX_ENTRIES) {
      throw new IllegalArgumentException("Invalid portal snapshot entry count: " + count);
    }
    ArrayList<Entry> entries = new ArrayList<>(count);
    for (int i = 0; i < count; i++) entries.add(readEntry(buf));
    return new MapPortalSnapshotsPayload(dimensionId, centerX, centerZ, radiusBlocks,
        List.copyOf(entries));
  }

  private static void writeEntry(FriendlyByteBuf buf, Entry entry) {
    buf.writeUUID(entry.id);
    buf.writeVarInt(entry.minX);
    buf.writeVarInt(entry.minY);
    buf.writeVarInt(entry.minZ);
    buf.writeVarInt(entry.maxX);
    buf.writeVarInt(entry.maxY);
    buf.writeVarInt(entry.maxZ);
    buf.writeUtf(entry.axis, MAX_SHORT_TEXT_LENGTH);
    buf.writeUtf(entry.colorName, MAX_SHORT_TEXT_LENGTH);
    buf.writeVarInt(entry.portalColor);
    buf.writeResourceLocation(entry.frameBlockId);
    buf.writeBoolean(entry.active);
    buf.writeBoolean(entry.destination != null);
    if (entry.destination != null) writeDestination(buf, entry.destination);
  }

  private static Entry readEntry(FriendlyByteBuf buf) {
    UUID id = buf.readUUID();
    int minX = buf.readVarInt();
    int minY = buf.readVarInt();
    int minZ = buf.readVarInt();
    int maxX = buf.readVarInt();
    int maxY = buf.readVarInt();
    int maxZ = buf.readVarInt();
    String axis = buf.readUtf(MAX_SHORT_TEXT_LENGTH);
    String colorName = buf.readUtf(MAX_SHORT_TEXT_LENGTH);
    int portalColor = buf.readVarInt();
    ResourceLocation frameBlockId = buf.readResourceLocation();
    boolean active = buf.readBoolean();
    Destination destination = buf.readBoolean() ? readDestination(buf) : null;
    return new Entry(id, minX, minY, minZ, maxX, maxY, maxZ, axis, colorName, portalColor,
        frameBlockId, active, destination);
  }

  private static void writeDestination(FriendlyByteBuf buf, Destination destination) {
    buf.writeUUID(destination.portalId);
    buf.writeResourceLocation(destination.dimensionId);
    buf.writeDouble(destination.x);
    buf.writeDouble(destination.y);
    buf.writeDouble(destination.z);
  }

  private static Destination readDestination(FriendlyByteBuf buf) {
    return new Destination(buf.readUUID(), buf.readResourceLocation(), buf.readDouble(),
        buf.readDouble(), buf.readDouble());
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  public record Entry(
      UUID id,
      int minX,
      int minY,
      int minZ,
      int maxX,
      int maxY,
      int maxZ,
      String axis,
      String colorName,
      int portalColor,
      ResourceLocation frameBlockId,
      boolean active,
      Destination destination) {}

  public record Destination(
      UUID portalId,
      ResourceLocation dimensionId,
      double x,
      double y,
      double z) {}
}
