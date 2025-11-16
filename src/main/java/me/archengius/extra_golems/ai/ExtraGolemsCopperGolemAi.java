package me.archengius.extra_golems.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import me.archengius.extra_golems.definition.GolemDefinition;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Set;

public class ExtraGolemsCopperGolemAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 1.5f;
    public static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0f;
    public static final int TRANSPORT_ITEM_HORIZONTAL_SEARCH_RADIUS = 32;
    public static final int TRANSPORT_ITEM_VERTICAL_SEARCH_RADIUS = 8;

    private static final ImmutableList<SensorType<? extends Sensor<? super CopperGolem>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY);

    private static final ImmutableList<MemoryModuleType<?>> BASE_MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.IS_PANICKING, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.PATH, MemoryModuleType.GAZE_COOLDOWN_TICKS, MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS,
            MemoryModuleType.VISITED_BLOCK_POSITIONS, MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS, MemoryModuleType.DOORS_TO_CLOSE,
            ExtraGolemsMemoryModuleTypes.VISITED_ENTITIES, ExtraGolemsMemoryModuleTypes.UNREACHABLE_TRANSPORT_ENTITIES);

    public static Brain<CopperGolem> makeBrain(GolemDefinition golemDefinition, Dynamic<?> dynamic) {
        return initializeBrain(brainProvider(golemDefinition).makeBrain(dynamic), golemDefinition);
    }

    protected static Brain.Provider<CopperGolem> brainProvider(GolemDefinition golemDefinition) {
        ImmutableList.Builder<MemoryModuleType<?>> resultMemoryTypes = ImmutableList.builder();
        resultMemoryTypes.addAll(BASE_MEMORY_TYPES);
        resultMemoryTypes.addAll(golemDefinition.getAdditionalMemoryTypes());
        return Brain.provider(resultMemoryTypes.build(), SENSOR_TYPES);
    }

    protected static Brain<CopperGolem> initializeBrain(Brain<CopperGolem> brain, GolemDefinition golemDefinition) {
        initCoreActivity(brain, golemDefinition);
        initIdleActivity(brain, golemDefinition);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<CopperGolem> brain, GolemDefinition golemDefinition) {
        ImmutableList.Builder<BehaviorControl<? super CopperGolem>> coreBehaviorsBuilder = ImmutableList.builder();
        if (golemDefinition.shouldAnimalPanic()) {
            coreBehaviorsBuilder.add(new AnimalPanic<>(SPEED_MULTIPLIER_WHEN_PANICKING));
        }
        coreBehaviorsBuilder.addAll(ImmutableList.of(
                new LookAtTargetSink(45, 90),
                new MoveToTargetSink(),
                InteractWithDoor.create(),
                new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS),
                new CountDownCooldownTicks(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS)));

        brain.addActivity(Activity.CORE, 0, coreBehaviorsBuilder.build());
    }

    @SuppressWarnings("deprecation")
    private static void initIdleActivity(Brain<CopperGolem> brain, GolemDefinition golemDefinition) {
        brain.addActivity(Activity.IDLE, ImmutableList.of(
                Pair.of(0, golemDefinition.createCoreBehavior()),
                Pair.of(1, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0F, UniformInt.of(40, 80))),
                Pair.of(2, new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT),
                        ImmutableList.of(Pair.of(RandomStroll.stroll(1.0f, 2, 2), 1), Pair.of(new DoNothing(30, 60), 1))))));
    }
}
