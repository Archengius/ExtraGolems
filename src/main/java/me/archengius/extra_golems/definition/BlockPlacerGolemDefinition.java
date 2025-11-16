package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemBlockPlacementBehavior;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class BlockPlacerGolemDefinition extends GolemDefinitionWithItemFilter {

    public BlockPlacerGolemDefinition() {
        super(Optional.of(Blocks.PURPUR_BLOCK),
                CopperGolemBlockPlacementBehavior.AnyBlock::new,
                CopperGolemBlockPlacementBehavior.MEMORY_TYPES,
                CopperGolemBlockPlacementBehavior.AnyBlock::isValidItemToHold,
                CopperGolemBlockPlacementBehavior.MAX_PLACED_BLOCK_STACK_SIZE);
    }
}
