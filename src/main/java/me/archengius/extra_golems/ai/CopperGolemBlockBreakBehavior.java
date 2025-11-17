package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.util.ExtraGolemsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

public abstract class CopperGolemBlockBreakBehavior extends CopperGolemBaseBehavior {
    public static final float BLOCK_BREAK_SPEED_MODIFIER = 1.0f;
    public static final int BLOCK_BREAK_SPEED_HORIZONTAL_SEARCH_RADIUS = 16;
    public static final int BLOCK_BREAK_SPEED_VERTICAL_SEARCH_RADIUS = 2;
    public static final int MAX_HELD_ITEM_STACK_SIZE = 1;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of();

    protected CopperGolemBlockBreakBehavior(float speedModifier, int horizontalSearchDistance, int verticalSearchDistance) {
        super(speedModifier, horizontalSearchDistance, verticalSearchDistance);
    }

    protected CopperGolemBlockBreakBehavior() {
        this(BLOCK_BREAK_SPEED_MODIFIER, BLOCK_BREAK_SPEED_HORIZONTAL_SEARCH_RADIUS, BLOCK_BREAK_SPEED_VERTICAL_SEARCH_RADIUS);
    }

    public static boolean isValidItemToHold(ItemStack itemStack) {
        return itemStack.has(DataComponents.TOOL);
    }

    protected abstract Vec3 getMarkerBlockSearchAreaOffset(Level level, PathfinderMob mob);

    protected abstract boolean isMarkerBlockState(BlockState blockState);

    protected abstract Optional<BlockPos> getBreakTargetBlockPos(Level level, PathfinderMob mob, BlockPos markerBlockPos);

    protected abstract boolean shouldDestroyBlockState(BlockState blockState);

    protected boolean shouldDestroyBlock(Level level, PathfinderMob mob, BlockPos blockPos, BlockState blockState, ItemStack itemStack) {
        return ExtraGolemsUtil.checkShouldDestroyBlock((ServerLevel) level, mob, blockPos, blockState, itemStack) && shouldDestroyBlockState(blockState);
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack heldItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (heldItemStack.isEmpty()) {
            Optional<BlockPos> closestContainerBlockPos = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(BlockTags.COPPER_CHESTS));
            // Since some blocks can be broken without holding a tool, we allow the fall-through here if we cannot find a tool
            if (closestContainerBlockPos.isPresent()) {
                return Optional.of(new PickupToolInteractionTarget(this, level, closestContainerBlockPos.get()));
            }
        }
        Optional<BlockPos> closestBlockBreakPos = findClosestBlockBreakPos(level, mob);
        return closestBlockBreakPos.map(blockPos -> new BreakBlockInteractionTarget(this, level, blockPos));
    }

    private Optional<BlockPos> findClosestBlockBreakPos(Level level, PathfinderMob mob) {
        Vec3 markerSearchAreaOffset = getMarkerBlockSearchAreaOffset(level, mob);
        AABB targetSearchBoundingBox = getTargetSearchArea(mob).move(markerSearchAreaOffset);
        Set<GlobalPos> visitedPositions = getVisitedPositions(mob);
        Set<GlobalPos> unreachablePositions = getUnreachablePositions(mob);
        Vec3 entityPosition = mob.position();
        ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);

        Optional<BlockPos> closestBlockBreakPos = Optional.empty();
        double closestMarkerBlockPosDistanceSq = Double.MAX_VALUE;

        for (BlockPos markerBlockPos : BlockPos.betweenClosed(targetSearchBoundingBox)) {
            BlockState markerBlockState = level.getBlockState(markerBlockPos);
            if (isMarkerBlockState(markerBlockState) && markerBlockPos.distToCenterSqr(entityPosition) < closestMarkerBlockPosDistanceSq) {
                Optional<BlockPos> blockBreakPos = getBreakTargetBlockPos(level, mob, new BlockPos(markerBlockPos));
                if (blockBreakPos.isPresent() && shouldDestroyBlock(level, mob, blockBreakPos.get(), level.getBlockState(blockBreakPos.get()), itemStack)) {
                    GlobalPos globalPos = new GlobalPos(level.dimension(), blockBreakPos.get());
                    if (!visitedPositions.contains(globalPos) && !unreachablePositions.contains(globalPos)) {
                        closestBlockBreakPos = blockBreakPos;
                        closestMarkerBlockPosDistanceSq = markerBlockPos.distToCenterSqr(entityPosition);
                    }
                }
            }
        }
        return closestBlockBreakPos;
    }

    protected static class PickupToolInteractionTarget extends ContainerPickupInteractionTarget {

        public PickupToolInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = MAX_HELD_ITEM_STACK_SIZE;
        }

        @Override
        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return isValidItemToHold(itemStack);
        }
    }

    protected static class BreakBlockInteractionTarget extends BlockInteractionTarget {
        private static final int BREAK_BLOCK_BASE_INTERACTION_TIME = 20;
        private static final int BREAK_SOUND_PLAY_INTERVAL = 4;
        private static final int GOLEM_SWING_ARM_INTERVAL = 20;

        private int currentBlockBreakVisualProgress = -1;
        private int currentMaxInteractionTime = 0;
        private boolean destroyedBlockSuccessfully = false;
        private boolean isDestroyingBlock = false;
        private ItemStack currentBreakingItemStack = ItemStack.EMPTY;

        protected BreakBlockInteractionTarget(CopperGolemBlockBreakBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
        }

        private void updateBlockBreakVisualProgress(Level level, PathfinderMob mob, int newBreakProgress) {
            if (this.currentBlockBreakVisualProgress != newBreakProgress) {
                this.currentBlockBreakVisualProgress = newBreakProgress;
                level.destroyBlockProgress(mob.getId(), this.blockPos, newBreakProgress);
            }
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return this.currentMaxInteractionTime;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) &&
                    ((CopperGolemBlockBreakBehavior) this.owner).shouldDestroyBlock(level, mob, this.blockPos, this.blockState, itemStack);
        }

        @Override
        public void startTargetInteraction(Level level, PathfinderMob mob) {
            super.startTargetInteraction(level, mob);

            this.currentMaxInteractionTime = BREAK_BLOCK_BASE_INTERACTION_TIME;
            this.destroyedBlockSuccessfully = false;
            this.isDestroyingBlock = false;

            ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            if (((CopperGolemBlockBreakBehavior) this.owner).shouldDestroyBlock(level, mob, this.blockPos, this.blockState, itemStack)) {
                ExtraGolemsUtil.startDestroyingBlock((ServerLevel) level, mob, this.blockPos, this.blockState, itemStack, EquipmentSlot.MAINHAND);
                updateBlockBreakVisualProgress(level, mob, 0);

                this.isDestroyingBlock = true;
                this.currentBreakingItemStack = itemStack.copy();
            }
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            // Reset block break progress if the item we are holding has changed in any way
            ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            if (this.isDestroyingBlock && !ItemStack.isSameItemSameComponents(itemStack, this.currentBreakingItemStack)) {
                updateBlockBreakVisualProgress(level, mob, -1);
                this.isDestroyingBlock = false;
            }

            if (this.isDestroyingBlock) {
                float currentDestroyProgress = ExtraGolemsUtil.calculateBlockDestroyProgress(level, mob, this.blockPos, this.blockState, itemStack) * (ticksSinceInteractionStart + 1);
                updateBlockBreakVisualProgress(level, mob, (int) (currentDestroyProgress * 10.0f));

                if (ticksSinceInteractionStart % BREAK_SOUND_PLAY_INTERVAL == 0) {
                    SoundType soundType = this.blockState.getSoundType();
                    level.playSound(null, this.blockPos, soundType.getHitSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0f) / 8.0f, soundType.getPitch() * 0.5f);
                }
                if (ticksSinceInteractionStart % GOLEM_SWING_ARM_INTERVAL == 0) {
                    if (mob instanceof CopperGolem copperGolem) {
                        copperGolem.setState(CopperGolemState.IDLE);
                    }
                }
                if (ticksSinceInteractionStart % GOLEM_SWING_ARM_INTERVAL == 1) {
                    if (mob instanceof CopperGolem copperGolem) {
                        copperGolem.setState(CopperGolemState.DROPPING_ITEM);
                    }
                }

                if (currentDestroyProgress >= 1.0f) {
                    updateBlockBreakVisualProgress(level, mob, -1);

                    this.isDestroyingBlock = false;
                    if (mob instanceof CopperGolem copperGolem) {
                        copperGolem.setState(CopperGolemState.IDLE);
                    }
                    if (ExtraGolemsUtil.destroyBlock((ServerLevel) level, mob, this.blockPos, this.blockState, itemStack)) {
                        this.destroyedBlockSuccessfully = true;
                    }
                }
                this.currentMaxInteractionTime++;
            }
        }

        @Override
        public void cancelTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.cancelTargetInteraction(level, mob, ticksSinceInteractionStart);
            updateBlockBreakVisualProgress(level, mob, -1);
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            updateBlockBreakVisualProgress(level, mob, -1);
            if (this.destroyedBlockSuccessfully) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }

    public static class CopperBlockMarkedBlock extends CopperGolemBlockBreakBehavior {
        public CopperBlockMarkedBlock() {
        }

        @Override
        protected Vec3 getMarkerBlockSearchAreaOffset(Level level, PathfinderMob mob) {
            Direction breakBlockInDirection = Direction.UP;
            return breakBlockInDirection.getOpposite().getUnitVec3();
        }

        @Override
        protected boolean isMarkerBlockState(BlockState blockState) {
            return blockState.is(BlockTags.COPPER);
        }

        @Override
        protected Optional<BlockPos> getBreakTargetBlockPos(Level level, PathfinderMob mob, BlockPos markerBlockPos) {
            Direction breakBlockInDirection = Direction.UP;
            return Optional.of(markerBlockPos.relative(breakBlockInDirection));
        }

        @Override
        protected boolean shouldDestroyBlockState(BlockState blockState) {
            return true;
        }
    }

    public static class GrownVegetationBlock extends CopperGolemBlockBreakBehavior {
        public GrownVegetationBlock() {
        }

        private static boolean isGrownVegetationBlockState(BlockState blockState) {
            if (blockState.getBlock() instanceof CropBlock) {
                return blockState.getValue(CropBlock.AGE) == CropBlock.MAX_AGE;
            }
            return blockState.getBlock() == Blocks.PUMPKIN || blockState.getBlock() == Blocks.MELON;
        }

        @Override
        protected boolean isMarkerBlockState(BlockState blockState) {
            return isGrownVegetationBlockState(blockState);
        }

        @Override
        protected Vec3 getMarkerBlockSearchAreaOffset(Level level, PathfinderMob mob) {
            return Vec3.ZERO;
        }

        @Override
        protected Optional<BlockPos> getBreakTargetBlockPos(Level level, PathfinderMob mob, BlockPos markerBlockPos) {
            return Optional.of(markerBlockPos);
        }

        @Override
        protected boolean shouldDestroyBlockState(BlockState blockState) {
            return isGrownVegetationBlockState(blockState);
        }
    }
}
