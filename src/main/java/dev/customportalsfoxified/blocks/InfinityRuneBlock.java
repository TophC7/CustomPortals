package dev.customportalsfoxified.blocks;

import com.mojang.serialization.MapCodec;
import dev.customportalsfoxified.data.RuneType;

public class InfinityRuneBlock extends AbstractRuneBlock {

  public static final MapCodec<InfinityRuneBlock> CODEC = simpleCodec(p -> new InfinityRuneBlock());

  @Override
  protected MapCodec<? extends InfinityRuneBlock> codec() {
    return CODEC;
  }

  @Override
  public RuneType getRuneType() {
    return RuneType.INFINITY;
  }
}
