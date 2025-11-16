package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CopperGolemItemCollectorBehavior extends CopperGolemBaseBehavior {
    private static final float ITEM_COLLECTOR_SPEED_MODIFIER = 2.0f;
    private static final int ITEM_COLLECTOR_HORIZONTAL_SEARCH_RADIUS = 32;
    public static final int MAX_HELD_ITEM_STACK_SIZE = 16;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(ExtraGolemsMemoryModuleTypes.PICKED_UP_WILDCARD_ITEM);

    public CopperGolemItemCollectorBehavior() {
        super(ITEM_COLLECTOR_SPEED_MODIFIER, ITEM_COLLECTOR_HORIZONTAL_SEARCH_RADIUS, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS);
    }

    public static boolean isValidItemToHold(ItemStack itemStack) {
        return true;
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack currentItemInHand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        boolean shouldRetainSingleItem = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.PICKED_UP_WILDCARD_ITEM).isEmpty();
        if (!currentItemInHand.isEmpty() && (!currentItemInHand.isStackable() || currentItemInHand.getCount() >= MAX_HELD_ITEM_STACK_SIZE)) {
            Optional<BlockPos> closestContainerTarget = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(Blocks.CHEST));
            return closestContainerTarget.map(blockPos -> new DropItemToContainerInteractionTarget(this, level, blockPos, shouldRetainSingleItem));
        }

        Optional<ItemEntity> closestItemEntity = findClosestMatchingItemEntity(level, mob, currentItemInHand);
        if (closestItemEntity.isPresent()) {
            return Optional.of(new CollectItemEntityInteractionTarget(this, closestItemEntity.get(), MAX_HELD_ITEM_STACK_SIZE));
        }

        if (!currentItemInHand.isEmpty() && !(currentItemInHand.isStackable() && currentItemInHand.getCount() == 1)) {
            Optional<BlockPos> closestContainerTarget = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(Blocks.CHEST));
            return closestContainerTarget.map(blockPos -> new DropItemToContainerInteractionTarget(this, level, blockPos, shouldRetainSingleItem));
        }
        return Optional.empty();
    }

    private Optional<ItemEntity> findClosestMatchingItemEntity(ServerLevel level, PathfinderMob mob, ItemStack itemStack) {
        AABB targetBoundingBox = getTargetSearchArea(mob);
        Set<UUID> visitedEntities = getVisitedEntities(mob);
        Set<UUID> unreachableEntities = getUnreachableEntities(mob);
        List<? extends ItemEntity> nearbyItemEntities = level.getEntitiesOfClass(ItemEntity.class, targetBoundingBox);

        double closestItemEntityDistanceSq = Double.MAX_VALUE;
        Optional<ItemEntity> closestItemEntity = Optional.empty();

        for (ItemEntity itemEntity : nearbyItemEntities) {
            if (!visitedEntities.contains(itemEntity.getUUID()) && !unreachableEntities.contains(itemEntity.getUUID()) && itemEntity.distanceToSqr(mob) < closestItemEntityDistanceSq &&
                    (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemEntity.getItem())) && isValidItemToHold(itemEntity.getItem())) {
                closestItemEntity = Optional.of(itemEntity);
                closestItemEntityDistanceSq = itemEntity.distanceToSqr(mob);
            }
        }
        return closestItemEntity;
    }

    private static class DropItemToContainerInteractionTarget extends ContainerDropInteractionTarget {

        public DropItemToContainerInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos, boolean retainSingleItem) {
            super(owner, level, blockPos);
            this.retainSingleItem = retainSingleItem;
        }

        @Override
        protected boolean shouldDropItemIntoContainer(Level level, PathfinderMob mob, Container container, ItemStack itemStack) {
            return containerContainsSameItemType(container, itemStack);
        }
    }

    private static class CollectItemEntityInteractionTarget extends BaseEntityInteractionTarget {
        private static final int COLLECT_ITEM_INTERACTION_TIME = 2;
        private static final double COLLECT_ITEM_HORIZONTAL_RANGE = 0.5f;
        private static final double COLLECT_ITEM_VERTICAL_RANGE = 0.2f;
        protected final int maxItemsToCollectAtOnce;

        public CollectItemEntityInteractionTarget(CopperGolemBaseBehavior owner, Entity entity, int maxItemsToCollectAtOnce) {
            super(owner, entity);
            this.maxItemsToCollectAtOnce = maxItemsToCollectAtOnce;
        }

        private int getMaxItemsToCollectAtOnce(ItemStack itemStack) {
            return !itemStack.isEmpty() ? Math.min(this.maxItemsToCollectAtOnce, itemStack.getMaxStackSize()) : this.maxItemsToCollectAtOnce;
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return COLLECT_ITEM_INTERACTION_TIME;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack currentItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) &&
                    (currentItemStack.isEmpty() || currentItemStack.getCount() < getMaxItemsToCollectAtOnce(currentItemStack));
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            AABB pickupBoundingBox = mob.getBoundingBox().inflate(COLLECT_ITEM_HORIZONTAL_RANGE, COLLECT_ITEM_VERTICAL_RANGE, COLLECT_ITEM_HORIZONTAL_RANGE);
            List<? extends ItemEntity> itemEntitiesToPotentiallyCollect = level.getEntitiesOfClass(ItemEntity.class, pickupBoundingBox);
            boolean collectedTargetItemEntity = false;

            ItemStack currentItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            boolean startedWithNoCurrentItem = currentItemStack.isEmpty();
            boolean collectedAnyItemEntities = false;
            for (ItemEntity itemEntity : itemEntitiesToPotentiallyCollect) {
                if (currentItemStack.isEmpty() || ItemStack.isSameItemSameComponents(currentItemStack, itemEntity.getItem())) {
                    int maxItemsToCollect = Math.max(getMaxItemsToCollectAtOnce(itemEntity.getItem()) - currentItemStack.getCount(), 0);
                    if (maxItemsToCollect > 0) {
                        int itemsCollected = Math.min(itemEntity.getItem().getCount(), maxItemsToCollect);
                        if (currentItemStack.isEmpty()) {
                            currentItemStack = itemEntity.getItem().copyWithCount(itemsCollected);
                        } else {
                            currentItemStack.grow(itemsCollected);
                        }
                        if (itemsCollected < itemEntity.getItem().getCount()) {
                            ItemStack newItemEntityItem = itemEntity.getItem().copy();
                            newItemEntityItem.shrink(itemsCollected);
                            itemEntity.setItem(newItemEntityItem);
                        } else {
                            itemEntity.discard();
                        }
                        collectedAnyItemEntities = true;
                        if (itemEntity == this.entity) {
                            collectedTargetItemEntity = true;
                        }
                    }
                }
            }

            if (startedWithNoCurrentItem && !currentItemStack.isEmpty()) {
                mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.PICKED_UP_WILDCARD_ITEM, Optional.of(Unit.INSTANCE));
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, currentItemStack);
            if (collectedAnyItemEntities) {
                mob.playSound(SoundEvents.ITEM_PICKUP, 0.2f, 2.0f + (mob.getRandom().nextFloat() - mob.getRandom().nextFloat()) * 1.4f);
            }

            // We want to clear the memories if we have collected the item entity we were targeting
            if (collectedTargetItemEntity) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }
}
