package dev.customportalsfoxified.client;

import dev.customportalsfoxified.CustomPortalsFoxified;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
    modid = CustomPortalsFoxified.MOD_ID,
    value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD)
public class PortalOverlayLayer implements LayeredDraw.Layer {

  @SubscribeEvent
  public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
    event.registerAbove(
        VanillaGuiLayers.CAMERA_OVERLAYS,
        ResourceLocation.fromNamespaceAndPath(CustomPortalsFoxified.MOD_ID, "portal_overlay"),
        new PortalOverlayLayer());
  }

  @Override
  public void render(GuiGraphics graphics, DeltaTracker delta) {
    int colorId = ClientPortalState.getOverlayColor();
    if (colorId < 0) return;

    // only render while the player actually has an active portal process
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null || player.portalProcess == null) {
      ClientPortalState.reset();
      return;
    }

    DyeColor color = DyeColor.byId(colorId);
    int rgb = color.getTextColor();

    float r = ((rgb >> 16) & 0xFF) / 255.0F;
    float g = ((rgb >> 8) & 0xFF) / 255.0F;
    float b = (rgb & 0xFF) / 255.0F;

    // fade in based on how far through the transition we are
    float progress = (float) player.portalProcess.getPortalTime() / 80.0F;
    float alpha = Math.min(progress, 0.8F);

    int screenWidth = graphics.guiWidth();
    int screenHeight = graphics.guiHeight();

    int argb =
        ((int) (alpha * 255) << 24)
            | ((int) (r * 255) << 16)
            | ((int) (g * 255) << 8)
            | (int) (b * 255);
    graphics.fill(0, 0, screenWidth, screenHeight, argb);
  }
}
