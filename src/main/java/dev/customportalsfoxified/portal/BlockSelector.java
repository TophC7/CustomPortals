package dev.customportalsfoxified.portal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockSelector {

  boolean matches(BlockState state);

  int specificity();

  Set<ResourceLocation> resolveBlockIds(HolderLookup.Provider registryLookup);

  static BlockSelector any() {
    return AnyBlockSelector.INSTANCE;
  }

  static BlockSelector exact(ResourceLocation blockId) {
    return new ExactBlockSelector(blockId);
  }

  static BlockSelector tag(TagKey<net.minecraft.world.level.block.Block> tagKey) {
    return new TagBlockSelector(tagKey);
  }

  static BlockSelector fromJson(JsonObject json, String fieldName) {
    JsonObject selector = GsonHelper.getAsJsonObject(json, fieldName);

    if (selector.has("type")) {
      String type = GsonHelper.getAsString(selector, "type");
      if (!"any".equals(type)) {
        throw new JsonParseException(
            "Unsupported " + fieldName + " selector type '" + type + "', expected 'any'");
      }
      return any();
    }

    if (selector.has("block")) {
      return exact(ResourceLocation.parse(GsonHelper.getAsString(selector, "block")));
    }

    if (selector.has("tag")) {
      return tag(
          TagKey.create(
              Registries.BLOCK, ResourceLocation.parse(GsonHelper.getAsString(selector, "tag"))));
    }

    throw new JsonParseException(
        "Expected '" + fieldName + "' to define one of: 'type', 'block', or 'tag'");
  }

  enum AnyBlockSelector implements BlockSelector {
    INSTANCE;

    @Override
    public boolean matches(BlockState state) {
      return true;
    }

    @Override
    public int specificity() {
      return 0;
    }

    @Override
    public Set<ResourceLocation> resolveBlockIds(HolderLookup.Provider registryLookup) {
      LinkedHashSet<ResourceLocation> blockIds = new LinkedHashSet<>();
      var blockLookup = registryLookup.lookupOrThrow(Registries.BLOCK);
      blockLookup.listElementIds().forEach(key -> blockIds.add(key.location()));
      return blockIds;
    }
  }

  record ExactBlockSelector(ResourceLocation blockId) implements BlockSelector {
    @Override
    public boolean matches(BlockState state) {
      return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(blockId);
    }

    @Override
    public int specificity() {
      return 2;
    }

    @Override
    public Set<ResourceLocation> resolveBlockIds(HolderLookup.Provider registryLookup) {
      return Set.of(blockId);
    }
  }

  record TagBlockSelector(TagKey<net.minecraft.world.level.block.Block> tagKey)
      implements BlockSelector {
    @Override
    public boolean matches(BlockState state) {
      return state.is(tagKey);
    }

    @Override
    public int specificity() {
      return 1;
    }

    @Override
    public Set<ResourceLocation> resolveBlockIds(HolderLookup.Provider registryLookup) {
      LinkedHashSet<ResourceLocation> blockIds = new LinkedHashSet<>();
      var blockLookup = registryLookup.lookupOrThrow(Registries.BLOCK);
      blockLookup
          .get(tagKey)
          .ifPresent(
              holders ->
                  holders.stream()
                      .map(Holder::unwrapKey)
                      .flatMap(Optional::stream)
                      .forEach(key -> blockIds.add(key.location())));
      return blockIds;
    }
  }
}
