package dev.customportalsfoxified.particle;

import dev.customportalsfoxified.CustomPortalsFoxified;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {

  public static final DeferredRegister<ParticleType<?>> PARTICLES =
      DeferredRegister.create(Registries.PARTICLE_TYPE, CustomPortalsFoxified.MOD_ID);

  public static final Supplier<ParticleType<ColoredPortalParticleOptions>> COLORED_PORTAL =
      PARTICLES.register(
          "colored_portal",
          () ->
              new ParticleType<>(false) {
                @Override
                public com.mojang.serialization.MapCodec<ColoredPortalParticleOptions> codec() {
                  return ColoredPortalParticleOptions.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<
                        ? super net.minecraft.network.RegistryFriendlyByteBuf,
                        ColoredPortalParticleOptions>
                    streamCodec() {
                  return ColoredPortalParticleOptions.STREAM_CODEC.cast();
                }
              });
}
