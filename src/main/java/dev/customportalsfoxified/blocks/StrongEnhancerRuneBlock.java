package dev.customportalsfoxified.blocks;

import com.mojang.serialization.MapCodec;
import dev.customportalsfoxified.data.RuneType;

public class StrongEnhancerRuneBlock extends AbstractRuneBlock {

  public static final MapCodec<StrongEnhancerRuneBlock> CODEC =
      simpleCodec(p -> new StrongEnhancerRuneBlock());

  @Override
  protected MapCodec<? extends StrongEnhancerRuneBlock> codec() {
    return CODEC;
  }

  @Override
  public RuneType getRuneType() {
    return RuneType.STRONG_ENHANCER;
  }
}
