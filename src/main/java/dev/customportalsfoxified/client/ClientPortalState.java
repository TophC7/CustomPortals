package dev.customportalsfoxified.client;

// client-side portal state for overlay rendering
// updated by S2C payloads, read by PortalOverlayLayer
public class ClientPortalState {

  private static int overlayColor = -1;
  private static boolean transitioning = false;

  public static int getOverlayColor() {
    return overlayColor;
  }

  public static void setOverlayColor(int color) {
    overlayColor = color;
  }

  public static boolean isTransitioning() {
    return transitioning;
  }

  public static void setTransitioning(boolean active) {
    transitioning = active;
  }

  public static void reset() {
    overlayColor = -1;
    transitioning = false;
  }
}
