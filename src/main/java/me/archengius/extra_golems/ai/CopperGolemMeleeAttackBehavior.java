package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.ExtraGolemsMemoryModuleTypes;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class CopperGolemMeleeAttackBehavior extends CopperGolemBaseBehavior {
    private static final int MELEE_ATTACK_HORIZONTAL_SEARCH_RADIUS = 16;
    private static final int LAST_ATTACKED_ENTITY_MEMORY_TIME = 100;
    public static final int MAX_HELD_ITEM_STACK_SIZE = 1;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(ExtraGolemsMemoryModuleTypes.LAST_ATTACKED_TARGET);

    public CopperGolemMeleeAttackBehavior(float speedModifier, int horizontalSearchDistance, int verticalSearchDistance) {
        super(speedModifier, horizontalSearchDistance, verticalSearchDistance);
    }

    public static boolean isValidItemToHold(ItemStack itemStack) {
        return itemStack.is(ItemTags.SWORDS) || itemStack.is(ItemTags.AXES);
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack itemInHand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (itemInHand.isEmpty()) {
            Optional<BlockPos> closestContainerTarget = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(BlockTags.COPPER_CHESTS));
            return closestContainerTarget.map(blockPos -> new ContainerWeaponPickupInteractionTarget(this, level, blockPos));
        }

        Optional<LivingEntity> closestAttackTarget = getClosestAttackTarget(level, mob);
        return closestAttackTarget.map(attackTarget -> new MeleeAttackEntityInteractionTarget(this, attackTarget));
    }

    private Optional<LivingEntity> getClosestAttackTarget(ServerLevel level, PathfinderMob mob) {
        AABB targetBoundingBox = getTargetSearchArea(mob);
        Set<UUID> visitedEntities = getVisitedEntities(mob);
        Set<UUID> unreachableEntities = getUnreachableEntities(mob);
        List<? extends LivingEntity> nearbyLivingEntities = level.getEntitiesOfClass(LivingEntity.class, targetBoundingBox);

        // Try attacking the same entity we have attacked before first
        Optional<LivingEntity> lastAttackedTarget = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.LAST_ATTACKED_TARGET);
        if (lastAttackedTarget.isPresent() && lastAttackedTarget.get().isAlive() && nearbyLivingEntities.contains(lastAttackedTarget.get())) {
            if (!visitedEntities.contains(lastAttackedTarget.get().getUUID()) && !unreachableEntities.contains(lastAttackedTarget.get().getUUID()) &&
                isEntityValidAttackTarget(level, mob, lastAttackedTarget.get())) {
                return lastAttackedTarget;
            }
        }

        // Fallback to attacking the entity closest to us otherwise
        double closestAttackTargetDistanceSq = Double.MAX_VALUE;
        Optional<LivingEntity> closestAttackTarget = Optional.empty();

        for (LivingEntity livingEntity : nearbyLivingEntities) {
            if (!visitedEntities.contains(livingEntity.getUUID()) && !unreachableEntities.contains(livingEntity.getUUID()) &&
                    isEntityValidAttackTarget(level, mob, livingEntity) && livingEntity.distanceToSqr(mob) < closestAttackTargetDistanceSq) {
                closestAttackTarget = Optional.of(livingEntity);
                closestAttackTargetDistanceSq = livingEntity.distanceToSqr(mob);
            }
        }
        return closestAttackTarget;
    }

    protected abstract boolean isEntityValidAttackTarget(Level level, PathfinderMob mob, LivingEntity livingEntity);

    private static class ContainerWeaponPickupInteractionTarget extends ContainerPickupInteractionTarget {
        private static final int WEAPON_PICKUP_INTERACTION_TIME = 100;

        public ContainerWeaponPickupInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = MAX_HELD_ITEM_STACK_SIZE;
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return WEAPON_PICKUP_INTERACTION_TIME;
        }

        @Override
        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return isValidItemToHold(itemStack);
        }
    }

    private static class MeleeAttackEntityInteractionTarget extends BaseEntityInteractionTarget {
        private static final int MELEE_ATTACK_ENTITY_INTERACTION_TIME = 40;
        private static final int SWING_ANIMATION_TICK_NUMBER = 1;
        private static final int ATTACK_ENTITY_TICK_NUMBER = 10;

        private boolean attackedEntitySuccessfully = false;

        public MeleeAttackEntityInteractionTarget(CopperGolemBaseBehavior owner, Entity entity) {
            super(owner, entity);
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return MELEE_ATTACK_ENTITY_INTERACTION_TIME;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            return super.isTargetStillAvailableAndRelevant(level, mob) &&
                this.entity instanceof LivingEntity livingEntity && !livingEntity.isInvisible();
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            if (ticksSinceInteractionStart == SWING_ANIMATION_TICK_NUMBER) {
                if (mob instanceof CopperGolem copperGolem) {
                    copperGolem.setState(CopperGolemState.GETTING_ITEM);
                }
            }

            if (ticksSinceInteractionStart == ATTACK_ENTITY_TICK_NUMBER) {
                this.attackedEntitySuccessfully = false;
                if (this.entity instanceof LivingEntity livingEntity && !livingEntity.isInvulnerable() && !livingEntity.isInvisible()) {
                    mob.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);

                    livingEntity.setLastHurtByPlayer(FakePlayer.get((ServerLevel) level), 1);
                    this.attackedEntitySuccessfully = mob.doHurtTarget((ServerLevel) level, livingEntity);

                    ItemStack weaponItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
                    if (this.attackedEntitySuccessfully && !weaponItemStack.isEmpty() && weaponItemStack.has(DataComponents.WEAPON)) {
                        weaponItemStack.postHurtEnemy(livingEntity, mob);
                    }
                }
            }
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            if (this.attackedEntitySuccessfully) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
                if (this.entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                    mob.getBrain().setMemoryWithExpiry(ExtraGolemsMemoryModuleTypes.LAST_ATTACKED_TARGET, livingEntity, LAST_ATTACKED_ENTITY_MEMORY_TIME);
                }
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }

    public static final class AdultAnimalAttackBehavior extends CopperGolemMeleeAttackBehavior {
        private static final float ENEMY_MOB_ATTACK_SPEED_MULTIPLIER = 1.0f;

        public AdultAnimalAttackBehavior() {
            super(ENEMY_MOB_ATTACK_SPEED_MULTIPLIER, MELEE_ATTACK_HORIZONTAL_SEARCH_RADIUS, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS);
        }

        @Override
        protected boolean isEntityValidAttackTarget(Level level, PathfinderMob mob, LivingEntity livingEntity) {
            return livingEntity instanceof Animal animal && !animal.isBaby();
        }
    }

    public static final class EnemyMobAttackBehavior extends CopperGolemMeleeAttackBehavior {
        private static final float ENEMY_MOB_ATTACK_SPEED_MULTIPLIER = 1.0f;

        public EnemyMobAttackBehavior() {
            super(ENEMY_MOB_ATTACK_SPEED_MULTIPLIER, MELEE_ATTACK_HORIZONTAL_SEARCH_RADIUS, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS);
        }

        @Override
        protected boolean isEntityValidAttackTarget(Level level, PathfinderMob mob, LivingEntity livingEntity) {
            return livingEntity instanceof Enemy;
        }
    }
}
