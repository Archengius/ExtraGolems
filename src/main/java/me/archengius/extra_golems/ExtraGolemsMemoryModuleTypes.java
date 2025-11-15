package me.archengius.extra_golems;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ExtraGolemsMemoryModuleTypes {

    public static final MemoryModuleType<Set<UUID>> VISITED_ENTITIES = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "visited_entities"),
            new MemoryModuleType<>(Optional.of(UUIDUtil.CODEC.listOf().xmap(Sets::newHashSet, Lists::newArrayList))));
    public static final MemoryModuleType<Set<UUID>> UNREACHABLE_TRANSPORT_ENTITIES = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "unreachable_transport_entities"),
            new MemoryModuleType<>(Optional.of(UUIDUtil.CODEC.listOf().xmap(Sets::newHashSet, Lists::newArrayList))));

    public static final MemoryModuleType<Animal> PREFERRED_BREED_PARTNER = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "preferred_breed_partner"),
            new MemoryModuleType<>(Optional.empty()));

    public static final MemoryModuleType<Integer> ACCUMULATED_EXPERIENCE = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "accumulated_experience"),
            new MemoryModuleType<>(Optional.of(Codec.INT)));

    public static final MemoryModuleType<LivingEntity> LAST_ATTACKED_TARGET = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "last_attacked_target"),
            new MemoryModuleType<>(Optional.empty()));

    public static final MemoryModuleType<ItemStack> TRADE_RESULT = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "trade_result"),
            new MemoryModuleType<>(Optional.of(ItemStack.CODEC)));
    public static final MemoryModuleType<Unit> REFILL_COST_ITEM_HINT = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "refill_cost_item_hint"),
            new MemoryModuleType<>(Optional.empty()));

    public static final MemoryModuleType<Unit> PICKED_UP_WILDCARD_ITEM = Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "picked_up_wildcard_item"),
            new MemoryModuleType<>(Optional.of(Unit.CODEC)));


    public static void register() {
    }
}
