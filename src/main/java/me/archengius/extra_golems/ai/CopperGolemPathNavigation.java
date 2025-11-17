package me.archengius.extra_golems.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class CopperGolemPathNavigation extends GroundPathNavigation {

    public CopperGolemPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new CopperGolemPathNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private static class CopperGolemPathNodeEvaluator extends WalkNodeEvaluator {

        @Override
        public void prepare(PathNavigationRegion pathNavigationRegion, Mob mob) {
            super.prepare(pathNavigationRegion, mob);
            this.currentContext = new CopperGolemPathfindingContext(pathNavigationRegion, mob);
        }

        @Override
        public PathType getPathType(Mob mob, BlockPos blockPos) {
            return getPathType(new CopperGolemPathfindingContext(mob.level(), mob), blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
    }

    private static class CopperGolemPathfindingContext extends PathfindingContext {

        private final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        public CopperGolemPathfindingContext(CollisionGetter collisionGetter, Mob mob) {
            super(collisionGetter, mob);
        }

        @Override
        public PathType getPathTypeFromState(int x, int y, int z) {
            BlockPos blockPos = mutableBlockPos.set(x, y, z);
            // Treat saplings as damaging blocks due to the possibility of us suffocating inside the grown tree
            if (getBlockState(blockPos).is(BlockTags.SAPLINGS)) {
                return PathType.DAMAGE_OTHER;
            }
            return super.getPathTypeFromState(x, y, z);
        }
    }
}
