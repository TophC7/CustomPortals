package dev.customportalsfoxified.util;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModAttachments;
import dev.customportalsfoxified.blocks.CustomPortalBlock;
import dev.customportalsfoxified.config.CPConfig;
import dev.customportalsfoxified.data.CustomPortal;
import dev.customportalsfoxified.data.PortalSavedData;
import dev.customportalsfoxified.network.SyncPortalColorPayload;
import dev.customportalsfoxified.portal.PortalDefinition;
import dev.customportalsfoxified.portal.PortalDefinitionReloadListener;
import dev.customportalsfoxified.portal.PortalDefinitions;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import xyz.kwahson.core.config.SafeConfig;

@EventBusSubscriber(modid = CustomPortalsFoxified.MOD_ID)
public class PortalEventHandler {

  private static boolean commonConfigValidated;

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Pre event) {
    if (!commonConfigValidated && CPConfig.COMMON_SPEC.isLoaded()) {
      commonConfigValidated = true;
      SafeConfig.validateOrReset(CustomPortalsFoxified.MOD_ID, CPConfig.COMMON_SPEC,
          "common", CPConfig.MAX_PORTAL_SIZE, CPConfig.MIN_PORTAL_SIZE, CPConfig.BASE_RANGE);
    }

    if (PortalDefinitions.consumeValidationPending()) {
      revalidatePortals(event.getServer());
    }
  }

  // reset so config is re-validated on next server start within the same JVM
  // (singleplayer: quit world -> new world without restarting)
  @SubscribeEvent
  public static void onServerStopped(ServerStoppedEvent event) {
    commonConfigValidated = false;
    PortalDefinitions.clearDatapackDefinitions();
    PortalDefinitions.clearValidationPending();
  }

  // NOTE: NeoForge bug #2510
  // synced data attachments aren't re-sent after
  // dimension change. Manually resync portal color when a player changes dimension.
  @SubscribeEvent
  public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer sp) {
      int color = sp.getData(ModAttachments.PORTAL_COLOR.get());
      if (color >= 0) {
        PacketDistributor.sendToPlayer(sp, new SyncPortalColorPayload(color));
      }
    }
  }

  @SubscribeEvent
  public static void onAddReloadListener(AddReloadListenerEvent event) {
    event.addListener(new PortalDefinitionReloadListener());
  }

  @SubscribeEvent
  public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    if (event.getFace() == null) return;

    InteractionResult result =
        tryActivatePortal(
            level,
            event.getPos(),
            event.getFace(),
            event.getItemStack(),
            event.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    if (!result.consumesAction()) return;

    event.setCancellationResult(result);
    event.setCanceled(true);
  }

  public static InteractionResult tryActivatePortal(
      ServerLevel level,
      BlockPos clickedPos,
      net.minecraft.core.Direction face,
      net.minecraft.world.item.ItemStack catalystStack,
      ServerPlayer serverPlayer) {
    if (face == null || catalystStack.isEmpty()) return InteractionResult.PASS;

    BlockPos airPos = clickedPos.relative(face);
    if (!level.getBlockState(airPos).isAir()) return InteractionResult.PASS;

    PortalDefinition definition =
        PortalDefinitions.resolveActivation(level.getBlockState(clickedPos), catalystStack);
    if (definition == null || !definition.canActivateWith(catalystStack)) {
      return InteractionResult.PASS;
    }

    boolean built =
        PortalHelper.buildPortal(
            level,
            airPos,
            clickedPos,
            definition,
            definition.createReturnCatalyst(catalystStack));
    if (!built) return InteractionResult.PASS;

    level.playSound(null, airPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
    if (serverPlayer == null || !serverPlayer.isCreative()) {
      definition.applyCatalystCost(level, serverPlayer, catalystStack);
    }
    return InteractionResult.SUCCESS;
  }

  private static void revalidatePortals(MinecraftServer server) {
    int disabledCount = 0;
    int reenabledCount = 0;
    int unlinkedCount = 0;
    int relinkedCount = 0;

    for (ServerLevel level : server.getAllLevels()) {
      PortalSavedData data = PortalSavedData.get(level);
      for (CustomPortal portal : new ArrayList<>(data.getRegistry().getAll())) {
        boolean allowed =
            PortalDefinitions.isPortalAllowed(portal.getDefinitionId(), portal.getFrameMaterial());
        if (portal.isDefinitionDisabled() == !allowed) {
          continue;
        }

        portal.setDefinitionDisabled(!allowed);
        if (allowed) {
          reenabledCount++;
        } else {
          disabledCount++;
          if (portal.isLinked()) {
            unlinkPortal(server, level, portal);
            unlinkedCount++;
          }
        }

        CustomPortalBlock.updateLitState(level, portal);
        data.setDirty();
      }
    }

    for (ServerLevel level : server.getAllLevels()) {
      PortalSavedData data = PortalSavedData.get(level);
      for (CustomPortal portal : new ArrayList<>(data.getRegistry().getAll())) {
        if (portal.isDefinitionDisabled() || !portal.isLinked()) continue;

        CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, server);
        if (partner != null && portal.isCompatibleWith(partner)) {
          continue;
        }

        unlinkPortal(server, level, portal);
        unlinkedCount++;
        data.setDirty();
      }
    }

    for (ServerLevel level : server.getAllLevels()) {
      PortalSavedData data = PortalSavedData.get(level);
      for (CustomPortal portal : new ArrayList<>(data.getRegistry().getAll())) {
        if (portal.isLinked() || portal.isDefinitionDisabled() || portal.isRedstoneDisabled()) {
          continue;
        }
        if (PortalLinkHelper.tryResolveLink(level, portal) != null) {
          relinkedCount++;
          data.setDirty();
        } else {
          CustomPortalBlock.updateLitState(level, portal);
        }
      }
    }

    CustomPortalsFoxified.LOGGER.info(
        "Revalidated portals after datapack reload: disabled={}, reenabled={}, unlinked={}, relinked={}",
        disabledCount,
        reenabledCount,
        unlinkedCount,
        relinkedCount);
  }

  private static void unlinkPortal(MinecraftServer server, ServerLevel level, CustomPortal portal) {
    if (!portal.isLinked()) {
      CustomPortalBlock.updateLitState(level, portal);
      return;
    }

    CustomPortal partner = PortalSavedData.resolveLinkedPartner(portal, server);
    if (partner != null) {
      portal.unlinkFrom(partner);

      ServerLevel partnerLevel = server.getLevel(partner.getDimension());
      if (partnerLevel != null) {
        CustomPortalBlock.updateLitState(partnerLevel, partner);
        PortalSavedData.get(partnerLevel).setDirty();
      }
    } else {
      portal.unlink();
    }

    CustomPortalBlock.updateLitState(level, portal);
  }
}
