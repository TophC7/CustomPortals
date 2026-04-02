package dev.customportalsfoxified;

import dev.customportalsfoxified.blocks.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(Registries.BLOCK, CustomPortalsFoxified.MOD_ID);

  // PORTAL //

  public static final Supplier<CustomPortalBlock> CUSTOM_PORTAL =
      BLOCKS.register("custom_portal", CustomPortalBlock::new);

  // RUNES //

  public static final Supplier<HasteRuneBlock> HASTE_RUNE =
      BLOCKS.register("haste_rune", HasteRuneBlock::new);

  public static final Supplier<GateRuneBlock> GATE_RUNE =
      BLOCKS.register("gate_rune", GateRuneBlock::new);

  public static final Supplier<EnhancerRuneBlock> ENHANCER_RUNE =
      BLOCKS.register("enhancer_rune", EnhancerRuneBlock::new);

  public static final Supplier<StrongEnhancerRuneBlock> STRONG_ENHANCER_RUNE =
      BLOCKS.register("strong_enhancer_rune", StrongEnhancerRuneBlock::new);

  public static final Supplier<InfinityRuneBlock> INFINITY_RUNE =
      BLOCKS.register("infinity_rune", InfinityRuneBlock::new);
}
