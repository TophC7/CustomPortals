package dev.customportalsfoxified.blocks;

import com.mojang.serialization.MapCodec;
import dev.customportalsfoxified.data.RuneType;

public class EnhancerRuneBlock extends AbstractRuneBlock {

    public static final MapCodec<EnhancerRuneBlock> CODEC = simpleCodec(p -> new EnhancerRuneBlock());

    @Override
    protected MapCodec<? extends EnhancerRuneBlock> codec() {
        return CODEC;
    }

    @Override
    public RuneType getRuneType() {
        return RuneType.WEAK_ENHANCER;
    }
}
