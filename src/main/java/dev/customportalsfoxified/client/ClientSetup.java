package dev.customportalsfoxified.client;

import dev.customportalsfoxified.CustomPortalsFoxified;
import dev.customportalsfoxified.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
    modid = CustomPortalsFoxified.MOD_ID,
    value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD)
public class ClientSetup {

  @SubscribeEvent
  public static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          // portal block is translucent
          ItemBlockRenderTypes.setRenderLayer(
              ModBlocks.CUSTOM_PORTAL.get(), RenderType.translucent());

          // rune blocks use cutout rendering
          ItemBlockRenderTypes.setRenderLayer(ModBlocks.HASTE_RUNE.get(), RenderType.cutout());
          ItemBlockRenderTypes.setRenderLayer(ModBlocks.GATE_RUNE.get(), RenderType.cutout());
          ItemBlockRenderTypes.setRenderLayer(ModBlocks.ENHANCER_RUNE.get(), RenderType.cutout());
          ItemBlockRenderTypes.setRenderLayer(
              ModBlocks.STRONG_ENHANCER_RUNE.get(), RenderType.cutout());
          ItemBlockRenderTypes.setRenderLayer(ModBlocks.INFINITY_RUNE.get(), RenderType.cutout());
        });
  }
}
