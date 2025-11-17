package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public abstract class CopperGolemBaseBehavior extends Behavior<PathfinderMob> {
    public static final int DEFAULT_TARGET_INTERACTION_TIME = 60;
    private static final int IDLE_COOLDOWN = 140;
    private static final int VISITED_POSITIONS_MEMORY_TIME = 6000;
    private static final int MAX_VISITED_POSITIONS = 10;
    private static final int MAX_UNREACHABLE_POSITIONS = 50;
    private static final int VISITED_ENTITIES_MEMORY_TIME = 6000;
    private static final int MAX_VISITED_ENTITIES = 20;
    private static final int MAX_UNREACHABLE_ENTITIES = 100;
    private static final int PASSENGER_MOB_TARGET_SEARCH_DISTANCE = 1;
    private static final double CLOSE_ENOUGH_TO_START_QUEUING_DISTANCE = 3.0F;
    private static final double CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_DISTANCE = 0.5F;
    private static final double CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_PATH_END_DISTANCE = 1.0F;
    private static final double CLOSE_ENOUGH_TO_CONTINUE_INTERACTING_WITH_TARGET = 2.0F;

    protected final float speedModifier;
    protected final int horizontalSearchDistance;
    protected final int verticalSearchDistance;
    @Nullable protected GolemInteractionTarget target;
    protected CurrentTaskState state;
    protected int ticksSinceReachingTarget = 0;

    protected CopperGolemBaseBehavior(float speedModifier, int horizontalSearchDistance, int verticalSearchDistance) {
        super(ImmutableMap.of(MemoryModuleType.VISITED_BLOCK_POSITIONS, MemoryStatus.REGISTERED, MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS, MemoryStatus.REGISTERED, MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.horizontalSearchDistance = horizontalSearchDistance;
        this.verticalSearchDistance = verticalSearchDistance;
        this.state = CurrentTaskState.TRAVELLING;
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob mob, long startTimestamp) {
        if (mob.getNavigation() instanceof GroundPathNavigation groundPathNavigation) {
            groundPathNavigation.setCanPathToTargetsBelowSurface(true);
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob mob) {
        return !mob.isLeashed();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, PathfinderMob mob, long currentTimestamp) {
        return mob.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty() && !mob.isPanicking() && !mob.isLeashed();
    }

    @Override
    protected boolean timedOut(long currentTimestamp) {
        return false;
    }

    protected abstract Optional<GolemInteractionTarget> getNewInteractionTarget(ServerLevel level, PathfinderMob mob);

    @Override
    protected void tick(ServerLevel level, PathfinderMob mob, long currentTimestamp) {
        if (!hasValidTarget(level, mob)) {
            stopTargetingCurrentTarget(mob);
            Optional<GolemInteractionTarget> newTarget = getNewInteractionTarget(level, mob);
            if (newTarget.isPresent()) {
                this.target = newTarget.get();

                resetInteractionEffects(mob);
                this.state = CurrentTaskState.TRAVELLING;
                this.ticksSinceReachingTarget = 0;
                this.target.markTargetVisited(level, mob);
            } else {
                enterCooldownAfterNoMatchingTargetFound(mob);
                stop(level, mob, currentTimestamp);
            }
        } else if (this.target != null) {
            if (this.state == CurrentTaskState.QUEUING) {
                if (!this.target.isTargetCurrentlyOccupied(level)) {
                    this.state = CurrentTaskState.TRAVELLING;
                    setLookAndWalkTarget(mob);
                }
            }

            if (this.state == CurrentTaskState.TRAVELLING) {
                if (isWithinTargetDistance(CLOSE_ENOUGH_TO_START_QUEUING_DISTANCE, level, mob) && this.target.isTargetCurrentlyOccupied(level)) {
                    stopInPlace(mob);
                    this.state = CurrentTaskState.QUEUING;
                } else if (isWithinTargetDistance(getInteractionRange(mob), level, mob)) {
                    this.target.startTargetInteraction(level, mob);
                    this.state = CurrentTaskState.INTERACTING;
                } else {
                    setLookAndWalkTarget(mob);
                }
            }

            if (this.state == CurrentTaskState.INTERACTING) {
                if (!isWithinTargetDistance(CLOSE_ENOUGH_TO_CONTINUE_INTERACTING_WITH_TARGET, level, mob)) {
                    this.target.cancelTargetInteraction(level, mob, this.ticksSinceReachingTarget);
                    resetInteractionEffects(mob);
                    this.state = CurrentTaskState.TRAVELLING;
                    this.ticksSinceReachingTarget = 0;
                } else {
                    ++this.ticksSinceReachingTarget;
                    mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, this.target.getTargetPositionTracker());
                    stopInPlace(mob);
                    this.target.tickTargetInteraction(level, mob, this.ticksSinceReachingTarget);

                    if (this.ticksSinceReachingTarget >= this.target.getTargetInteractionTime(level, mob)) {
                        this.target.finishTargetInteraction(level, mob);
                        resetInteractionEffects(mob);
                        this.state = CurrentTaskState.TRAVELLING;
                        this.ticksSinceReachingTarget = 0;
                    }
                }
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, PathfinderMob mob, long currentTimestamp) {
        resetInteractionEffects(mob);
        if (mob.getNavigation() instanceof GroundPathNavigation groundPathNavigation) {
            groundPathNavigation.setCanPathToTargetsBelowSurface(false);
        }
    }

    private static Vec3 setMiddleYPosition(PathfinderMob mob, Vec3 vec3) {
        return vec3.add(0.0f, mob.getBoundingBox().getYsize() / 2.0f, 0.0f);
    }

    private static Vec3 getCenterPos(PathfinderMob mob) {
        return setMiddleYPosition(mob, mob.position());
    }

    private static Vec3 getPositionToReachTargetFrom(@Nullable Path path, PathfinderMob pathfinderMob) {
        boolean isInvalidPath = path == null || path.getEndNode() == null;
        Vec3 targetPosition = isInvalidPath ? pathfinderMob.position() : path.getEndNode().asBlockPos().getBottomCenter();
        return setMiddleYPosition(pathfinderMob, targetPosition);
    }

    private static double getInteractionRange(PathfinderMob mob) {
        boolean hasFinishedPath = mob.getNavigation().getPath() != null && mob.getNavigation().getPath().isDone();
        return hasFinishedPath ? CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_PATH_END_DISTANCE : CLOSE_ENOUGH_TO_START_INTERACTING_WITH_TARGET_DISTANCE;
    }

    private int getHorizontalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? PASSENGER_MOB_TARGET_SEARCH_DISTANCE : this.horizontalSearchDistance;
    }

    private int getVerticalSearchDistance(PathfinderMob mob) {
        return mob.isPassenger() ? PASSENGER_MOB_TARGET_SEARCH_DISTANCE : this.verticalSearchDistance;
    }

    public AABB getTargetSearchArea(PathfinderMob mob) {
        int horizontalSearchDistance = getHorizontalSearchDistance(mob);
        return (new AABB(mob.blockPosition())).inflate(horizontalSearchDistance, getVerticalSearchDistance(mob), horizontalSearchDistance);
    }

    protected void resetInteractionEffects(PathfinderMob mob) {
        if (mob instanceof CopperGolem copperGolem) {
            copperGolem.clearOpenedChestPos();
            copperGolem.setState(CopperGolemState.IDLE);
        }
    }

    private boolean isWithinTargetDistance(double distance, Level level, PathfinderMob mob) {
        return isPositionWithinTargetDistance(distance, level, mob, getCenterPos(mob));
    }

    private boolean isPositionWithinTargetDistance(double distance, Level level, PathfinderMob mob, Vec3 position) {
        AABB entityBoundingBox = mob.getBoundingBox();
        AABB centeredBoundingBox = AABB.ofSize(position, entityBoundingBox.getXsize(), entityBoundingBox.getYsize(), entityBoundingBox.getZsize());
        return this.target != null && this.target.getTargetBoundingBox(level).inflate(distance, 0.5F, distance).intersects(centeredBoundingBox);
    }

    private void setLookAndWalkTarget(PathfinderMob mob) {
        if (this.target != null) {
            PositionTracker positionTracker = this.target.getTargetPositionTracker();
            mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, positionTracker);
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(positionTracker, this.speedModifier, 0));
        }
    }

    private void stopInPlace(PathfinderMob pathfinderMob) {
        pathfinderMob.getNavigation().stop();
        pathfinderMob.setXxa(0.0F);
        pathfinderMob.setYya(0.0F);
        pathfinderMob.setSpeed(0.0F);
        pathfinderMob.setDeltaMovement(0.0F, pathfinderMob.getDeltaMovement().y, 0.0F);
    }

    public void stopTargetingCurrentTarget(PathfinderMob mob) {
        this.ticksSinceReachingTarget = 0;
        this.target = null;
        mob.getNavigation().stop();
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    public void clearMemoriesAfterMatchingTargetFound(PathfinderMob mob) {
        stopTargetingCurrentTarget(mob);
        mob.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        mob.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
        mob.getBrain().eraseMemory(ExtraGolemsMemoryModuleTypes.VISITED_ENTITIES);
        mob.getBrain().eraseMemory(ExtraGolemsMemoryModuleTypes.UNREACHABLE_TRANSPORT_ENTITIES);
    }

    private void enterCooldownAfterNoMatchingTargetFound(PathfinderMob mob) {
        stopTargetingCurrentTarget(mob);
        mob.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, IDLE_COOLDOWN);
        mob.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        mob.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
        mob.getBrain().eraseMemory(ExtraGolemsMemoryModuleTypes.VISITED_ENTITIES);
        mob.getBrain().eraseMemory(ExtraGolemsMemoryModuleTypes.UNREACHABLE_TRANSPORT_ENTITIES);
    }

    private boolean hasValidTarget(Level level, PathfinderMob mob) {
        if (this.target != null && this.target.isTargetStillAvailableAndRelevant(level, mob)) {
            if (this.state != CurrentTaskState.TRAVELLING) {
                return true;
            }
            if (hasValidTravellingPath(level, mob)) {
                return true;
            }
            this.target.markTargetAsUnreachable(level, mob);
        }
        return false;
    }

    private boolean hasValidTravellingPath(Level level, PathfinderMob mob) {
        if (this.target != null) {
            BlockPos targetBlockPos = this.target.getTargetPositionTracker().currentBlockPosition();
            Path path = mob.getNavigation().getPath() == null ? mob.getNavigation().createPath(targetBlockPos, 0) : mob.getNavigation().getPath();
            Vec3 destinationPosition = getPositionToReachTargetFrom(path, mob);
            boolean isPositionReachable = isPositionWithinTargetDistance(getInteractionRange(mob), level, mob, destinationPosition);

            boolean isPathfindingTemporarilyUnavailable = path == null && !isPositionReachable;
            return isPathfindingTemporarilyUnavailable || isPositionReachable && this.target.canSeeTargetFromPosition(level, mob, destinationPosition);
        }
        return false;
    }

    public static Set<GlobalPos> getVisitedPositions(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS).orElse(Set.of());
    }

    public static Set<GlobalPos> getUnreachablePositions(PathfinderMob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS).orElse(Set.of());
    }

    public void setVisitedBlockPos(Level level, PathfinderMob mob, BlockPos blockPos) {
        Set<GlobalPos> visitedPositions = new HashSet<>(getVisitedPositions(mob));
        visitedPositions.add(new GlobalPos(level.dimension(), blockPos));
        if (visitedPositions.size() > MAX_VISITED_POSITIONS) {
            enterCooldownAfterNoMatchingTargetFound(mob);
        } else {
            mob.getBrain().setMemoryWithExpiry(MemoryModuleType.VISITED_BLOCK_POSITIONS, visitedPositions, VISITED_POSITIONS_MEMORY_TIME);
        }
    }

    public void markVisitedBlockPosAsUnreachable(Level level, PathfinderMob pathfinderMob, BlockPos blockPos) {
        Set<GlobalPos> visitedPositions = new HashSet<>(getVisitedPositions(pathfinderMob));
        visitedPositions.remove(new GlobalPos(level.dimension(), blockPos));
        Set<GlobalPos> unreachablePositions = new HashSet<>(getUnreachablePositions(pathfinderMob));
        unreachablePositions.add(new GlobalPos(level.dimension(), blockPos));
        if (unreachablePositions.size() > MAX_UNREACHABLE_POSITIONS) {
            enterCooldownAfterNoMatchingTargetFound(pathfinderMob);
        } else {
            pathfinderMob.getBrain().setMemoryWithExpiry(MemoryModuleType.VISITED_BLOCK_POSITIONS, visitedPositions, VISITED_POSITIONS_MEMORY_TIME);
            pathfinderMob.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS, unreachablePositions, VISITED_POSITIONS_MEMORY_TIME);
        }
    }

    public static Set<UUID> getVisitedEntities(PathfinderMob mob) {
        return mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.VISITED_ENTITIES).orElse(Set.of());
    }

    public static Set<UUID> getUnreachableEntities(PathfinderMob mob) {
        return mob.getBrain().getMemory(ExtraGolemsMemoryModuleTypes.UNREACHABLE_TRANSPORT_ENTITIES).orElse(Set.of());
    }

    public void setVisitedEntity(PathfinderMob mob, Entity entity) {
        Set<UUID> visitedEntities = new HashSet<>(getVisitedEntities(mob));
        visitedEntities.add(entity.getUUID());
        if (visitedEntities.size() > MAX_VISITED_ENTITIES) {
            enterCooldownAfterNoMatchingTargetFound(mob);
        } else {
            mob.getBrain().setMemoryWithExpiry(ExtraGolemsMemoryModuleTypes.VISITED_ENTITIES, visitedEntities, VISITED_ENTITIES_MEMORY_TIME);
        }
    }

    public void markVisitedEntityAsUnreachable(PathfinderMob mob, Entity entity) {
        Set<UUID> visitedEntities = new HashSet<>(getVisitedEntities(mob));
        visitedEntities.remove(entity.getUUID());
        Set<UUID> unreachableEntities = new HashSet<>(getUnreachableEntities(mob));
        unreachableEntities.add(entity.getUUID());
        if (unreachableEntities.size() > MAX_UNREACHABLE_ENTITIES) {
            enterCooldownAfterNoMatchingTargetFound(mob);
        } else {
            mob.getBrain().setMemoryWithExpiry(ExtraGolemsMemoryModuleTypes.VISITED_ENTITIES, visitedEntities, VISITED_ENTITIES_MEMORY_TIME);
            mob.getBrain().setMemoryWithExpiry(ExtraGolemsMemoryModuleTypes.UNREACHABLE_TRANSPORT_ENTITIES, unreachableEntities, VISITED_ENTITIES_MEMORY_TIME);
        }
    }

    public static Container getBlockContainerAt(Level level, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity, boolean allowWorldContainerHolders) {
        if (allowWorldContainerHolders && blockState.getBlock() instanceof WorldlyContainerHolder worldlyContainerHolder) {
            return worldlyContainerHolder.getContainer(blockState, level, blockPos);
        }
        if (blockEntity instanceof Container container) {
            if (blockEntity instanceof ChestBlockEntity && blockState.getBlock() instanceof ChestBlock chestBlock) {
                return ChestBlock.getContainer(chestBlock, blockState, level, blockPos, true);
            }
            return container;
        }
        return null;
    }

    public Optional<BlockPos> findClosestContainerInteractionTarget(ServerLevel level, PathfinderMob mob, Predicate<BlockState> containerTypePredicate) {
        AABB containerSearchBoundingBox = getTargetSearchArea(mob);
        Set<GlobalPos> visitedPositions = getVisitedPositions(mob);
        Set<GlobalPos> unreachablePositions = getUnreachablePositions(mob);

        List<ChunkPos> overlappingChunkPositions = ChunkPos.rangeClosed(new ChunkPos(mob.blockPosition()), Math.floorDiv(this.getHorizontalSearchDistance(mob), 16) + 1).toList();
        double closestInteractableContainerDistanceSq = Double.MAX_VALUE;
        Optional<BlockPos> resultContainerPosition = Optional.empty();

        for (ChunkPos chunkPos : overlappingChunkPositions) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof Container && containerSearchBoundingBox.contains(new Vec3(blockEntity.getBlockPos())) && containerTypePredicate.test(blockEntity.getBlockState())) {
                        double distanceToContainerSquared = blockEntity.getBlockPos().distToCenterSqr(mob.position());
                        if (distanceToContainerSquared < closestInteractableContainerDistanceSq) {
                            // Skip locked chests that cannot be interacted with
                            if (blockEntity instanceof ChestBlockEntity chestBlockEntity && chestBlockEntity.isLocked()) {
                                continue;
                            }

                            // Skip containers that we have already visited
                            GlobalPos containerGlobalPos = new GlobalPos(level.dimension(), blockEntity.getBlockPos());
                            if (visitedPositions.contains(containerGlobalPos) || unreachablePositions.contains(containerGlobalPos)) {
                                continue;
                            }

                            // If this is a connected chest block, skip it if we have visited its connected chest as well
                            if (blockEntity.getBlockState().getBlock() instanceof ChestBlock) {
                                BlockPos connectedContainerBlockPos = ChestBlock.getConnectedBlockPos(blockEntity.getBlockPos(), blockEntity.getBlockState());
                                if (level.getBlockState(connectedContainerBlockPos).getBlock() instanceof ChestBlock) {
                                    GlobalPos connectedContainerGlobalPos = new GlobalPos(level.dimension(), connectedContainerBlockPos);
                                    if (visitedPositions.contains(connectedContainerGlobalPos) || unreachablePositions.contains(connectedContainerGlobalPos)) {
                                        continue;
                                    }
                                }
                            }

                            resultContainerPosition = Optional.of(blockEntity.getBlockPos());
                            closestInteractableContainerDistanceSq = distanceToContainerSquared;
                        }
                    }
                }
            }
        }
        return resultContainerPosition;
    }

    protected enum CurrentTaskState {
        TRAVELLING,
        QUEUING,
        INTERACTING
    }

    public interface GolemInteractionTarget {
        PositionTracker getTargetPositionTracker();
        void markTargetVisited(Level level, PathfinderMob mob);
        void markTargetAsUnreachable(Level level, PathfinderMob mob);
        void finishTargetInteraction(Level level, PathfinderMob mob);

        default int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return DEFAULT_TARGET_INTERACTION_TIME;
        }
        default void startTargetInteraction(Level level, PathfinderMob mob) {
        }
        default void cancelTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
        }
        default void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
        }
        default AABB getTargetBoundingBox(Level level) {
            return new AABB(this.getTargetPositionTracker().currentBlockPosition());
        }
        default boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            return true;
        }
        default boolean isTargetCurrentlyOccupied(Level level) {
            return false;
        }
        default boolean canSeeTargetFromPosition(Level level, PathfinderMob mob, Vec3 position) {
            return true;
        }
    }

    public abstract static class BaseEntityInteractionTarget implements GolemInteractionTarget {
        protected final CopperGolemBaseBehavior owner;
        protected final Entity entity;
        protected final EntityTracker entityTracker;

        protected BaseEntityInteractionTarget(CopperGolemBaseBehavior owner, Entity entity) {
            this.owner = owner;
            this.entity = entity;
            this.entityTracker = new EntityTracker(this.entity, true);
        }

        @Override
        public PositionTracker getTargetPositionTracker() {
            return this.entityTracker;
        }

        @Override
        public AABB getTargetBoundingBox(Level level) {
            return this.entity.getBoundingBox();
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            return this.entity.isAlive();
        }

        @Override
        public void markTargetVisited(Level level, PathfinderMob mob) {
            this.owner.setVisitedEntity(mob, this.entity);
        }

        @Override
        public void markTargetAsUnreachable(Level level, PathfinderMob mob) {
            this.owner.markVisitedEntityAsUnreachable(mob, this.entity);
        }

        @Override
        public boolean canSeeTargetFromPosition(Level level, PathfinderMob mob, Vec3 position) {
            Vec3 targetEyePosition = this.entityTracker.currentPosition();
            return level.clip(new ClipContext(position, targetEyePosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
        }
    }

    public abstract static class BlockInteractionTarget implements GolemInteractionTarget {
        protected final CopperGolemBaseBehavior owner;
        protected final BlockPos blockPos;
        protected final BlockPosTracker blockPosTracker;
        protected final BlockState blockState;
        protected final BlockEntity blockEntity;

        protected BlockInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            this.owner = owner;
            this.blockPos = blockPos;
            this.blockPosTracker = new BlockPosTracker(this.blockPos);
            this.blockState = level.getBlockState(blockPos);
            this.blockEntity = level.getBlockEntity(blockPos);
        }

        @Override
        public PositionTracker getTargetPositionTracker() {
            return this.blockPosTracker;
        }

        @Override
        public AABB getTargetBoundingBox(Level level) {
            VoxelShape targetBlockShape = level.getBlockState(this.blockPos).getCollisionShape(level, this.blockPos);
            return (targetBlockShape.isEmpty() ? Shapes.block() : targetBlockShape).bounds().move(this.blockPos);
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            BlockState newBlockState = level.getBlockState(this.blockPos);
            BlockEntity newBlockEntity = level.getBlockEntity(this.blockPos);
            return newBlockState == this.blockState && newBlockEntity == this.blockEntity;
        }

        protected void getAllConnectedBlocks(Level level, List<BlockPos> connectedBlocks) {
            connectedBlocks.add(this.blockPos);
        }

        @Override
        public void markTargetVisited(Level level, PathfinderMob mob) {
            List<BlockPos> connectedBlocks = Lists.newArrayList();
            getAllConnectedBlocks(level, connectedBlocks);
            for (BlockPos connectedBlockPos : connectedBlocks) {
                this.owner.setVisitedBlockPos(level, mob, connectedBlockPos);
            }
        }

        @Override
        public void markTargetAsUnreachable(Level level, PathfinderMob mob) {
            List<BlockPos> connectedBlocks = Lists.newArrayList();
            getAllConnectedBlocks(level, connectedBlocks);
            for (BlockPos connectedBlockPos : connectedBlocks) {
                this.owner.markVisitedBlockPosAsUnreachable(level, mob, connectedBlockPos);
            }
        }

        @Override
        public boolean canSeeTargetFromPosition(Level level, PathfinderMob mob, Vec3 position) {
            Vec3 blockCenterPosition = this.blockPos.getCenter();
            return Direction.stream().map((direction) -> blockCenterPosition.add(direction.getUnitVec3().scale(0.5f)))
                    .map((resultPosition) -> level.clip(new ClipContext(position, resultPosition, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)))
                    .anyMatch((blockHitResult) -> blockHitResult.getType() == HitResult.Type.MISS || (blockHitResult.getType() == HitResult.Type.BLOCK && blockHitResult.getBlockPos().equals(blockPos)));
        }
    }

    public abstract static class ContainerInteractionTarget extends BlockInteractionTarget {
        private static final int TICK_TO_START_ON_REACHED_INTERACTION = 1;
        private static final int TICK_TO_PLAY_ON_REACHED_SOUND = 9;

        protected boolean shouldPerformContainerBehavior;
        protected @Nullable CopperGolemState interactionGolemState;
        protected @Nullable SoundEvent interactionSoundEvent;

        protected ContainerInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
        }

        protected boolean checkContainerBehavior(Level level, PathfinderMob mob, Container container) {
            return true;
        }
        protected abstract boolean performContainerBehavior(Level level, PathfinderMob mob, Container container);

        @Override
        public void startTargetInteraction(Level level, PathfinderMob mob) {
            super.startTargetInteraction(level, mob);

            this.shouldPerformContainerBehavior = false;
            Container container = getBlockContainerAt(level, this.blockPos, this.blockState, this.blockEntity, true);
            if (container != null) {
                this.shouldPerformContainerBehavior = checkContainerBehavior(level, mob, container);
            }
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            Container container = getBlockContainerAt(level, this.blockPos, this.blockState, this.blockEntity, true);
            if (mob instanceof CopperGolem copperGolem && container != null) {
                if (ticksSinceInteractionStart == TICK_TO_START_ON_REACHED_INTERACTION) {
                    container.startOpen(copperGolem);
                    copperGolem.setOpenedChestPos(this.blockPos);
                    if (this.interactionGolemState != null) {
                        copperGolem.setState(this.interactionGolemState);
                    }
                }
            }

            if (ticksSinceInteractionStart == TICK_TO_PLAY_ON_REACHED_SOUND && this.interactionSoundEvent != null) {
                mob.playSound(this.interactionSoundEvent);
            }
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            Container container = getBlockContainerAt(level, this.blockPos, this.blockState, this.blockEntity, true);
            if (mob instanceof CopperGolem copperGolem && container != null) {
                if (container.getEntitiesWithContainerOpen().contains(mob)) {
                    container.stopOpen(copperGolem);
                }
                copperGolem.clearOpenedChestPos();
            }

            boolean completedTargetBehavior = false;
            if (this.shouldPerformContainerBehavior && container != null) {
                completedTargetBehavior = performContainerBehavior(level, mob, container);
            }
            if (completedTargetBehavior) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            return super.isTargetStillAvailableAndRelevant(level, mob) &&
                    !(this.blockState.getBlock() instanceof ChestBlock && ChestBlock.isChestBlockedAt(level, this.blockPos));
        }

        @Override
        protected void getAllConnectedBlocks(Level level, List<BlockPos> connectedBlocks) {
            super.getAllConnectedBlocks(level, connectedBlocks);
            if (this.blockState.getBlock() instanceof ChestBlock) {
                BlockPos connectedBlockPos = ChestBlock.getConnectedBlockPos(this.blockPos, this.blockState);
                BlockState connectedBlockState = level.getBlockState(connectedBlockPos);
                if (connectedBlockState.getBlock() instanceof ChestBlock) {
                    connectedBlocks.add(connectedBlockPos);
                }
            }
        }

        @Override
        public boolean isTargetCurrentlyOccupied(Level level) {
            Container container = getBlockContainerAt(level, this.blockPos, this.blockState, this.blockEntity, true);
            if (container instanceof ChestBlockEntity && this.blockState.getBlock() instanceof ChestBlock) {
                BlockPos connectedBlockPos = ChestBlock.getConnectedBlockPos(this.blockPos, this.blockState);
                BlockEntity connectedBlockEntity = level.getBlockEntity(connectedBlockPos);
                if (connectedBlockEntity instanceof ChestBlockEntity connectedChestBlockEntity && !connectedChestBlockEntity.getEntitiesWithContainerOpen().isEmpty()) {
                    return true;
                }
            }
            return container != null && !container.getEntitiesWithContainerOpen().isEmpty();
        }
    }

    public static class ContainerPickupInteractionTarget extends ContainerInteractionTarget {
        private static final int DEFAULT_TRANSPORTED_ITEM_MAX_STACK_SIZE = 16;
        protected int transportedItemMaxStackSize;

        public ContainerPickupInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.transportedItemMaxStackSize = DEFAULT_TRANSPORTED_ITEM_MAX_STACK_SIZE;
        }

        protected int getMaxTransportedItemStackSize(ItemStack itemStack) {
            return !itemStack.isEmpty() ? Math.min(transportedItemMaxStackSize, itemStack.getMaxStackSize()) : transportedItemMaxStackSize;
        }

        protected boolean shouldPickupItem(Level level, PathfinderMob mob, ItemStack itemStack) {
            return true;
        }

        private boolean checkCanPickupItemsFromContainer(Level level, PathfinderMob mob, Container container) {
            ItemStack currentItem = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!currentItem.isEmpty() && currentItem.getCount() >= getMaxTransportedItemStackSize(currentItem)) {
                return false;
            }

            for (int slotIndex = 0; slotIndex < container.getContainerSize(); slotIndex++) {
                ItemStack stackInSlot = container.getItem(slotIndex);
                if (!stackInSlot.isEmpty() && shouldPickupItem(level, mob, stackInSlot) && (
                        currentItem.isEmpty() || ItemStack.isSameItemSameComponents(stackInSlot, currentItem))) {
                    return true;
                }
            }
            return false;
        }

        private ItemStack pickupItemFromContainer(Level level, PathfinderMob mob, Container container) {
            ItemStack resultItemStack = mob.getItemBySlot(EquipmentSlot.MAINHAND).copy();
            if (!resultItemStack.isEmpty() && resultItemStack.getCount() >= getMaxTransportedItemStackSize(resultItemStack)) {
                return resultItemStack;
            }

            for (int slotIndex = 0; slotIndex < container.getContainerSize(); slotIndex++) {
                ItemStack stackInSlot = container.getItem(slotIndex);
                if (!stackInSlot.isEmpty() && shouldPickupItem(level, mob, stackInSlot) && (
                        resultItemStack.isEmpty() || ItemStack.isSameItemSameComponents(stackInSlot, resultItemStack))) {
                    int maxItemsToRemove = Math.max(getMaxTransportedItemStackSize(stackInSlot) - resultItemStack.getCount(), 0);
                    int itemsToRemove = Math.min(stackInSlot.getCount(), maxItemsToRemove);

                    if (itemsToRemove > 0) {
                        ItemStack removedItemStack = container.removeItem(slotIndex, itemsToRemove);
                        if (resultItemStack.isEmpty()) {
                            resultItemStack = removedItemStack;
                        } else {
                            resultItemStack.grow(removedItemStack.getCount());
                        }
                        if (resultItemStack.getCount() >= getMaxTransportedItemStackSize(removedItemStack)) {
                            return resultItemStack;
                        }
                    }
                }
            }
            return resultItemStack;
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack currentItem = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            return super.isTargetStillAvailableAndRelevant(level, mob) && (
                    currentItem.isEmpty() || currentItem.getCount() < getMaxTransportedItemStackSize(currentItem));
        }

        @Override
        protected boolean checkContainerBehavior(Level level, PathfinderMob mob, Container container) {
            if (checkCanPickupItemsFromContainer(level, mob, container)) {
                this.interactionGolemState = CopperGolemState.GETTING_ITEM;
                this.interactionSoundEvent = SoundEvents.COPPER_GOLEM_ITEM_GET;
                return true;
            }
            this.interactionGolemState = CopperGolemState.GETTING_NO_ITEM;
            this.interactionSoundEvent = SoundEvents.COPPER_GOLEM_ITEM_NO_GET;
            return false;
        }

        @Override
        protected boolean performContainerBehavior(Level level, PathfinderMob mob, Container container) {
            if (checkCanPickupItemsFromContainer(level, mob, container)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, pickupItemFromContainer(level, mob, container));
                mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                container.setChanged();
                return true;
            }
            return false;
        }
    }

    public static class ContainerDropInteractionTarget extends ContainerInteractionTarget {
        protected boolean retainSingleItem;

        public ContainerDropInteractionTarget(CopperGolemBaseBehavior owner, Level level, BlockPos blockPos) {
            super(owner, level, blockPos);
            this.retainSingleItem = false;
        }

        public static boolean containerContainsSameItemType(Container container, ItemStack itemStack) {
            for (int slotIndex = 0; slotIndex < container.getContainerSize(); slotIndex++) {
                ItemStack stackInSlot = container.getItem(slotIndex);
                if (!stackInSlot.isEmpty() && ItemStack.isSameItem(stackInSlot, itemStack)) {
                    return true;
                }
            }
            return false;
        }

        protected boolean shouldDropItemIntoContainer(Level level, PathfinderMob mob, Container container, ItemStack itemStack) {
            return true;
        }

        protected ItemStack getDropSourceItemStack(Level level, PathfinderMob mob) {
            return mob.getItemBySlot(EquipmentSlot.MAINHAND);
        }

        protected void setDropSourceItemStack(Level level, PathfinderMob mob, ItemStack itemStack) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
            mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        }

        private static boolean hasFreeSpaceInContainer(Container container, ItemStack itemStack) {
            for (int slotIndex = 0; slotIndex < container.getContainerSize(); slotIndex++) {
                ItemStack stackInSlot = container.getItem(slotIndex);

                if (stackInSlot.isEmpty()) {
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(stackInSlot, itemStack) && stackInSlot.getCount() < stackInSlot.getMaxStackSize()) {
                    return true;
                }
            }
            return false;
        }

        private static ItemStack dropItemsIntoContainer(Container container, ItemStack itemStack) {
            ItemStack remainderItemStack = itemStack.copy();
            if (remainderItemStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            for (int slotIndex = 0; slotIndex < container.getContainerSize(); slotIndex++) {
                ItemStack stackInSlot = container.getItem(slotIndex);

                if (stackInSlot.isEmpty()) {
                    container.setItem(slotIndex, remainderItemStack);
                    return ItemStack.EMPTY;
                }

                if (ItemStack.isSameItemSameComponents(stackInSlot, remainderItemStack) && stackInSlot.getCount() < stackInSlot.getMaxStackSize()) {
                    int freeSpaceInSlot = stackInSlot.getMaxStackSize() - stackInSlot.getCount();
                    int itemsMovedToSlot = Math.min(freeSpaceInSlot, remainderItemStack.getCount());

                    stackInSlot.grow(itemsMovedToSlot);
                    remainderItemStack.shrink(itemsMovedToSlot);
                    container.setItem(slotIndex, stackInSlot);

                    if (remainderItemStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            return remainderItemStack;
        }

        private boolean checkCanDropItemIntoContainer(Level level, PathfinderMob mob, Container container) {
            ItemStack currentItem = getDropSourceItemStack(level, mob);
            if (currentItem.isEmpty() || (this.retainSingleItem && currentItem.isStackable() && currentItem.getCount() == 1) ||
                    !shouldDropItemIntoContainer(level, mob, container, currentItem)) {
                return false;
            }
            return hasFreeSpaceInContainer(container, currentItem);
        }

        @Override
        public boolean isTargetStillAvailableAndRelevant(Level level, PathfinderMob mob) {
            ItemStack currentItem = getDropSourceItemStack(level, mob);
            return super.isTargetStillAvailableAndRelevant(level, mob) && !currentItem.isEmpty() &&
                    !(this.retainSingleItem && currentItem.isStackable() && currentItem.getCount() == 1);
        }

        @Override
        protected boolean checkContainerBehavior(Level level, PathfinderMob mob, Container container) {
            if (checkCanDropItemIntoContainer(level, mob, container)) {
                this.interactionGolemState = CopperGolemState.GETTING_ITEM;
                this.interactionSoundEvent = SoundEvents.COPPER_GOLEM_ITEM_GET;
                return true;
            }
            this.interactionGolemState = CopperGolemState.GETTING_NO_ITEM;
            this.interactionSoundEvent = SoundEvents.COPPER_GOLEM_ITEM_NO_GET;
            return false;
        }

        @Override
        protected boolean performContainerBehavior(Level level, PathfinderMob mob, Container container) {
            if (checkCanDropItemIntoContainer(level, mob, container)) {
                ItemStack currentItemCopy = getDropSourceItemStack(level, mob).copy();
                if (this.retainSingleItem && currentItemCopy.isStackable()) {
                    currentItemCopy.shrink(1);
                }
                if (!currentItemCopy.isEmpty()) {
                    ItemStack remainderItemStack = dropItemsIntoContainer(container, currentItemCopy);

                    if (this.retainSingleItem && currentItemCopy.isStackable()) {
                        if (remainderItemStack.isEmpty()) {
                            remainderItemStack = currentItemCopy.copyWithCount(1);
                        } else {
                            remainderItemStack.grow(1);
                        }
                    }
                    setDropSourceItemStack(level, mob, remainderItemStack);
                    container.setChanged();
                    return true;
                }
            }
            return false;
        }
    }

    public static abstract class EntityInteractionTarget extends BaseEntityInteractionTarget {
        private static final int DEFAULT_ENTITY_INTERACTION_TIME = 20;
        private static final int TICK_TO_START_ON_REACHED_INTERACTION = 1;
        private static final int TICK_TO_PLAY_ON_REACHED_SOUND = 5;

        protected boolean shouldPerformEntityBehavior;
        protected @Nullable CopperGolemState interactionGolemState;
        protected @Nullable SoundEvent interactionSoundEvent;

        protected EntityInteractionTarget(CopperGolemBaseBehavior owner, Entity entity) {
            super(owner, entity);
        }

        protected boolean checkEntityBehavior(Level level, PathfinderMob mob, Entity entity) {
            return true;
        }
        protected abstract boolean performEntityBehavior(Level level, PathfinderMob mob, Entity entity);

        @Override
        public int getTargetInteractionTime(Level level, PathfinderMob mob) {
            return DEFAULT_ENTITY_INTERACTION_TIME;
        }

        @Override
        public void startTargetInteraction(Level level, PathfinderMob mob) {
            super.startTargetInteraction(level, mob);
            this.shouldPerformEntityBehavior = checkEntityBehavior(level, mob, this.entity);
        }

        @Override
        public void tickTargetInteraction(Level level, PathfinderMob mob, int ticksSinceInteractionStart) {
            super.tickTargetInteraction(level, mob, ticksSinceInteractionStart);

            if (mob instanceof CopperGolem copperGolem) {
                if (ticksSinceInteractionStart == TICK_TO_START_ON_REACHED_INTERACTION) {
                    if (this.interactionGolemState != null) {
                        copperGolem.setState(this.interactionGolemState);
                    }
                }
            }

            if (ticksSinceInteractionStart == TICK_TO_PLAY_ON_REACHED_SOUND && this.interactionSoundEvent != null) {
                mob.playSound(this.interactionSoundEvent);
            }
        }

        @Override
        public void finishTargetInteraction(Level level, PathfinderMob mob) {
            boolean completedTargetBehavior = false;
            if (this.shouldPerformEntityBehavior) {
                completedTargetBehavior = performEntityBehavior(level, mob, this.entity);
            }
            if (completedTargetBehavior) {
                this.owner.clearMemoriesAfterMatchingTargetFound(mob);
            } else {
                this.owner.stopTargetingCurrentTarget(mob);
            }
        }
    }
}
