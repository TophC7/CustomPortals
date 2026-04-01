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
    private final Map<UUID, CustomPortal> idIndex = new HashMap<>();

    public @Nullable CustomPortal getPortalAt(BlockPos pos) {
        return positionIndex.get(pos);
    }

    public @Nullable CustomPortal getPortalById(UUID id) {
        return idIndex.get(id);
    }

    public List<CustomPortal> getAll() {
        return Collections.unmodifiableList(portals);
    }

    public void registerPortal(CustomPortal portal) {
        portals.add(portal);
        idIndex.put(portal.getId(), portal);
        for (BlockPos pos : portal.getPortalBlocks()) {
            positionIndex.put(pos, portal);
        }
    }

    public void removePortal(CustomPortal portal) {
        portals.remove(portal);
        idIndex.remove(portal.getId());
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
                if (portal.canLinkWith(candidate)) {
                    portal.link(candidate);
                    data.setDirty();
                    CustomPortalsFoxified.LOGGER.debug("Linked portals {} <-> {}", portal.getId(), candidate.getId());
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
        if (portal.isLinked()) {
            ServerLevel partnerLevel = server.getLevel(portal.getLinkedDimension());
            if (partnerLevel != null) {
                PortalSavedData partnerData = PortalSavedData.get(partnerLevel);
                CustomPortal partner = partnerData.getRegistry().getPortalById(portal.getLinkedPortalId());
                if (partner != null) {
                    partner.unlink();
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
        idIndex.clear();
        if (tag.contains("portals")) {
            ListTag list = tag.getList("portals", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CustomPortal portal = CustomPortal.load(list.getCompound(i));
                registerPortal(portal);
            }
        }
    }
}
