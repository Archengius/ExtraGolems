package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemBlockPlacementBehavior;
import net.minecraft.tags.ItemTags;

public class PlanterGolemDefinition extends GolemDefinitionWithItemFilter {

    public PlanterGolemDefinition() {
        super(itemStack -> itemStack.is(ItemTags.SAPLINGS),
                CopperGolemBlockPlacementBehavior.VegetationBlock::new,
                CopperGolemBlockPlacementBehavior.MEMORY_TYPES,
                CopperGolemBlockPlacementBehavior.VegetationBlock::isValidItemToHold,
                CopperGolemBlockPlacementBehavior.MAX_PLACED_BLOCK_STACK_SIZE);
    }
}
