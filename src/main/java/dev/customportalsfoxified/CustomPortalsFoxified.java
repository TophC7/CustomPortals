package dev.customportalsfoxified;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import dev.customportalsfoxified.network.ScreenTransitionPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CustomPortalsFoxified.MOD_ID)
public class CustomPortalsFoxified {

    public static final String MOD_ID = "custom_portals_foxified";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CustomPortalsFoxified(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Custom Portals Foxified loaded");

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);

        modContainer.registerConfig(ModConfig.Type.COMMON, CPConfig.SPEC);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                SyncPortalColorPayload.TYPE,
                SyncPortalColorPayload.STREAM_CODEC,
                SyncPortalColorPayload::handle);
        registrar.playToClient(
                ScreenTransitionPayload.TYPE,
                ScreenTransitionPayload.STREAM_CODEC,
                ScreenTransitionPayload::handle);
    }
}
