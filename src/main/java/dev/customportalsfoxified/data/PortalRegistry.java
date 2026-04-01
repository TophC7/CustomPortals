package dev.customportalsfoxified.data;

import dev.customportalsfoxified.CustomPortalsFoxified;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PortalRegistry {

    private final List<CustomPortal> portals = new ArrayList<>();
    private final Map<BlockPos, CustomPortal> positionIndex = new HashMap<>();

    public @Nullable CustomPortal getPortalAt(BlockPos pos) {
        return positionIndex.get(pos);
    }

    public @Nullable CustomPortal getPortalById(UUID id) {
        for (CustomPortal portal : portals) {
            if (portal.getId().equals(id)) return portal;
        }
        return null;
    }

    public List<CustomPortal> getAll() {
        return Collections.unmodifiableList(portals);
    }

    public void registerPortal(CustomPortal portal) {
        portals.add(portal);
        for (BlockPos pos : portal.getPortalBlocks()) {
            positionIndex.put(pos, portal);
        }
    }

    public void removePortal(CustomPortal portal) {
        portals.remove(portal);
        for (BlockPos pos : portal.getPortalBlocks()) {
            positionIndex.remove(pos);
        }
    }

    /**
     * Try to link a portal with any compatible unlinked portal across all dimensions.
     * Returns the linked partner if successful.
     */
    public @Nullable CustomPortal tryLinkAcrossAll(CustomPortal portal, MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            PortalSavedData data = PortalSavedData.get(level);
            for (CustomPortal candidate : data.getRegistry().getAll()) {
                CustomPortalsFoxified.LOGGER.debug("Checking link: {} vs {} | sameId={} color={}/{} frame={}/{} linked={}/{}",
                        portal.getId(), candidate.getId(),
                        portal.getId().equals(candidate.getId()),
                        portal.getColor(), candidate.getColor(),
                        portal.getFrameMaterial(), candidate.getFrameMaterial(),
                        portal.isLinked(), candidate.isLinked());
                if (portal.canLinkWith(candidate)) {
                    portal.link(candidate);
                    data.setDirty();
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Remove a portal and try to relink its former partner.
     */
    public void removeAndRelink(CustomPortal portal, MinecraftServer server) {
        // unlink partner first
        if (portal.isLinked()) {
            ServerLevel partnerLevel = server.getLevel(portal.getLinkedDimension());
            if (partnerLevel != null) {
                PortalSavedData partnerData = PortalSavedData.get(partnerLevel);
                CustomPortal partner = partnerData.getRegistry().getPortalById(portal.getLinkedPortalId());
                if (partner != null) {
                    partner.unlink();
                    // try to find a new link for the orphaned partner
                    partnerData.getRegistry().tryLinkAcrossAll(partner, server);
                    partnerData.setDirty();
                }
            }
        }
        removePortal(portal);
    }

    // SERIALIZATION //

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (CustomPortal portal : portals) {
            list.add(portal.save());
        }
        tag.put("portals", list);
    }

    public void load(CompoundTag tag) {
        portals.clear();
        positionIndex.clear();
        if (tag.contains("portals")) {
            ListTag list = tag.getList("portals", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CustomPortal portal = CustomPortal.load(list.getCompound(i));
                registerPortal(portal);
            }
        }
    }
}
