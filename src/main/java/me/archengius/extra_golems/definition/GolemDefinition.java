package me.archengius.extra_golems.definition;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.ai.CopperGolemBaseBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public interface GolemDefinition {

    CopperGolemBaseBehavior createCoreBehavior();

    default List<MemoryModuleType<?>> getAdditionalMemoryTypes() {
        return ImmutableList.of();
    }

    default boolean shouldAnimalPanic() {
        return true;
    }

    default boolean isBehaviorItem(ItemStack itemStack) {
        return false;
    }

    default void initializeOnce(CopperGolem copperGolem) {
    }

    default void tick(CopperGolem copperGolem) {
    }

    default void dropEquipment(CopperGolem copperGolem, ServerLevel level) {
    }

    default InteractionResult mobInteract(CopperGolem copperGolem, Player player, InteractionHand interactionHand) {
        return InteractionResult.PASS;
    }
}
