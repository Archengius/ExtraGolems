package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemMeleeAttackBehavior;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class ButcherGolemDefinition extends GolemDefinitionWithItemFilter {

    public ButcherGolemDefinition() {
        super(Optional.of(Blocks.IRON_BLOCK), CopperGolemMeleeAttackBehavior.AdultAnimalAttackBehavior::new, CopperGolemMeleeAttackBehavior.MEMORY_TYPES,
                CopperGolemMeleeAttackBehavior::isValidItemToHold, CopperGolemMeleeAttackBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }
}
