package dev.customportalsfoxified;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {

  public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CustomPortalsFoxified.MOD_ID);

  // portal color for the entity currently inside a custom portal (-1 = none)
  public static final Supplier<AttachmentType<Integer>> PORTAL_COLOR =
      ATTACHMENTS.register(
          "portal_color", () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).build());
}
