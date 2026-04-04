package dev.customportalsfoxified.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModBlocks;
import dev.customportalsfoxified.config.CPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import xyz.kwahson.core.config.SafeConfig;

/**
 * Replaces the vanilla purple nether portal overlay with a colored one.
 *
 * <p>Intercepts CAMERA_OVERLAYS via RenderGuiLayerEvent:
 * Pre: tick detection + suppress vanilla purple overlay.
 * Post: restore intensity + render our colored overlay.
 *
 * <p>Effects clear instantly when the player leaves the portal
 * (intensity starts decreasing). No gradual fade-out.
 */
@EventBusSubscriber(modid = CustomPortalsFoxified.MOD_ID, value = Dist.CLIENT)
public class PortalOverlayLayer {

  private static boolean clientConfigValidated;
  private static long lastTickedGameTime = -1;
  private static float savedIntensity = 0;
  private static float savedOldIntensity = 0;
  private static boolean suppressed = false;

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Pre event) {
    if (!clientConfigValidated && CPConfig.CLIENT_SPEC.isLoaded()) {
      clientConfigValidated = true;
      SafeConfig.validateOrReset(CustomPortalsFoxified.MOD_ID, CPConfig.CLIENT_SPEC,
          CPConfig.MUTE_SOUNDS);
    }
  }

  /**
   * Suppresses portal trigger and travel sounds when muted and in a custom portal.
   * Ambient sounds are already handled in CustomPortalBlock.animateTick.
   */
  @SubscribeEvent
  public static void onPlaySound(PlaySoundEvent event) {
    if (!SafeConfig.getBool(CPConfig.MUTE_SOUNDS, false)) return;

    boolean inCustomPortal =
        ClientPortalState.getOverlayColor() >= 0 || ClientPortalState.isOnCooldown();
    if (!inCustomPortal) return;

    String name = event.getName();
    if (name.equals("block.portal.trigger") || name.equals("block.portal.travel")) {
      event.setSound(null);
    }
  }

  @SubscribeEvent
  public static void onPreCameraOverlays(RenderGuiLayerEvent.Pre event) {
    if (!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) return;
    suppressed = false;

    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;

    // run detection once per game tick
    if (Minecraft.getInstance().level != null) {
      long gameTime = Minecraft.getInstance().level.getGameTime();
      if (gameTime != lastTickedGameTime) {
        lastTickedGameTime = gameTime;
        ClientPortalState.tick();
      }
    }

    int color = ClientPortalState.getOverlayColor();
    float intensity = player.spinningEffectIntensity;

    if (color >= 0 && intensity > 0) {
      // save real values, zero out so vanilla skips its purple overlay
      savedIntensity = player.spinningEffectIntensity;
      savedOldIntensity = player.oSpinningEffectIntensity;
      ClientPortalState.suppressVanillaOverlay(player);
      suppressed = true;
    }
  }

  @SubscribeEvent
  public static void onPostCameraOverlays(RenderGuiLayerEvent.Post event) {
    if (!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) return;
    if (!suppressed) return;
    suppressed = false;

    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;

    // restore real intensity so the rest of the frame is correct
    player.spinningEffectIntensity = savedIntensity;
    player.oSpinningEffectIntensity = savedOldIntensity;

    int colorId = ClientPortalState.getOverlayColor();
    if (colorId < 0) return;

    float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
    float lerpedIntensity = Mth.lerp(partialTick, savedOldIntensity, savedIntensity);
    renderPortalOverlay(event.getGuiGraphics(), lerpedIntensity, colorId);
  }

  /**
   * Renders the portal overlay tinted with the catalyst's dye color.
   * Uses our portal block's particle texture + vanilla's alpha curve.
   */
  private static void renderPortalOverlay(GuiGraphics graphics, float intensity, int colorId) {
    float alpha = intensity;
    if (alpha < 1.0F) {
      alpha *= alpha;
      alpha *= alpha;
      alpha = alpha * 0.8F + 0.2F;
    }

    DyeColor color = DyeColor.byId(colorId);
    int rgb = color.getTextColor();
    float r = ((rgb >> 16) & 0xFF) / 255.0F;
    float g = ((rgb >> 8) & 0xFF) / 255.0F;
    float b = (rgb & 0xFF) / 255.0F;

    BlockState portalState = ModBlocks.CUSTOM_PORTAL.get().defaultBlockState();
    TextureAtlasSprite sprite =
        Minecraft.getInstance()
            .getBlockRenderer()
            .getBlockModelShaper()
            .getParticleIcon(portalState);

    RenderSystem.disableDepthTest();
    RenderSystem.depthMask(false);
    RenderSystem.enableBlend();
    graphics.setColor(r, g, b, alpha);
    graphics.blit(0, 0, -90, graphics.guiWidth(), graphics.guiHeight(), sprite);
    RenderSystem.disableBlend();
    RenderSystem.depthMask(true);
    RenderSystem.enableDepthTest();
    graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
  }
}
