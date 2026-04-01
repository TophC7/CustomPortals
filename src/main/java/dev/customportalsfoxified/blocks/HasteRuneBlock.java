package dev.customportalsfoxified.blocks;

import com.mojang.serialization.MapCodec;
import dev.customportalsfoxified.data.RuneType;

public class HasteRuneBlock extends AbstractRuneBlock {

    public static final MapCodec<HasteRuneBlock> CODEC = simpleCodec(p -> new HasteRuneBlock());

    @Override
    protected MapCodec<? extends HasteRuneBlock> codec() {
        return CODEC;
    }

    @Override
    public RuneType getRuneType() {
        return RuneType.HASTE;
    }
}
