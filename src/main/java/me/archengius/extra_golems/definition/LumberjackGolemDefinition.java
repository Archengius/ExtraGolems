package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemLumberjackBehavior;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

public class LumberjackGolemDefinition extends GolemDefinitionWithItemFilter {

    public LumberjackGolemDefinition() {
        super(itemStack -> Block.byItem(itemStack.getItem()).defaultBlockState().is(BlockTags.LOGS),
                CopperGolemLumberjackBehavior::new, CopperGolemLumberjackBehavior.MEMORY_TYPES, CopperGolemLumberjackBehavior::isValidItemToHold, CopperGolemLumberjackBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }
}
