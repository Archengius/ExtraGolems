package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import me.archengius.extra_golems.util.ExtraGolemsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class CopperGolemLumberjackBehavior extends CopperGolemBaseBehavior {
    private static final float LUMBERJACK_SPEED_MODIFIER = 1.0f;
    private static final int LUMBER_HORIZONTAL_SEARCH_RADIUS = 16;
    private static final int LUMBER_VERTICAL_SEARCH_RADIUS = 2;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of();
    public static final int MAX_HELD_ITEM_STACK_SIZE = 1;

    public CopperGolemLumberjackBehavior() {
        super(LUMBERJACK_SPEED_MODIFIER, LUMBER_HORIZONTAL_SEARCH_RADIUS, LUMBER_VERTICAL_SEARCH_RADIUS);
    }

    public static boolean isValidItemToHold(ItemStack itemStack) {
        return itemStack.is(ItemTags.AXES);
    }

    private static boolean shouldDestroyBlockState(BlockState blockState) {
        return blockState.is(BlockTags.LOGS);
    }

    private static boolean isRequiredBlockStateForDestruction(BlockState blockState) {
        if (blockState.is(BlockTags.LEAVES)) {
            if (blockState.getBlock() instanceof LeavesBlock) {
                return !blockState.getValue(LeavesBlock.PERSISTENT);
            }
            return true;
        }
        return false;
    }

    private static boolean shouldDestroyBlock(Level level, PathfinderMob mob, BlockPos blockPos, BlockState blockState, ItemStack itemStack) {
        return shouldDestroyBlockState(blockState) && ExtraGolemsUtil.checkShouldDestroyBlock((ServerLevel) level, mob, blockPos, blockState, itemStack);
    }

    private Optional<BlockPos> findClosestLumberTargetPos(ServerLevel level, PathfinderMob mob) {
        AABB targetSearchBoundingBox = getTargetSearchArea(mob);
        Set<GlobalPos> visitedPositions = getVisitedPositions(mob);
        Set<GlobalPos> unreachablePositions = getUnreachablePositions(mob);

        ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        Vec3 entityPosition = mob.position();

        Optional<BlockPos> closestBlockPos = Optional.empty();
        double closestBlockPosDistanceSq = Double.MAX_VALUE;

        for (BlockPos blockPos : BlockPos.betweenClosed(targetSearchBoundingBox)) {
            BlockState blockState = level.getBlockState(blockPos);
            if (shouldDestroyBlockState(blockState) && blockPos.distToCenterSqr(entityPosition) < closestBlockPosDistanceSq) {
                GlobalPos globalPos = new GlobalPos(level.dimension(), blockPos);
                if (!visitedPositions.contains(globalPos) && !unreachablePositions.contains(globalPos)) {
                    if (shouldDestroyBlock(level, mob, blockPos, blockState, itemStack)) {
                        closestBlockPos = Optional.of(new BlockPos(blockPos));
                        closestBlockPosDistanceSq = blockPos.distToCenterSqr(entityPosition);
                    }
                }
            }
        }
        return closestBlockPos;
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack itemInHand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (itemInHand.isEmpty()) {
            Optional<BlockPos> closestContainerTarget = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(BlockTags.COPPER_CHESTS));
            return closestContainerTarget.map(blockPos -> new ContainerAxePickupInteractionTarget(this, level, blockPos));
        }

        Optional<BlockPos> closestLumberTargetPos = findClosestLumberTargetPos(level, mob);
        return closestLumberTargetPos.map(blockPos -> new TreeLumberInteractionTarget(this, level, blockPos));
    }

    private static class ContainerAxePickupInteractionTarget extends ContainerPickupInteractionTarget {
        private static final int AXE_PICKUP_INTERACTION_TIME = 100;

        public ContainerAxePickupInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = AXE_PICKUP_INTERACTION_TIME;
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return AXE_PICKUP_INTERACTION_TIME;
        }

        @Override
        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return isValidItemToHold(itemStack);
        }
    }

    private static class TreeLumberInteractionTarget extends BlockInteractionTarget {
        private static final int LUMBER_BASE_INTERACTION_TIME = 20;
        private static final int MAX_BLOCKS_TO_DESTROY_PER_INTERACTION = 64;
        private static final float DISTANCE_BREAK_SPEED_SCALE_MAX_DISTANCE = 15.0f;
        private static final float DISTANCE_BREAK_SPEED_MULTIPLIER = 3.0f;
        private static final int BREAK_SOUND_PLAY_INTERVAL = 4;
        private static final int GOLEM_SWING_ARM_INTERVAL = 20;
        private static final List<Vec3i> ADJACENT_BLOCKS_TO_CHECK = ImmutableList.of(
                new Vec3i(-1, 0, 0), new Vec3i(1, 0, 0), new Vec3i(0, 0, -1), new Vec3i(0, 0, 1), new Vec3i(0, -1, 0), new Vec3i(0, 1, 0),
                new Vec3i(-1, -1, 0), new Vec3i(1, -1, 0), new Vec3i(0, -1, -1), new Vec3i(0, -1, 1),
                new Vec3i(-1, 1, 0), new Vec3i(1, 1, 0), new Vec3i(0, 1, -1), new Vec3i(0, 1, 1),
                new Vec3i(-1, 0, -1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, -1), new Vec3i(1, 0, 1),
                new Vec3i(-1, -1, -1), new Vec3i(-1, -1, 1), new Vec3i(1, -1, -1), new Vec3i(1, -1, 1),
                new Vec3i(-1, 1, -1), new Vec3i(-1, 1, 1), new Vec3i(1, 1, -1), new Vec3i(1, 1, 1));

        private List<BlockPos> blocksToDestroy = ImmutableList.of();
        private int currentMaxInteractionTime = 0;
        private int blocksDestroyed = 0;
        private int lastDestroyedBlockIndex = -1;
        private int currentBlockBreakVisualProgress = -1;

        private int currentlyBreakingBlockIndex = -1;
        private int blockDestroyStartTicks = 0;
        private BlockState currentBreakingBlockState = Blocks.AIR.defaultBlockState();
        private ItemStack currentBreakingItemStack = ItemStack.EMPTY;

        public TreeLumberInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
        }

        private void updateBlockBreakVisualProgress(Level level, PathfinderMob mob, int newBreakProgress) {
            if (this.currentBlockBreakVisualProgress != newBreakProgress) {
                this.currentBlockBreakVisualProgress = newBreakProgress;
                level.destroyBlockProgress(mob.getId(), this.blockPos, newBreakProgress);
            }
        }

        private static List<BlockPos> populateBlocksToDestroy(Level level, BlockPos sourceBlockPos) {
            Set<BlockPos> checkedBlockSet = new HashSet<>(MAX_BLOCKS_TO_DESTROY_PER_INTERACTION * 4);
            List<BlockPos> pendingBlockList = Lists.newArrayListWithCapacity(MAX_BLOCKS_TO_DESTROY_PER_INTERACTION);
            List<BlockPos> resultBlockList = Lists.newArrayListWithCapacity(MAX_BLOCKS_TO_DESTROY_PER_INTERACTION / 2);
            boolean foundRequiredBlockState = false;

            pendingBlockList.add(sourceBlockPos);
            checkedBlockSet.add(sourceBlockPos);

            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            while (!pendingBlockList.isEmpty() && resultBlockList.size() < MAX_BLOCKS_TO_DESTROY_PER_INTERACTION) {
                BlockPos currentBlockPos = pendingBlockList.removeLast();
                BlockState currentBlockState = level.getBlockState(currentBlockPos);

                if (shouldDestroyBlockState(currentBlockState)) {
                    resultBlockList.add(currentBlockPos);

                    for (Vec3i blockPosOffset : ADJACENT_BLOCKS_TO_CHECK) {
                        mutableBlockPos.setWithOffset(currentBlockPos, blockPosOffset);
                        if (!checkedBlockSet.contains(mutableBlockPos)) {
                            BlockPos immutableBlockPos = mutableBlockPos.immutable();
                            pendingBlockList.add(immutableBlockPos);
                            checkedBlockSet.add(immutableBlockPos);
                        }
                    }
                }
                if (isRequiredBlockStateForDestruction(currentBlockState)) {
                    foundRequiredBlockState = true;
                }
            }

            if (foundRequiredBlockState) {
                resultBlockList.sort(Comparator.comparing(blockPos -> -blockPos.distSqr(sourceBlockPos)));
                return resultBlockList;
            } else {
                return ImmutableList.of();
            }
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return this.currentMaxInteractionTime;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) && !itemStack.isEmpty() &&
                    shouldDestroyBlock(level, mob, this.blockPos, this.blockState, itemStack);
        }

        @Override
        public void startTargetInteraction(Level level, PathfinderMob mob) {
            super.startTargetInteraction(level, mob);

            this.blocksToDestroy = populateBlocksToDestroy(level, this.blockPos);
            this.currentMaxInteractionTime = LUMBER_BASE_INTERACTION_TIME;
            this.blocksDestroyed = 0;
            this.lastDestroyedBlockIndex = -1;
            this.currentlyBreakingBlockIndex = -1;
        }

        @Override
        public void cancelTargetInteraction(Level level, PathfinderMob mob) {
            super.cancelTargetInteraction(level, mob);
            updateBlockBreakVisualProgress(level, mob, -1);
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            // If we are not currently breaking a block, attempt to start breaking one
            while (this.currentlyBreakingBlockIndex == -1 && this.lastDestroyedBlockIndex + 1 < this.blocksToDestroy.size()) {
                int newBlockIndex = this.lastDestroyedBlockIndex + 1;
                BlockPos newBlockPos = this.blocksToDestroy.get(newBlockIndex);
                BlockState newBlockState = level.getBlockState(newBlockPos);

                ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
                if (shouldDestroyBlock(level, mob, newBlockPos, newBlockState, itemStack)) {
                    ExtraGolemsUtil.startDestroyingBlock((ServerLevel) level, mob, newBlockPos, newBlockState, itemStack, EquipmentSlot.MAINHAND);
                    updateBlockBreakVisualProgress(level, mob, 0);

                    this.currentlyBreakingBlockIndex = newBlockIndex;
                    this.currentBreakingBlockState = newBlockState;
                    this.currentBreakingItemStack = itemStack.copy();
                    this.blockDestroyStartTicks = ticksSinceInteractionStart;
                } else {
                    this.lastDestroyedBlockIndex = newBlockIndex;
                }
            }

            // Tick breaking progress of the current block
            if (this.currentlyBreakingBlockIndex != -1) {
                BlockPos currentBlockPos = this.blocksToDestroy.get(this.currentlyBreakingBlockIndex);
                BlockState currentBlockState = level.getBlockState(currentBlockPos);
                ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);

                if (currentBlockState == this.currentBreakingBlockState && ItemStack.isSameItemSameComponents(itemStack, this.currentBreakingItemStack)) {
                    int currentProgressTicks = ticksSinceInteractionStart - this.blockDestroyStartTicks;
                    float clampedDistanceFromInteractionPoint = Math.min((float)currentBlockPos.getCenter().distanceTo(this.blockPos.getCenter()), DISTANCE_BREAK_SPEED_SCALE_MAX_DISTANCE);
                    float distanceSpeedDivisor = 1.0f + clampedDistanceFromInteractionPoint / DISTANCE_BREAK_SPEED_SCALE_MAX_DISTANCE * DISTANCE_BREAK_SPEED_MULTIPLIER;
                    float currentDestroyProgress = ExtraGolemsUtil.calculateBlockDestroyProgress(level, mob, currentBlockPos, currentBlockState, itemStack) / distanceSpeedDivisor * (currentProgressTicks + 1);
                    updateBlockBreakVisualProgress(level, mob, (int) (currentDestroyProgress * 10.0f));

                    if (currentProgressTicks % BREAK_SOUND_PLAY_INTERVAL == 0) {
                        SoundType soundType = currentBlockState.getSoundType();
                        level.playSound(null, this.blockPos, soundType.getHitSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0f) / 8.0f, soundType.getPitch() * 0.5f);
                    }
                    if (currentProgressTicks % GOLEM_SWING_ARM_INTERVAL == 0) {
                        if (mob instanceof CopperGolem copperGolem) {
                            copperGolem.setState(CopperGolemState.IDLE);
                        }
                    }
                    if (currentProgressTicks % GOLEM_SWING_ARM_INTERVAL == 1) {
                        if (mob instanceof CopperGolem copperGolem) {
                            copperGolem.setState(CopperGolemState.DROPPING_ITEM);
                        }
                    }

                    if (currentDestroyProgress >= 1.0f) {
                        updateBlockBreakVisualProgress(level, mob, -1);
                        this.lastDestroyedBlockIndex = this.currentlyBreakingBlockIndex;
                        this.currentlyBreakingBlockIndex = -1;
                        if (mob instanceof CopperGolem copperGolem) {
                            copperGolem.setState(CopperGolemState.IDLE);
                        }

                        if (ExtraGolemsUtil.destroyBlock((ServerLevel) level, mob, currentBlockPos, currentBlockState, itemStack)) {
                            this.blocksDestroyed++;
                        }
                    }

                } else {
                    updateBlockBreakVisualProgress(level, mob, -1);
                    this.currentlyBreakingBlockIndex = -1;
                    if (mob instanceof CopperGolem copperGolem) {
                        copperGolem.setState(CopperGolemState.IDLE);
                    }
                }
                this.currentMaxInteractionTime++;
            }
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            updateBlockBreakVisualProgress(level, mob, -1);
            if (this.blocksDestroyed > 0) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }
}
