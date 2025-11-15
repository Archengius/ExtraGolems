package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.ExtraGolemsMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class CopperGolemTraderBehavior extends CopperGolemBaseBehavior {
    private static final TargetingConditions VILLAGER_INTERACTION_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
    public static final int MAX_HELD_ITEM_STACK_SIZE = 64;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(ExtraGolemsMemoryModuleTypes.TRADE_RESULT, ExtraGolemsMemoryModuleTypes.REFILL_COST_ITEM_HINT);

    public CopperGolemTraderBehavior() {
        super(ExtraGolemsCopperGolemAi.SPEED_MULTIPLIER_WHEN_IDLING, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_HORIZONTAL_SEARCH_RADIUS, ExtraGolemsCopperGolemAi.TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS);
    }

    public static boolean isValidItemToHold(ItemStack itemStack) {
        return true;
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack currentTradeResultStack = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT).orElse(ItemStack.EMPTY);
        if (!currentTradeResultStack.isEmpty()) {
            Optional<BlockPos> firstDropTargetContainer = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(Blocks.CHEST));
            return firstDropTargetContainer.map(blockPos -> new DropTradeResultInteractionTarget(this, level, blockPos));
        }

        ItemStack currentTradeCostStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        boolean hasRefillCostItemHint = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.REFILL_COST_ITEM_HINT).isPresent();
        if (currentTradeCostStack.isEmpty() || (currentTradeCostStack.isStackable() && currentTradeCostStack.getCount() == 1) || hasRefillCostItemHint) {
            Optional<BlockPos> firstPickupTargetContainer = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(BlockTags.COPPER_CHESTS));
            if (firstPickupTargetContainer.isEmpty() && hasRefillCostItemHint) {
                mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.REFILL_COST_ITEM_HINT, Optional.empty());
            }
            return firstPickupTargetContainer.map(blockPos -> new PickupCostItemInteractionTarget(this, level, blockPos));
        }

        Optional<Villager> closestVillager = findClosestVillagerInteractionTarget(level, mob);
        return closestVillager.map(villager -> new TradeWithVillagerInteractionTarget(this, villager));
    }

    private Optional<Villager> findClosestVillagerInteractionTarget(ServerLevel level, PathfinderMob mob) {
        AABB interactionBoundingBox = getTargetSearchArea(mob);
        Set<UUID> visitedEntities = getVisitedEntities(mob);
        Set<UUID> unreachableEntities = getUnreachableEntities(mob);
        List<? extends Villager> nearbyVillagers = level.getNearbyEntities(Villager.class, VILLAGER_INTERACTION_TARGETING, mob, interactionBoundingBox);

        double closestNearbyVillagerDistanceSq = Double.MAX_VALUE;
        Optional<Villager> closestNearbyVillager = Optional.empty();

        for (Villager nearbyVillager : nearbyVillagers) {
            if (!visitedEntities.contains(nearbyVillager.getUUID()) && !unreachableEntities.contains(nearbyVillager.getUUID())) {
                Holder<VillagerProfession> profession = nearbyVillager.getVillagerData().profession();
                if (!nearbyVillager.isBaby() && !profession.is(VillagerProfession.NONE) && !profession.is(VillagerProfession.NITWIT) && mob.distanceToSqr(nearbyVillager) < closestNearbyVillagerDistanceSq) {
                    closestNearbyVillager = Optional.of(nearbyVillager);
                    closestNearbyVillagerDistanceSq = mob.distanceToSqr(nearbyVillager);
                }
            }
        }
        return closestNearbyVillager;
    }

    private static class PickupCostItemInteractionTarget extends ContainerPickupInteractionTarget {

        public PickupCostItemInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = MAX_HELD_ITEM_STACK_SIZE;
        }

        @Override
        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return isValidItemToHold(itemStack);
        }

        @Override
        protected boolean performContainerBehavior(Level level, PathfinderMob mob, Container container) {
            if (super.performContainerBehavior(level, mob, container)) {
                mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.REFILL_COST_ITEM_HINT, Optional.empty());
                return true;
            }
            return false;
        }
    }

    private static class DropTradeResultInteractionTarget extends ContainerDropInteractionTarget {

        public DropTradeResultInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
        }

        @Override
        protected ItemStack getDropSourceItemStack(Level level, PathfinderMob mob) {
            return mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT).orElse(ItemStack.EMPTY);
        }

        @Override
        protected void setDropSourceItemStack(Level level, PathfinderMob mob, ItemStack itemStack) {
            mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT, itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack));
        }

        @Override
        protected boolean shouldDropItemIntoContainer(Level level, PathfinderMob mob, Container container, ItemStack itemStack) {
            return containerContainsSameItemType(container, itemStack);
        }
    }

    private static class TradeWithVillagerInteractionTarget extends BaseEntityInteractionTarget {
        private static final int VILLAGER_TRADE_INTERACTION_TIME = 60;
        private static final int TICK_TO_START_TRADE_INTERACTION = 1;
        private static final int TICK_TO_PLAY_GOLEM_INTERACTION_SOUND = 9;
        private static final int TICK_TO_PLAY_VILLAGER_INTERACTION_SOUND = 30;
        private static final int DEFAULT_MAX_TRADE_RESULT_STACK_SIZE = 16;

        protected int maxTradeResultStackSize;
        private Optional<MerchantOffer> lockedTradeOffer;

        public TradeWithVillagerInteractionTarget(CopperGolemBaseBehavior owner, Entity entity) {
            super(owner, entity);
            this.maxTradeResultStackSize = DEFAULT_MAX_TRADE_RESULT_STACK_SIZE;
        }

        protected boolean checkCanTradeWithVillager(Villager villager) {
            return !villager.isBaby() && !villager.isTrading() && !villager.getOffers().isEmpty();
        }

        protected Optional<MerchantOffer> findVillagerTradeOffer(PathfinderMob mob, Villager villager) {
            if (!checkCanTradeWithVillager(villager)) {
                return Optional.empty();
            }
            ItemStack currentCostItemCopy = mob.getItemBySlot(EquipmentSlot.MAINHAND).copy();
            if (currentCostItemCopy.isEmpty()) {
                return Optional.empty();
            }
            currentCostItemCopy.setCount(currentCostItemCopy.getMaxStackSize());
            MerchantOffer matchingOffer = villager.getOffers().getRecipeFor(currentCostItemCopy, ItemStack.EMPTY, -1);
            if (matchingOffer == null || matchingOffer.isOutOfStock()) {
                return Optional.empty();
            }
            return Optional.of(matchingOffer);
        }

        private static boolean checkCanSatisfyTradeOffer(PathfinderMob mob, MerchantOffer merchantOffer) {
            ItemStack currentCostItemCopy = mob.getItemBySlot(EquipmentSlot.MAINHAND).copy();
            if (!currentCostItemCopy.isEmpty() && currentCostItemCopy.isStackable()) {
                currentCostItemCopy.shrink(1);
            }
            return !currentCostItemCopy.isEmpty() && merchantOffer.satisfiedBy(currentCostItemCopy, ItemStack.EMPTY);
        }

        private static boolean checkHasSpaceForTradeResult(PathfinderMob mob, MerchantOffer merchantOffer) {
            ItemStack currentTradeResult = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT).orElse(ItemStack.EMPTY);
            return currentTradeResult.isEmpty() || (ItemStack.isSameItemSameComponents(currentTradeResult, merchantOffer.getResult()) &&
                    currentTradeResult.getCount() + merchantOffer.getResult().getCount() <= currentTradeResult.getMaxStackSize());
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return VILLAGER_TRADE_INTERACTION_TIME;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack currentCostItem = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) && !currentCostItem.isEmpty() &&
                    !(currentCostItem.isStackable() && currentCostItem.getCount() == 1);
        }

        @Override
        public void startTargetInteraction(Level level, PathfinderMob mob) {
            super.startTargetInteraction(level, mob);
            this.lockedTradeOffer = Optional.empty();
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            if (ticksSinceInteractionStart == TICK_TO_START_TRADE_INTERACTION) {
                this.lockedTradeOffer = Optional.empty();
                if (this.entity instanceof Villager villager) {
                    Optional<MerchantOffer> potentialTradeOffer = findVillagerTradeOffer(mob, villager);
                    if (potentialTradeOffer.isPresent() && checkHasSpaceForTradeResult(mob, potentialTradeOffer.get())) {
                        if (checkCanSatisfyTradeOffer(mob, potentialTradeOffer.get())) {
                            this.lockedTradeOffer = potentialTradeOffer;
                        } else {
                            mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.REFILL_COST_ITEM_HINT, Optional.of(Unit.INSTANCE));
                        }
                    }
                }
                if (mob instanceof CopperGolem copperGolem) {
                    copperGolem.setState(this.lockedTradeOffer.isEmpty() ? CopperGolemState.DROPPING_NO_ITEM : CopperGolemState.DROPPING_ITEM);
                }
            }

            if (ticksSinceInteractionStart == TICK_TO_PLAY_GOLEM_INTERACTION_SOUND) {
                mob.playSound(this.lockedTradeOffer.isEmpty() ? SoundEvents.COPPER_GOLEM_ITEM_NO_DROP : SoundEvents.COPPER_GOLEM_ITEM_DROP);
            }

            if (ticksSinceInteractionStart == TICK_TO_PLAY_VILLAGER_INTERACTION_SOUND) {
                if (this.entity instanceof Villager villager && !villager.isTrading()) {
                    villager.notifyTradeUpdated(this.lockedTradeOffer.map(MerchantOffer::getResult).orElse(ItemStack.EMPTY));
                }
            }
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            boolean hasSuccessfullyTraded = false;
            if (this.entity instanceof Villager villager && checkCanTradeWithVillager(villager) && this.lockedTradeOffer.isPresent()) {
                MerchantOffer merchantOffer = this.lockedTradeOffer.get();

                boolean shouldContinueTrading = true;
                while (villager.getOffers().contains(merchantOffer) && !merchantOffer.isOutOfStock() && checkHasSpaceForTradeResult(mob, merchantOffer) && shouldContinueTrading) {
                    shouldContinueTrading = false;

                    if (checkCanSatisfyTradeOffer(mob, merchantOffer)) {
                        ItemStack currentCostItem = mob.getItemBySlot(EquipmentSlot.MAINHAND);
                        merchantOffer.take(currentCostItem, ItemStack.EMPTY);

                        ItemStack newTradeResult = merchantOffer.assemble();
                        ItemStack currentTradeResult = mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT).orElse(ItemStack.EMPTY);
                        if (currentTradeResult.isEmpty()) {
                            currentTradeResult = newTradeResult.copy();
                        } else {
                            currentTradeResult.grow(newTradeResult.getCount());
                        }

                        mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.TRADE_RESULT, currentTradeResult);
                        villager.notifyTrade(merchantOffer);

                        hasSuccessfullyTraded = true;
                        shouldContinueTrading = currentTradeResult.getCount() < this.maxTradeResultStackSize;
                    } else {
                        mob.getBrain().setMemory(ExtraGolemsMemoryModuleTypes.REFILL_COST_ITEM_HINT, Optional.of(Unit.INSTANCE));
                    }
                }

                if (hasSuccessfullyTraded) {
                    level.broadcastEntityEvent(villager, EntityEvent.VILLAGER_HAPPY);
                    villager.playSound(villager.getNotifyTradeSound(), 0.5f, 1.0f);
                }
            }

            this.lockedTradeOffer = Optional.empty();
            if (hasSuccessfullyTraded) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }

        @Override
        public boolean isTargetCurrentlyOccupied(Level level) {
            return this.entity instanceof Villager villager && villager.isTrading();
        }
    }
}
