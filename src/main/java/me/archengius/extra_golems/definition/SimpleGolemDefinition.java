package me.archengius.extra_golems.definition;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.ai.CopperGolemBaseBehavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SimpleGolemDefinition implements GolemDefinition {

    private final Optional<Predicate<ItemStack>> behaviorItemPredicate;
    private final Supplier<? extends CopperGolemBaseBehavior> golemBehavior;
    private final ImmutableList<MemoryModuleType<?>> additionalMemoryTypes;

    public SimpleGolemDefinition(Optional<ItemLike> behaviorItemLike, Supplier<? extends CopperGolemBaseBehavior> golemBehavior, ImmutableList<MemoryModuleType<?>> additionalMemoryTypes) {
        this.behaviorItemPredicate = behaviorItemLike.map(ItemLike::asItem).map(behaviorItem -> itemStack -> itemStack.is(behaviorItem));
        this.golemBehavior = golemBehavior;
        this.additionalMemoryTypes = additionalMemoryTypes;
    }

    public SimpleGolemDefinition(Predicate<ItemStack> behaviorPredicate, Supplier<? extends CopperGolemBaseBehavior> golemBehavior, ImmutableList<MemoryModuleType<?>> additionalMemoryTypes) {
        this.behaviorItemPredicate = Optional.of(behaviorPredicate);
        this.golemBehavior = golemBehavior;
        this.additionalMemoryTypes = additionalMemoryTypes;
    }

    @Override
    public boolean isBehaviorItem(ItemStack itemStack) {
        return this.behaviorItemPredicate.isPresent() && this.behaviorItemPredicate.get().test(itemStack);
    }

    @Override
    public ImmutableList<MemoryModuleType<?>> getAdditionalMemoryTypes() {
        return this.additionalMemoryTypes;
    }

    @Override
    public CopperGolemBaseBehavior createCoreBehavior() {
        return this.golemBehavior.get();
    }
}
