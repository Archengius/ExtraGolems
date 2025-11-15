package me.archengius.extra_golems;

import com.mojang.serialization.Lifecycle;
import me.archengius.extra_golems.definition.*;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class CopperGolemDefinitions {

    public static final ResourceKey<Registry<GolemDefinition>> REGISTRY_KEY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "golem_definitions"));
    @SuppressWarnings("unchecked")
    public static final Registry<GolemDefinition> REGISTRY = Registry.register((Registry<Registry<GolemDefinition>>) BuiltInRegistries.REGISTRY,
            REGISTRY_KEY, (Registry<GolemDefinition>) new MappedRegistry<>(REGISTRY_KEY, Lifecycle.stable()));

    public static final GolemDefinition ANIMAL_BREEDER = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "animal_breeder"), new AnimalBreederGolemDefinition());
    public static final GolemDefinition EXPERIENCE_COLLECTOR = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "experience_collector"), new ExperienceCollectorGolemDefinition());
    public static final GolemDefinition BUTCHER = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "butcher"), new ButcherGolemDefinition());
    public static final GolemDefinition WARRIOR = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "warrior"), new WarriorGolemDefinition());
    public static final GolemDefinition TRADER = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "trader"), new TraderGolemDefinition());
    public static final GolemDefinition ITEM_COLLECTOR = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "item_collector"), new ItemCollectorGolemDefinition());
    public static final GolemDefinition LUMBERJACK = Registry.register(REGISTRY,
            ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "lumberjack"), new LumberjackGolemDefinition());

    public static void register() {
    }
}
