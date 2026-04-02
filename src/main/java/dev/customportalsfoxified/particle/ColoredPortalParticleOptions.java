package dev.customportalsfoxified.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

public record ColoredPortalParticleOptions(Vector3f color) implements ParticleOptions {

  public static final MapCodec<ColoredPortalParticleOptions> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      Codec.FLOAT.fieldOf("r").forGetter(o -> o.color.x()),
                      Codec.FLOAT.fieldOf("g").forGetter(o -> o.color.y()),
                      Codec.FLOAT.fieldOf("b").forGetter(o -> o.color.z()))
                  .apply(
                      instance,
                      (r, g, b) -> new ColoredPortalParticleOptions(new Vector3f(r, g, b))));

  public static final StreamCodec<ByteBuf, ColoredPortalParticleOptions> STREAM_CODEC =
      StreamCodec.composite(
          ByteBufCodecs.FLOAT,
          o -> o.color.x(),
          ByteBufCodecs.FLOAT,
          o -> o.color.y(),
          ByteBufCodecs.FLOAT,
          o -> o.color.z(),
          (r, g, b) -> new ColoredPortalParticleOptions(new Vector3f(r, g, b)));

  @Override
  public ParticleType<?> getType() {
    return ModParticles.COLORED_PORTAL.get();
  }
}
