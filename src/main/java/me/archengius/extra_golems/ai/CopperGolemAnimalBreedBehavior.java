package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.mixin.AnimalAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class
CopperGolemAnimalBreedBehavior extends CopperGolemBaseBehavior {
    private static final double BREEDING_PARTNER_SEARCH_RANGE = 8.0f;
    private static final int PREFERRED_BREEDING_PARTNER_MEMORY_TIME = 500;
    private static final TargetingConditions BREEDING_PARTNER_TARGETING = TargetingConditions.forNonCombat().range(BREEDING_PARTNER_SEARCH_RANGE).ignoreLineOfSight();
    private static final TargetingConditions ANIMAL_INTERACTION_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
    public static final int MAX_HELD_ITEM_STACK_SIZE = 16;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(ExtraGolemsMemoryModuleTypes.PREFERRED_BREED_PARTNER);

    public CopperGolemAnimalBreedBehavior() {
        super(ExtraGolemsCopperGolemAi.SPEED_MULTIPLIER_WHEN_IDLING, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_HORIZONTAL_SEARCH_RADIUS, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS);
    }

    public static boolean isValidItemToHold(ItemStack itemStack) {
        return itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.location().getPath().endsWith("_food"));
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack itemInHand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (itemInHand.isEmpty() || (itemInHand.isStackable() && itemInHand.getCount() == 1)) {
            Optional<BlockPos> closestContainerTarget = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(BlockTags.COPPER_CHESTS));
            return closestContainerTarget.map(blockPos -> new ContainerFoodPickupInteractionTarget(this, level, blockPos));
        }

        Optional<Animal> closestAnimalTarget = findClosestAnimalInteractionTarget(level, mob, itemInHand);
        return closestAnimalTarget.map(animal -> new GiveFoodToAnimalInteractionTarget(this, animal));
    }

    private static boolean canAnimalPotentiallyMate(Animal animal) {
        return !animal.isInLove() && animal.canFallInLove() && animal.getAge() == 0 && !animal.isPanicking();
    }

    private static boolean hasPotentialBreedingPartner(ServerLevel level, Animal animal) {
        List<? extends Animal> nearbyAnimals = level.getNearbyEntities(animal.getClass(), BREEDING_PARTNER_TARGETING, animal, animal.getBoundingBox().inflate(BREEDING_PARTNER_SEARCH_RANGE));
        for (Animal nearbyAnimal : nearbyAnimals) {
            if ((nearbyAnimal.isInLove() || canAnimalPotentiallyMate(nearbyAnimal)) && !nearbyAnimal.isPanicking()) {
                Path pathToNearbyAnimal = animal.getNavigation().createPath(nearbyAnimal, 0);
                if (pathToNearbyAnimal != null && pathToNearbyAnimal.canReach()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<Animal> findClosestAnimalInteractionTarget(ServerLevel level, PathfinderMob mob, ItemStack itemInHand) {
        AABB interactionBoundingBox = getTargetSearchArea(mob);
        Set<UUID> visitedEntities = getVisitedEntities(mob);
        Set<UUID> unreachableEntities = getUnreachableEntities(mob);
        List<? extends Animal> nearbyAnimals = level.getNearbyEntities(Animal.class, ANIMAL_INTERACTION_TARGETING, mob, interactionBoundingBox);

        // If we have just interacted with another animal in range, prefer an animal that is closest to that animal
        Optional<Animal> preferredBreedingPartner = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.PREFERRED_BREED_PARTNER);
        if (preferredBreedingPartner.isPresent() && preferredBreedingPartner.get().isAlive() && nearbyAnimals.contains(preferredBreedingPartner.get()) &&
                preferredBreedingPartner.get().isFood(itemInHand) && preferredBreedingPartner.get().isInLove()) {
            double closestPartnerAnimalDistanceSq = Double.MAX_VALUE;
            Optional<Animal> resultPartnerAnimal = Optional.empty();

            for (Animal nearbyAnimal : nearbyAnimals) {
                if (!visitedEntities.contains(nearbyAnimal.getUUID()) && !unreachableEntities.contains(nearbyAnimal.getUUID())) {
                    if (canAnimalPotentiallyMate(nearbyAnimal) && nearbyAnimal.isFood(itemInHand) && !nearbyAnimal.isPanicking() &&
                            nearbyAnimal.getClass() == preferredBreedingPartner.get().getClass() && nearbyAnimal != preferredBreedingPartner.get()) {
                        double distanceToAnimalSq = preferredBreedingPartner.get().distanceToSqr(nearbyAnimal);
                        if (distanceToAnimalSq <= BREEDING_PARTNER_SEARCH_RANGE * BREEDING_PARTNER_SEARCH_RANGE && distanceToAnimalSq < closestPartnerAnimalDistanceSq) {

                            // We want this animal to also be reachable from the breeding partner to be considered valid
                            Path pathToNearbyAnimal = preferredBreedingPartner.get().getNavigation().createPath(nearbyAnimal, 0);
                            if (pathToNearbyAnimal != null && pathToNearbyAnimal.canReach()) {
                                resultPartnerAnimal = Optional.of(nearbyAnimal);
                                closestPartnerAnimalDistanceSq = distanceToAnimalSq;
                            }
                        }
                    }
                }
            }
            if (resultPartnerAnimal.isPresent()) {
                return resultPartnerAnimal;
            }
        }

        // We have not previously interacted with an animal in range, or there is no valid breeding partner for the previously bred animal, so just check the animal closest to us instead
        // In that case we also do not have to check if the animal can be reached from us with pathfinding, since this will be discovered later
        double closestNearbyAnimalDistanceSq = Double.MAX_VALUE;
        Optional<Animal> closestNearbyAnimal = Optional.empty();

        for (Animal nearbyAnimal : nearbyAnimals) {
            if (!visitedEntities.contains(nearbyAnimal.getUUID()) && !unreachableEntities.contains(nearbyAnimal.getUUID())) {
                if (canAnimalPotentiallyMate(nearbyAnimal) && nearbyAnimal.isFood(itemInHand) && !nearbyAnimal.isPanicking() && mob.distanceToSqr(nearbyAnimal) < closestNearbyAnimalDistanceSq) {
                    closestNearbyAnimal = Optional.of(nearbyAnimal);
                    closestNearbyAnimalDistanceSq = mob.distanceToSqr(nearbyAnimal);
                }
            }
        }
        return closestNearbyAnimal;
    }

    private static class ContainerFoodPickupInteractionTarget extends ContainerPickupInteractionTarget {
        private static final int FOOD_PICKUP_INTERACTION_TIME = 40;

        public ContainerFoodPickupInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = MAX_HELD_ITEM_STACK_SIZE;
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return FOOD_PICKUP_INTERACTION_TIME;
        }

        @Override
        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return isValidItemToHold(itemStack);
        }
    }

    private static class GiveFoodToAnimalInteractionTarget extends EntityInteractionTarget {

        public GiveFoodToAnimalInteractionTarget(CopperGolemBaseBehavior owner, Entity entity) {
            super(owner, entity);
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack itemInHand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) && this.entity instanceof Animal animal &&
                    !itemInHand.isEmpty() && animal.isFood(itemInHand);
        }

        @Override
        protected boolean checkEntityBehavior(Level level, PathfinderMob mob, Entity entity) {
            if (super.checkEntityBehavior(level, mob, entity) && entity instanceof Animal animal &&
                    CopperGolemAnimalBreedBehavior.canAnimalPotentiallyMate(animal) &&
                    CopperGolemAnimalBreedBehavior.hasPotentialBreedingPartner((ServerLevel) level, animal)) {
                this.interactionGolemState = CopperGolemState.DROPPING_NO_ITEM;
                this.interactionSoundEvent = SoundEvents.COPPER_GOLEM_ITEM_NO_DROP;
                return true;
            }
            this.interactionGolemState = CopperGolemState.DROPPING_NO_ITEM;
            this.interactionSoundEvent = SoundEvents.COPPER_GOLEM_ITEM_NO_DROP;
            return false;
        }

        @Override
        protected boolean performEntityBehavior(Level level, PathfinderMob mob, Entity entity) {
            ItemStack itemInHand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            if (entity instanceof Animal animal &&
                    CopperGolemAnimalBreedBehavior.canAnimalPotentiallyMate(animal) &&
                    CopperGolemAnimalBreedBehavior.hasPotentialBreedingPartner((ServerLevel) level, animal) &&
                    !itemInHand.isEmpty() && animal.isFood(itemInHand)) {

                itemInHand.shrink(1);
                animal.setInLove(null);
                ((AnimalAccessor) animal).extra_golems$playEatingSound();
                mob.getBrain().setMemoryWithExpiry(ExtraGolemsMemoryModuleTypes.PREFERRED_BREED_PARTNER, animal, PREFERRED_BREEDING_PARTNER_MEMORY_TIME);
                return true;
            }
            return false;
        }
    }
}
