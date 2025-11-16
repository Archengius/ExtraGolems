package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemBlockBreakBehavior;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class BlockBreakerGolemDefinition extends GolemDefinitionWithItemFilter {

    public BlockBreakerGolemDefinition() {
        super(Optional.of(Blocks.PURPUR_PILLAR), CopperGolemBlockBreakBehavior.CopperBlockMarkedBlock::new, CopperGolemBlockBreakBehavior.MEMORY_TYPES,
                CopperGolemBlockBreakBehavior::isValidItemToHold, CopperGolemBlockBreakBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }
}
