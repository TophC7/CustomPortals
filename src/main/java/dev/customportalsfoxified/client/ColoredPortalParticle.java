package dev.customportalsfoxified.client;

import dev.customportalsfoxified.particle.ColoredPortalParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import org.jetbrains.annotations.Nullable;

/**
 * Replicates vanilla PortalParticle behavior with configurable color. 
 * Movement uses eased interpolation, aka particles start offset from spawn
 * and settle back toward it over their lifetime.
 */
public class ColoredPortalParticle extends TextureSheetParticle {

  private final double xStart;
  private final double yStart;
  private final double zStart;

  protected ColoredPortalParticle(
      ClientLevel level,
      double x,
      double y,
      double z,
      double xSpeed,
      double ySpeed,
      double zSpeed,
      float r,
      float g,
      float b) {
    super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    this.xd = xSpeed;
    this.yd = ySpeed;
    this.zd = zSpeed;
    this.xStart = x;
    this.yStart = y;
    this.zStart = z;

    // vanilla portal sizing
    this.quadSize = 0.1F * (this.random.nextFloat() * 0.2F + 0.5F);
    // apply color with vanilla-style brightness variation
    float brightness = this.random.nextFloat() * 0.6F + 0.4F;
    this.rCol = r * brightness;
    this.gCol = g * brightness;
    this.bCol = b * brightness;
    // vanilla portal lifetime: 40-49 ticks
    this.lifetime = this.random.nextInt(10) + 40;
  }

  @Override
  public ParticleRenderType getRenderType() {
    return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
  }

  @Override
  public float getQuadSize(float scaleFactor) {
    // vanilla easing: grow to peak then shrink
    float progress = (this.age + scaleFactor) / this.lifetime;
    progress = 1.0F - progress;
    progress = 1.0F - progress * progress;
    return this.quadSize * progress;
  }

  @Override
  public int getLightColor(float partialTick) {
    int base = super.getLightColor(partialTick);
    // gradually increase brightness as particle ages
    float progress = (float) this.age / this.lifetime;
    progress = progress * progress * progress * progress;
    int blockLight = base & 0xFF;
    int skyLight = base >> 16 & 0xFF;
    skyLight += (int) (progress * 15.0F * 16.0F);
    if (skyLight > 240) skyLight = 240;
    return blockLight | skyLight << 16;
  }

  @Override
  public void tick() {
    this.xo = this.x;
    this.yo = this.y;
    this.zo = this.z;
    if (this.age++ >= this.lifetime) {
      this.remove();
      return;
    }
    // eased interpolation from start position + velocity
    float progress = (float) this.age / this.lifetime;
    float eased = -progress + progress * progress * 2.0F;
    float invEased = 1.0F - eased;
    this.x = this.xStart + this.xd * invEased;
    this.y = this.yStart + this.yd * invEased + (1.0F - progress);
    this.z = this.zStart + this.zd * invEased;
  }

  // PROVIDER //

  public static class Provider implements ParticleProvider<ColoredPortalParticleOptions> {
    private final SpriteSet sprites;

    public Provider(SpriteSet sprites) {
      this.sprites = sprites;
    }

    @Override
    public @Nullable Particle createParticle(
        ColoredPortalParticleOptions options,
        ClientLevel level,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed) {
      ColoredPortalParticle particle =
          new ColoredPortalParticle(
              level,
              x,
              y,
              z,
              xSpeed,
              ySpeed,
              zSpeed,
              options.color().x(),
              options.color().y(),
              options.color().z());
      particle.pickSprite(this.sprites);
      return particle;
    }
  }
}
