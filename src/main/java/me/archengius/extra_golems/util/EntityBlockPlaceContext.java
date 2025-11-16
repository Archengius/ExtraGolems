package me.archengius.extra_golems.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

public class EntityBlockPlaceContext extends BlockPlaceContext {

    private final Entity entity;
    private final boolean secondaryUseAction;

    public EntityBlockPlaceContext(Entity entity, BlockPos clickedBlockPos, Direction clickedBlockFace, ItemStack itemStack, boolean replaceClickedBlock, boolean secondaryUseAction) {
        super(entity.level(), null, InteractionHand.MAIN_HAND, itemStack, new BlockHitResult(clickedBlockPos.getCenter().add(clickedBlockFace.getUnitVec3().scale(0.5f)), clickedBlockFace, clickedBlockPos, false));
        this.entity = entity;
        this.secondaryUseAction = secondaryUseAction;
        this.replaceClicked = replaceClickedBlock;
    }

    public Entity getEntity() {
        return this.entity;
    }

    @Override
    public boolean isSecondaryUseActive() {
        return this.secondaryUseAction;
    }

    @Override
    public float getRotation() {
        return this.entity.getYRot();
    }

    @Override
    public Direction getNearestLookingDirection() {
        return Direction.orderedByNearest(this.entity)[0];
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        return Direction.getFacingAxis(this.entity, Direction.Axis.Y);
    }

    @Override
    public Direction[] getNearestLookingDirections() {
        Direction[] directions = Direction.orderedByNearest(this.entity);
        if (!this.replaceClicked) {
            Direction direction = getClickedFace();

            int firstNonOppositeDirectionIndex = 0;
            while (firstNonOppositeDirectionIndex < directions.length && directions[firstNonOppositeDirectionIndex] != direction.getOpposite()) {
                firstNonOppositeDirectionIndex++;
            }

            if (firstNonOppositeDirectionIndex > 0) {
                System.arraycopy(directions, 0, directions, 1, firstNonOppositeDirectionIndex);
                directions[0] = direction.getOpposite();
            }
        }
        return directions;
    }
}
