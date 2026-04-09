package dev.customportalsfoxified.data;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.blocks.CustomPortalBlock;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

public class PortalRegistry {

  private final LinkedHashSet<CustomPortal> portals = new LinkedHashSet<>();
  private final Map<BlockPos, CustomPortal> positionIndex = new HashMap<>();
  private final Map<UUID, CustomPortal> idIndex = new HashMap<>();
  private final Map<PortalLinkKey, List<CustomPortal>> linkIndex = new HashMap<>();

  public @Nullable CustomPortal getPortalAt(BlockPos pos) {
    return positionIndex.get(pos);
  }

  public @Nullable CustomPortal getPortalById(UUID id) {
    return idIndex.get(id);
  }

  public Collection<CustomPortal> getAll() {
    return Collections.unmodifiableCollection(portals);
  }

  public List<CustomPortal> getByLinkKey(PortalLinkKey linkKey) {
    return Collections.unmodifiableList(
        linkIndex.getOrDefault(linkKey, Collections.emptyList()));
  }

  public void registerPortal(CustomPortal portal) {
    portals.add(portal);
    idIndex.put(portal.getId(), portal);
    for (BlockPos pos : portal.getPortalBlocks()) {
      positionIndex.put(pos, portal);
    }
    linkIndex.computeIfAbsent(PortalLinkKey.of(portal), k -> new ArrayList<>()).add(portal);
  }

  public void removePortal(CustomPortal portal) {
    portals.remove(portal);
    idIndex.remove(portal.getId());
    for (BlockPos pos : portal.getPortalBlocks()) {
      positionIndex.remove(pos);
    }
    List<CustomPortal> linkList = linkIndex.get(PortalLinkKey.of(portal));
    if (linkList != null) {
      linkList.remove(portal);
      if (linkList.isEmpty()) linkIndex.remove(PortalLinkKey.of(portal));
    }
  }

  /**
   * Try to link a portal with the closest compatible unlinked portal across all dimensions.
   * Returns the linked partner if successful.
   */
  public @Nullable CustomPortal tryLinkAcrossAll(CustomPortal portal, MinecraftServer server) {
    CustomPortal bestCandidate = null;
    ServerLevel bestLevel = null;
    long bestDistSq = Long.MAX_VALUE;

    PortalLinkKey linkKey = PortalLinkKey.of(portal);
    for (ServerLevel level : server.getAllLevels()) {
      for (CustomPortal candidate : PortalSavedData.registry(level).getByLinkKey(linkKey)) {
        if (portal.canLinkWith(candidate)) {
          long distSq = portal.calculateDistanceSquared(candidate);
          if (distSq < bestDistSq) {
            bestCandidate = candidate;
            bestLevel = level;
            bestDistSq = distSq;
          }
        }
      }
    }

    if (bestCandidate == null) return null;

    portal.link(bestCandidate);
    PortalSavedData.get(bestLevel).setDirty();

    // push LIT state on both portals now that they're linked
    CustomPortalBlock.updateLitState(bestLevel, bestCandidate);
    ServerLevel portalLevel = server.getLevel(portal.getDimension());
    if (portalLevel != null) {
      CustomPortalBlock.updateLitState(portalLevel, portal);
    }

    CustomPortalsFoxified.LOGGER.debug(
        "Linked portals {} <-> {}", portal.getId(), bestCandidate.getId());
    return bestCandidate;
  }

  /** Remove a portal and try to relink its former partner. */
  public void removeAndRelink(CustomPortal portal, MinecraftServer server) {
    if (portal.isLinked()) {
      CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, server);
      if (partner != null) {
        partner.unlink();
        ServerLevel partnerLevel = server.getLevel(partner.getDimension());
        if (partnerLevel != null) {
          CustomPortalBlock.updateLitState(partnerLevel, partner);
          PortalSavedData partnerData = PortalSavedData.get(partnerLevel);
          partnerData.getRegistry().tryLinkAcrossAll(partner, server);
          partnerData.setDirty();
        }
      }
    }
    removePortal(portal);
  }

  // SERIALIZATION //

  public void save(CompoundTag tag, HolderLookup.Provider registries) {
    ListTag list = new ListTag();
    for (CustomPortal portal : portals) {
      list.add(portal.save(registries));
    }
    tag.put("portals", list);
  }

  public void load(CompoundTag tag, HolderLookup.Provider registries) {
    portals.clear();
    positionIndex.clear();
    idIndex.clear();
    linkIndex.clear();
    if (tag.contains("portals")) {
      ListTag list = tag.getList("portals", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        CustomPortal portal = CustomPortal.load(list.getCompound(i), registries);
        if (portal != null) {
          registerPortal(portal);
        } else {
          CustomPortalsFoxified.LOGGER.warn("Skipping corrupted portal entry at index {}", i);
        }
      }
    }
  }
}
