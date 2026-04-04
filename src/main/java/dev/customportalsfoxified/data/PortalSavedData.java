package dev.customportalsfoxified.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

public class PortalSavedData extends SavedData {

  private final PortalRegistry registry = new PortalRegistry();

  private static final Factory<PortalSavedData> FACTORY =
      new Factory<>(PortalSavedData::new, PortalSavedData::load);

  public PortalSavedData() {}

  public static PortalSavedData get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(FACTORY, "custom_portals_foxified");
  }

  public PortalRegistry getRegistry() {
    return registry;
  }

  /** Shorthand for {@code get(level).getRegistry()} at pure-lookup sites. */
  public static PortalRegistry registry(ServerLevel level) {
    return get(level).getRegistry();
  }

  /**
   * Resolve a portal's linked partner across dimensions.
   * Returns null if unlinked, dimension missing, or partner no longer exists.
   */
  public static @Nullable CustomPortal resolveLinkedPartner(
      CustomPortal portal, MinecraftServer server) {
    if (!portal.isLinked() || portal.getLinkedDimension() == null) return null;
    ServerLevel partnerLevel = server.getLevel(portal.getLinkedDimension());
    if (partnerLevel == null) return null;
    return registry(partnerLevel).getPortalById(portal.getLinkedPortalId());
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    registry.save(tag);
    return tag;
  }

  public static PortalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
    PortalSavedData data = new PortalSavedData();
    data.registry.load(tag);
    return data;
  }
}
