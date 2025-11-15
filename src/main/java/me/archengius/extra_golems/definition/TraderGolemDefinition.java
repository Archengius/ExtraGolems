package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ExtraGolemsMemoryModuleTypes;
import me.archengius.extra_golems.ai.CopperGolemTraderBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class TraderGolemDefinition extends GolemDefinitionWithItemFilter {

    public TraderGolemDefinition() {
        super(Optional.of(Blocks.EMERALD_BLOCK), CopperGolemTraderBehavior::new, CopperGolemTraderBehavior.MEMORY_TYPES,
                CopperGolemTraderBehavior::isValidItemToHold, CopperGolemTraderBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }

    @Override
    public InteractionResult mobInteract(CopperGolem copperGolem, Player player, InteractionHand interactionHand) {
        if (interactionHand == InteractionHand.MAIN_HAND && player.getItemInHand(interactionHand).isEmpty()) {
            ItemStack tradeResultStack = copperGolem.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT).orElse(ItemStack.EMPTY);
            if (!tradeResultStack.isEmpty()) {
                BehaviorUtils.throwItem(copperGolem, tradeResultStack, player.position());
                copperGolem.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT, Optional.empty());
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.mobInteract(copperGolem, player, interactionHand);
    }

    @Override
    public void dropEquipment(CopperGolem copperGolem, ServerLevel level) {
        super.dropEquipment(copperGolem, level);

        ItemStack tradeResultStack = copperGolem.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT).orElse(ItemStack.EMPTY);
        if (!tradeResultStack.isEmpty()) {
            copperGolem.spawnAtLocation(level, tradeResultStack);
        }
    }
}
