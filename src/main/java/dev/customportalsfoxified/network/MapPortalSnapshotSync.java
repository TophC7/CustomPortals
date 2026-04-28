package dev.customportalsfoxified.network;

import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.portal.PortalDefinitions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/** Sends authoritative portal snapshots to Map n HUD clients that opted in. */
public final class MapPortalSnapshotSync {

  private static final int MIN_RADIUS_BLOCKS = 192;
  private static final Set<UUID> SUBSCRIBED_PLAYERS = new HashSet<>();

  private MapPortalSnapshotSync() {}

  public static void sendToInterestedPlayers(MinecraftServer server) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      if (isSubscribed(player)) sendTo(player);
    }
  }

  public static void sendAround(ServerLevel level, CustomPortal portal) {
    BlockPos pos = portal.getSpawnPos();
    sendAround(level, pos.getX() + 0.5, pos.getZ() + 0.5);
  }

  public static void sendAround(ServerLevel level, double x, double z) {
    for (ServerPlayer player : level.players()) {
      if (!isSubscribed(player)) continue;
      int radius = syncRadius(player);
      double dx = player.getX() - x;
      double dz = player.getZ() - z;
      if (dx * dx + dz * dz <= (double) radius * radius) sendTo(player);
    }
  }

  public static void setSubscribed(ServerPlayer player, boolean subscribed) {
    if (subscribed) {
      SUBSCRIBED_PLAYERS.add(player.getUUID());
      sendTo(player);
    } else {
      remove(player);
    }
  }

  public static void remove(ServerPlayer player) {
    SUBSCRIBED_PLAYERS.remove(player.getUUID());
  }

  public static void clearSubscriptions() {
    SUBSCRIBED_PLAYERS.clear();
  }

  public static void sendTo(ServerPlayer player) {
    if (!isSubscribed(player)) return;
    if (!NetworkRegistry.hasChannel(player.connection, MapPortalSnapshotsPayload.TYPE.id())) return;
    ServerLevel level = player.serverLevel();
    int radius = syncRadius(player);
    double radiusSq = (double) radius * radius;
    ArrayList<MapPortalSnapshotsPayload.Entry> entries = new ArrayList<>();

    for (CustomPortal portal : PortalSavedData.registry(level).getAll()) {
      BlockPos pos = portal.getSpawnPos();
      double dx = pos.getX() + 0.5 - player.getX();
      double dz = pos.getZ() + 0.5 - player.getZ();
      if (dx * dx + dz * dz > radiusSq) continue;
      MapPortalSnapshotsPayload.Entry entry = entry(player.server, portal);
      if (entry != null) entries.add(entry);
      if (entries.size() >= MapPortalSnapshotsPayload.MAX_ENTRIES) break;
    }

    PacketDistributor.sendToPlayer(player, new MapPortalSnapshotsPayload(
        level.dimension().location(), player.getX(), player.getZ(), radius, List.copyOf(entries)));
  }

  private static boolean isSubscribed(ServerPlayer player) {
    return SUBSCRIBED_PLAYERS.contains(player.getUUID());
  }

  private static int syncRadius(ServerPlayer player) {
    int viewDistance = player.server.getPlayerList().getViewDistance();
    return Math.max(MIN_RADIUS_BLOCKS, (viewDistance + 1) * 16);
  }

  private static @Nullable MapPortalSnapshotsPayload.Entry entry(
      MinecraftServer server, CustomPortal portal) {
    Bounds bounds = bounds(portal);
    if (bounds == null) return null;
    return new MapPortalSnapshotsPayload.Entry(
        portal.getId(),
        bounds.minX(),
        bounds.minY(),
        bounds.minZ(),
        bounds.maxX(),
        bounds.maxY(),
        bounds.maxZ(),
        portal.getAxis().getSerializedName(),
        portal.getColor().getSerializedName(),
        portal.getColor().getTextColor(),
        portal.getFrameMaterial(),
        PortalDefinitions.shouldPortalStayLit(portal),
        destination(server, portal));
  }

  private static @Nullable MapPortalSnapshotsPayload.Destination destination(
      MinecraftServer server, CustomPortal portal) {
    CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, server);
    if (partner == null) return null;
    BlockPos pos = partner.getSpawnPos();
    return new MapPortalSnapshotsPayload.Destination(
        partner.getId(),
        partner.getDimension().location(),
        pos.getX() + 0.5,
        pos.getY() + 0.5,
        pos.getZ() + 0.5);
  }

  private static @Nullable Bounds bounds(CustomPortal portal) {
    if (portal.getPortalBlocks().isEmpty()) return null;
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (BlockPos pos : portal.getPortalBlocks()) {
      minX = Math.min(minX, pos.getX());
      minY = Math.min(minY, pos.getY());
      minZ = Math.min(minZ, pos.getZ());
      maxX = Math.max(maxX, pos.getX());
      maxY = Math.max(maxY, pos.getY());
      maxZ = Math.max(maxZ, pos.getZ());
    }
    return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
  }

  private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
}
