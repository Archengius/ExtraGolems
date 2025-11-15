package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ExtraGolemsMemoryModuleTypes;
import me.archengius.extra_golems.ai.CopperGolemItemCollectorBehavior;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class ItemCollectorGolemDefinition extends GolemDefinitionWithItemFilter {

    public ItemCollectorGolemDefinition() {
        super(Optional.of(Blocks.AMETHYST_BLOCK), CopperGolemItemCollectorBehavior::new, CopperGolemItemCollectorBehavior.MEMORY_TYPES,
                CopperGolemItemCollectorBehavior::isValidItemToHold, CopperGolemItemCollectorBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }

    @Override
    protected void notifyPlayerGaveItemToGolem(CopperGolem copperGolem, Player player, ItemStack itemStack) {
        super.notifyPlayerGaveItemToGolem(copperGolem, player, itemStack);
        copperGolem.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.PICKED_UP_WILDCARD_ITEM, Optional.empty());
    }
}
