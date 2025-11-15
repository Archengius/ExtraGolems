package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ai.CopperGolemAnimalBreedBehavior;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class AnimalBreederGolemDefinition extends GolemDefinitionWithItemFilter {

    public AnimalBreederGolemDefinition() {
        super(Optional.of(Blocks.HAY_BLOCK), CopperGolemAnimalBreedBehavior::new, CopperGolemAnimalBreedBehavior.MEMORY_TYPES,
                CopperGolemAnimalBreedBehavior::isValidItemToHold, CopperGolemAnimalBreedBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }
}
