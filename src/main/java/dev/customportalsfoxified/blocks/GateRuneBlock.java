package dev.customportalsfoxified.blocks;

import com.mojang.serialization.MapCodec;
import dev.customportalsfoxified.data.RuneType;

public class GateRuneBlock extends AbstractRuneBlock {

    public static final MapCodec<GateRuneBlock> CODEC = simpleCodec(p -> new GateRuneBlock());

    @Override
    protected MapCodec<? extends GateRuneBlock> codec() {
        return CODEC;
    }

    @Override
    public RuneType getRuneType() {
        return RuneType.GATE;
    }
}
