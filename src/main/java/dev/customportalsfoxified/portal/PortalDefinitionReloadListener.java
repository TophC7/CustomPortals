package dev.customportalsfoxified.portal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.customportalsfoxified.CustomPortalsFoxified;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public class PortalDefinitionReloadListener extends SimpleJsonResourceReloadListener {

  public static final String DIRECTORY = CustomPortalsFoxified.MOD_ID + "/portal_definitions";

  private static final Gson GSON = new GsonBuilder().create();

  public PortalDefinitionReloadListener() {
    super(GSON, DIRECTORY);
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
    LinkedHashMap<ResourceLocation, PortalDefinition> loaded = new LinkedHashMap<>();

    for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
      JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "portal definition");
      PortalDefinition definition = PortalDefinition.fromJson(entry.getKey(), json);
      loaded.put(definition.id(), definition);
    }

    boolean builtInAllowlistEnabled =
        !resourceManager
            .getResourceStack(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    CustomPortalsFoxified.MOD_ID, "tags/block/portal_frame_allowlist.json"))
            .isEmpty();

    PortalDefinitions.replaceDatapackDefinitions(loaded, builtInAllowlistEnabled);
    CustomPortalsFoxified.LOGGER.info("Loaded {} portal definitions", loaded.size());
  }
}
