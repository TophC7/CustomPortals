package dev.customportalsfoxified.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-side portal overlay state.
 *
 * <p>Color is set by {@code SyncPortalColorPayload} on first portal entry.
 * Vanilla manages {@code spinningEffectIntensity} via CONFUSION transition.
 * We suppress the vanilla purple overlay and render our own colored one.
 *
 * <p>Effects are cleared instantly when the player leaves the portal or teleports.
 */
public class ClientPortalState {

  private static int overlayColor = -1;
  private static float previousIntensity = 0;

  // post-teleport cooldown prevents re-entering destination portal
  private static int clientCooldown = 0;
  private static final int COOLDOWN_TICKS = 40;

  public static int getOverlayColor() {
    return overlayColor;
  }

  public static void setOverlayColor(int color) {
    overlayColor = color;
  }

  public static boolean isOnCooldown() {
    return clientCooldown > 0;
  }

  /**
   * Called once per game tick from the render path.
   * Detects when intensity starts decreasing (player left portal
   * or teleported) and clears all effects instantly.
   */
  public static void tick() {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;

    if (clientCooldown > 0) {
      clientCooldown--;
    }

    float currentIntensity = player.spinningEffectIntensity;

    // intensity decreasing = player is no longer in the portal
    if (overlayColor >= 0 && currentIntensity < previousIntensity) {
      clientCooldown = COOLDOWN_TICKS;
      clearPortalVisuals(player);
      currentIntensity = 0;
    }

    previousIntensity = currentIntensity;
  }

  /** Suppress vanilla purple overlay before CAMERA_OVERLAYS renders. */
  public static void suppressVanillaOverlay(LocalPlayer player) {
    player.spinningEffectIntensity = 0;
    player.oSpinningEffectIntensity = 0;
  }

  /** Kill all portal visual effects instantly. */
  public static void clearPortalVisuals(LocalPlayer player) {
    overlayColor = -1;
    previousIntensity = 0;
    player.spinningEffectIntensity = 0;
    player.oSpinningEffectIntensity = 0;
    player.portalProcess = null;
  }
}
