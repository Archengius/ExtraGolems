package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.util.EntityBlockPlaceContext;
import me.archengius.extra_golems.mixin.BlockItemAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public abstract class CopperGolemBlockPlacementBehavior extends CopperGolemBaseBehavior {
    public static final float BLOCK_PLACEMENT_SPEED_MODIFIER = 1.0f;
    public static final int BLOCK_PLACEMENT_HORIZONTAL_SEARCH_RADIUS = 16;
    public static final int BLOCK_PLACEMENT_VERTICAL_SEARCH_RADIUS = 2;
    public static final int MAX_HELD_ITEM_STACK_SIZE = 16;
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of();

    private final Predicate<ItemStack> validBlockForPlacementPredicate;

    protected CopperGolemBlockPlacementBehavior(float speedModifier, int horizontalSearchDistance, int verticalSearchDistance, Predicate<ItemStack> validBlockForPlacementPredicate) {
        super(speedModifier, horizontalSearchDistance, verticalSearchDistance);
        this.validBlockForPlacementPredicate = validBlockForPlacementPredicate;
    }

    protected CopperGolemBlockPlacementBehavior(Predicate<ItemStack> validBlockForPlacementPredicate) {
        this(BLOCK_PLACEMENT_SPEED_MODIFIER, BLOCK_PLACEMENT_HORIZONTAL_SEARCH_RADIUS, BLOCK_PLACEMENT_VERTICAL_SEARCH_RADIUS, validBlockForPlacementPredicate);
    }

    public static boolean canPlaceItemStackAsBlock(ItemStack itemStack) {
        return !itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem;
    }

    @Override
    protected Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob) {
        ItemStack handItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (handItemStack.isEmpty() || (handItemStack.isStackable() && handItemStack.getCount() == 1)) {
            Optional<BlockPos> closestContainerBlockPos = findClosestContainerInteractionTarget(level, mob, blockState -> blockState.is(BlockTags.COPPER_CHESTS));
            return closestContainerBlockPos.map(blockPos -> new PickupBlockToPlaceInteractionTarget(this, level, blockPos));
        }
        if (canPlaceItemStackAsBlock(handItemStack) && this.validBlockForPlacementPredicate.test(handItemStack)) {
            Optional<BlockPlacementTarget> closestPlacementBlockTarget = findClosestBlockPlacementTarget(level, mob);
            return closestPlacementBlockTarget.map(placementTarget -> new BlockPlacementInteractionTarget(this, level, placementTarget));
        }
        return Optional.empty();
    }

    protected Vec3 getMarkerBlockSearchAreaOffset(Level level, PathfinderMob mob) {
        Direction placeFromBlockFace = Direction.UP;
        return placeFromBlockFace.getOpposite().getUnitVec3();
    }

    protected abstract boolean isMarkerBlockState(BlockState blockState);

    protected Optional<BlockPlacementTarget> getBlockPlacementTarget(Level level, PathfinderMob mob, BlockPos markerBlockPos) {
        Direction placeFromBlockFace = Direction.UP;
        return Optional.of(new BlockPlacementTarget(markerBlockPos, placeFromBlockFace, false));
    }

    private static boolean checkValidBlockPlacementHit(Level level, PathfinderMob mob, BlockPlacementTarget placementTarget) {
        BlockPos placementSourceBlockPos = placementTarget.placeFromBlockPos();
        BlockState placeFromBlockState = level.getBlockState(placementSourceBlockPos);
        VoxelShape blockInteractionShape = placeFromBlockState.getShape(level, placementSourceBlockPos, CollisionContext.of(mob));
        return !blockInteractionShape.isEmpty();
    }

    protected boolean checkBlockPlacementTarget(Level level, PathfinderMob mob, BlockPlacementTarget placementTarget) {
        if (!checkValidBlockPlacementHit(level, mob, placementTarget)) {
            return false;
        }
        BlockState currentBlockState = level.getBlockState(placementTarget.placementTargetBlockPos());
        return currentBlockState.canBeReplaced();
    }

    private Optional<BlockPlacementTarget> findClosestBlockPlacementTarget(Level level, PathfinderMob mob) {
        Vec3 markerSearchAreaOffset = getMarkerBlockSearchAreaOffset(level, mob);
        AABB targetSearchBoundingBox = getTargetSearchArea(mob).move(markerSearchAreaOffset);
        Set<GlobalPos> visitedPositions = getVisitedPositions(mob);
        Set<GlobalPos> unreachablePositions = getUnreachablePositions(mob);
        Vec3 entityPosition = mob.position();

        Optional<BlockPlacementTarget> closestPlacementTarget = Optional.empty();
        double closestMarkerBlockPosDistanceSq = Double.MAX_VALUE;

        for (BlockPos markerBlockPos : BlockPos.betweenClosed(targetSearchBoundingBox)) {
            BlockState markerBlockState = level.getBlockState(markerBlockPos);
            if (!markerBlockState.isAir() && isMarkerBlockState(markerBlockState) && markerBlockPos.distToCenterSqr(entityPosition) < closestMarkerBlockPosDistanceSq) {
                Optional<BlockPlacementTarget> placementTarget = getBlockPlacementTarget(level, mob, new BlockPos(markerBlockPos));
                if (placementTarget.isPresent() && checkBlockPlacementTarget(level, mob, placementTarget.get())) {
                    GlobalPos globalPos = new GlobalPos(level.dimension(), placementTarget.get().placeFromBlockPos());
                    if (!visitedPositions.contains(globalPos) && !unreachablePositions.contains(globalPos)) {
                        closestPlacementTarget = placementTarget;
                        closestMarkerBlockPosDistanceSq = markerBlockPos.distToCenterSqr(entityPosition);
                    }
                }
            }
        }
        return closestPlacementTarget;
    }

    protected record BlockPlacementTarget(BlockPos placeFromBlockPos, Direction placeFromBlockFace, boolean replaceClickedBlock) {
        public BlockPos placementTargetBlockPos() {
            return replaceClickedBlock() ? placeFromBlockPos() : placeFromBlockPos().relative(placeFromBlockFace());
        }
    }

    protected static class PickupBlockToPlaceInteractionTarget extends ContainerPickupInteractionTarget {

        public PickupBlockToPlaceInteractionTarget(CopperGolemBlockPlacementBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = MAX_HELD_ITEM_STACK_SIZE;
        }

        @Override
        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return canPlaceItemStackAsBlock(itemStack) && ((CopperGolemBlockPlacementBehavior) this.owner).validBlockForPlacementPredicate.test(itemStack);
        }
    }

    protected static class BlockPlacementInteractionTarget extends BlockInteractionTarget {
        private static final int BLOCK_PLACEMENT_INTERACTION_TIME = 40;
        private static final int TICK_TO_START_INTERACTION_ANIMATION = 1;
        private static final int TICK_TO_PLAY_INTERACTION_SOUND = 9;
        private static final float BLOCK_PLACEMENT_INTERACTION_RANGE = 0.5f;

        private final BlockPlacementTarget blockPlacementTarget;
        private boolean shouldAttemptBlockPlacement = false;

        protected BlockPlacementInteractionTarget(CopperGolemBlockPlacementBehavior owner, Level level, BlockPlacementTarget blockPlacementTarget) {
            super(owner, level, blockPlacementTarget.placeFromBlockPos());
            this.blockPlacementTarget = blockPlacementTarget;
        }

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return BLOCK_PLACEMENT_INTERACTION_TIME;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) &&
                    (canPlaceItemStackAsBlock(itemStack) && ((CopperGolemBlockPlacementBehavior) this.owner).validBlockForPlacementPredicate.test(itemStack)) &&
                    ((CopperGolemBlockPlacementBehavior) this.owner).checkBlockPlacementTarget(level, mob, this.blockPlacementTarget);
        }

        protected static BlockPlaceContext createBlockPlacementContext(PathfinderMob mob, BlockPlacementTarget blockPlacementTarget) {
            ItemStack itemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return new EntityBlockPlaceContext(mob, blockPlacementTarget.placeFromBlockPos(), blockPlacementTarget.placeFromBlockFace(), itemStack,
                    blockPlacementTarget.replaceClickedBlock(), false);
        }

        protected boolean checkCanPlaceBlock(PathfinderMob mob) {
            BlockPlaceContext blockPlaceContext = createBlockPlacementContext(mob, this.blockPlacementTarget);
            if (blockPlaceContext.getItemInHand().getItem() instanceof BlockItem blockItem) {
                BlockPlaceContext updatedPlaceContext = blockItem.updatePlacementContext(blockPlaceContext);
                if (updatedPlaceContext != null && updatedPlaceContext.canPlace()) {
                    BlockState placementBlockState = ((BlockItemAccessor) blockItem).extra_golems$getPlacementState(updatedPlaceContext);
                    return placementBlockState != null;
                }
            }
            return false;
        }

        protected boolean placeBlock(PathfinderMob mob) {
            BlockPlaceContext blockPlaceContext = createBlockPlacementContext(mob, this.blockPlacementTarget);
            if (blockPlaceContext.getItemInHand().getItem() instanceof BlockItem blockItem) {
                return blockItem.place(blockPlaceContext) != InteractionResult.FAIL;
            }
            return false;
        }

        @Override
        public void startTargetInteraction(Level level, PathfinderMob mob) {
            super.startTargetInteraction(level, mob);
            this.shouldAttemptBlockPlacement = checkCanPlaceBlock(mob);
        }

        @Override
        public AABB getTargetBoundingBox(Level level) {
            return Shapes.block().bounds().move(this.blockPos).inflate(BLOCK_PLACEMENT_INTERACTION_RANGE);
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            if (mob instanceof CopperGolem copperGolem) {
                if (ticksSinceInteractionStart == TICK_TO_START_INTERACTION_ANIMATION) {
                    copperGolem.setState(this.shouldAttemptBlockPlacement ? CopperGolemState.DROPPING_ITEM : CopperGolemState.DROPPING_NO_ITEM);
                }
            }
            if (ticksSinceInteractionStart == TICK_TO_PLAY_INTERACTION_SOUND) {
                mob.playSound(this.shouldAttemptBlockPlacement ? SoundEvents.COPPER_GOLEM_ITEM_DROP : SoundEvents.COPPER_GOLEM_ITEM_NO_DROP);
            }
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            boolean blockWasPlaced = false;
            if (this.shouldAttemptBlockPlacement) {
                blockWasPlaced = placeBlock(mob);
            }
            if (blockWasPlaced) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }

    public static class AnyBlock extends CopperGolemBlockPlacementBehavior {
        public AnyBlock() {
            super(AnyBlock::isValidItemToHold);
        }

        public static boolean isValidItemToHold(ItemStack itemStack) {
            return canPlaceItemStackAsBlock(itemStack);
        }

        @Override
        protected boolean isMarkerBlockState(BlockState blockState) {
            return blockState.is(BlockTags.COPPER);
        }
    }

    public static class VegetationBlock extends CopperGolemBlockPlacementBehavior {
        public VegetationBlock() {
            super(AnyBlock::isValidItemToHold);
        }

        public static boolean isValidItemToHold(ItemStack itemStack) {
            return canPlaceItemStackAsBlock(itemStack) && isVegetationItem(itemStack);
        }

        private static boolean isVegetationItem(ItemStack itemStack) {
            if (itemStack.is(ItemTags.FLOWERS) || itemStack.is(ItemTags.SMALL_FLOWERS) || itemStack.is(ItemTags.SAPLINGS) || itemStack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
                return true;
            }
            Block block = Block.byItem(itemStack.getItem());
            return block instanceof net.minecraft.world.level.block.VegetationBlock || block instanceof CactusBlock || block instanceof BonemealableBlock;
        }

        @Override
        protected boolean isMarkerBlockState(BlockState blockState) {
            return !blockState.isAir();
        }

        @Override
        protected Optional<BlockPlacementTarget> getBlockPlacementTarget(Level level, PathfinderMob mob, BlockPos markerBlockPos) {
            Direction placeFromBlockFace = Direction.UP;

            ItemStack handItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            if (handItemStack.getItem() instanceof BlockItem blockItem) {

                BlockPos placementBlockPos = markerBlockPos.relative(placeFromBlockFace);
                BlockState blockState = blockItem.getBlock().defaultBlockState();
                if (blockState.canSurvive(level, placementBlockPos)) {

                    // Avoid placing saplings while inside the same block as them to prevent suffocation
                    if (!blockState.is(BlockTags.SAPLINGS) || mob.distanceToSqr(placementBlockPos.getCenter()) >= 1.44f) {
                        return Optional.of(new BlockPlacementTarget(markerBlockPos, placeFromBlockFace, false));
                    }
                }
            }
            return Optional.empty();
        }
    }
}
