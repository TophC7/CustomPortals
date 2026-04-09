package dev.customportalsfoxified.portal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface ItemSelector {

  boolean matches(ItemStack stack);

  int specificity();

  static ItemSelector exact(ResourceLocation itemId) {
    return new ExactItemSelector(itemId);
  }

  static ItemSelector tag(TagKey<Item> tagKey) {
    return new TagItemSelector(tagKey);
  }

  static ItemSelector fromJson(JsonObject json, String fieldName) {
    JsonObject selector = GsonHelper.getAsJsonObject(json, fieldName);

    if (selector.has("item")) {
      return exact(ResourceLocation.parse(GsonHelper.getAsString(selector, "item")));
    }

    if (selector.has("tag")) {
      return tag(
          TagKey.create(
              Registries.ITEM, ResourceLocation.parse(GsonHelper.getAsString(selector, "tag"))));
    }

    throw new JsonParseException(
        "Expected '" + fieldName + "' to define one of: 'item' or 'tag'");
  }

  record ExactItemSelector(ResourceLocation itemId) implements ItemSelector {
    @Override
    public boolean matches(ItemStack stack) {
      return !stack.isEmpty()
          && net.minecraft.core.registries.BuiltInRegistries.ITEM
              .getKey(stack.getItem())
              .equals(itemId);
    }

    @Override
    public int specificity() {
      return 2;
    }
  }

  record TagItemSelector(TagKey<Item> tagKey) implements ItemSelector {
    @Override
    public boolean matches(ItemStack stack) {
      return !stack.isEmpty() && stack.is(tagKey);
    }

    @Override
    public int specificity() {
      return 1;
    }
  }
}
