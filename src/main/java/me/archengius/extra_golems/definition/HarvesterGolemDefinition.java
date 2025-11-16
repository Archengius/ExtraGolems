package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemBlockBreakBehavior;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class HarvesterGolemDefinition extends GolemDefinitionWithItemFilter {

    public HarvesterGolemDefinition() {
        super(Optional.of(Blocks.GOLD_BLOCK), CopperGolemBlockBreakBehavior.GrownVegetationBlock::new, CopperGolemBlockBreakBehavior.MEMORY_TYPES,
                CopperGolemBlockBreakBehavior::isValidItemToHold, CopperGolemBlockBreakBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }
}
