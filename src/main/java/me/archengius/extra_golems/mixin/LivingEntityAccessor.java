package me.archengius.extra_golems.mixin;

import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("brain")
    void golem_extras$setBrain(Brain<?> newBrain);

    @Accessor("EMPTY_BRAIN")
    static Dynamic<?> golem_extras$getEmptyBrain() {
        throw new AssertionError();
    }
}
