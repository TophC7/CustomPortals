package dev.customportalsfoxified.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

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
