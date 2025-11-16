package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.mixin.ExperienceOrbAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CopperGolemExperienceCollectorBehavior extends CopperGolemBaseBehavior {
    private static final float EXPERIENCE_COLLECTOR_SPEED_MODIFIER = 2.0f;
    private static final int EXPERIENCE_COLLECTOR_HORIZONTAL_SEARCH_RADIUS = 32;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(ExtraGolemsMemoryModuleTypes.ACCUMULATED_EXPERIENCE);

    public CopperGolemExperienceCollectorBehavior() {
        super(EXPERIENCE_COLLECTOR_SPEED_MODIFIER, EXPERIENCE_COLLECTOR_HORIZONTAL_SEARCH_RADIUS, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS);
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        Optional<ExperienceOrb> closestExperienceOrb = findClosestExperienceOrb(level, mob);
        return closestExperienceOrb.map(experienceOrb -> new CollectExperienceInteractionTarget(this, experienceOrb));
    }

    private Optional<ExperienceOrb> findClosestExperienceOrb(ServerLevel level, PathfinderMob mob) {
        AABB targetBoundingBox = getTargetSearchArea(mob);
        Set<UUID> visitedEntities = getVisitedEntities(mob);
        Set<UUID> unreachableEntities = getUnreachableEntities(mob);
        List<? extends ExperienceOrb> nearbyExperienceOrbs = level.getEntitiesOfClass(ExperienceOrb.class, targetBoundingBox);

        double closestExperienceOrbDistanceSq = Double.MAX_VALUE;
        Optional<ExperienceOrb> closestExperienceOrb = Optional.empty();

        for (ExperienceOrb experienceOrb : nearbyExperienceOrbs) {
            if (!visitedEntities.contains(experienceOrb.getUUID()) && !unreachableEntities.contains(experienceOrb.getUUID()) && experienceOrb.distanceToSqr(mob) < closestExperienceOrbDistanceSq) {
                closestExperienceOrb = Optional.of(experienceOrb);
                closestExperienceOrbDistanceSq = experienceOrb.distanceToSqr(mob);
            }
        }
        return closestExperienceOrb;
    }

    private static class CollectExperienceInteractionTarget extends BaseEntityInteractionTarget {
        private static final int COLLECT_EXPERIENCE_INTERACTION_TIME = 1;
        private static final double COLLECT_EXPERIENCE_HORIZONTAL_RANGE = 0.5f;
        private static final double COLLECT_EXPERIENCE_VERTICAL_RANGE = 0.2f;

        public CollectExperienceInteractionTarget(CopperGolemBaseBehavior owner, Entity entity) {
            super(owner, entity);
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return COLLECT_EXPERIENCE_INTERACTION_TIME;
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            AABB pickupBoundingBox = mob.getBoundingBox().inflate(COLLECT_EXPERIENCE_HORIZONTAL_RANGE, COLLECT_EXPERIENCE_VERTICAL_RANGE, COLLECT_EXPERIENCE_HORIZONTAL_RANGE);
            List<? extends ExperienceOrb> experienceOrbsToCollect = level.getEntitiesOfClass(ExperienceOrb.class, pickupBoundingBox);

            int currentStoredExperience = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.ACCUMULATED_EXPERIENCE).orElse(0);
            for (ExperienceOrb experienceOrb : experienceOrbsToCollect) {
                currentStoredExperience += ((ExperienceOrbAccessor) experienceOrb).extra_golems$getCount();
                experienceOrb.discard();
            }

            mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.ACCUMULATED_EXPERIENCE, currentStoredExperience);
            if (!experienceOrbsToCollect.isEmpty()) {
                mob.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.1f, 0.9f + (mob.getRandom().nextFloat() - mob.getRandom().nextFloat()) * 0.35f);
            }

            // We want to clear the memories if we have collected the experience orb we were targeting, or ignore it otherwise
            if (this.entity instanceof ExperienceOrb targetExperienceOrb && experienceOrbsToCollect.contains(targetExperienceOrb)) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }
}
