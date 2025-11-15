package me.archengius.extra_golems.definition;

import me.archengius.extra_golems.ExtraGolemsMemoryModuleTypes;
import me.archengius.extra_golems.ai.CopperGolemExperienceCollectorBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class ExperienceCollectorGolemDefinition extends SimpleGolemDefinition {

    public ExperienceCollectorGolemDefinition() {
        super(Optional.of(Blocks.BOOKSHELF), CopperGolemExperienceCollectorBehavior::new, CopperGolemExperienceCollectorBehavior.MEMORY_TYPES);
    }

    @Override
    public InteractionResult mobInteract(CopperGolem copperGolem, Player player, InteractionHand interactionHand) {
        if (interactionHand == InteractionHand.MAIN_HAND && player.getItemInHand(interactionHand).isEmpty()) {
            int accumulatedExperience = copperGolem.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.ACCUMULATED_EXPERIENCE).orElse(0);

            if (accumulatedExperience > 0) {
                ExperienceOrb.award((ServerLevel) copperGolem.level(), player.position(), accumulatedExperience);
                copperGolem.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.ACCUMULATED_EXPERIENCE, Optional.empty());
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.mobInteract(copperGolem, player, interactionHand);
    }

    @Override
    public void dropEquipment(CopperGolem copperGolem, ServerLevel level) {
        super.dropEquipment(copperGolem, level);

        int accumulatedExperience = copperGolem.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.ACCUMULATED_EXPERIENCE).orElse(0);
        if (accumulatedExperience > 0) {
            ExperienceOrb.award(level, copperGolem.position(), accumulatedExperience);
        }
    }
}
