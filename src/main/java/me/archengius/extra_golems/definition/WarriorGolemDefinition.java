package me.archengius.extra_golems.definition;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.ExtraGolemsMod;
import me.archengius.extra_golems.ai.CopperGolemMeleeAttackBehavior;
import me.archengius.extra_golems.ai.ExtraGolemsMemoryModuleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class WarriorGolemDefinition extends GolemDefinitionWithItemFilter {

    private static final ResourceLocation WARRIOR_ARMOR_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "warrior_golem_armor");
    private static final AttributeModifier WARRIOR_ARMOR_ATTRIBUTE_MODIFIER = new AttributeModifier(WARRIOR_ARMOR_MODIFIER_ID, 20.0f, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier WARRIOR_ARMOR_TOUGHNESS_ATTRIBUTE_MODIFIER = new AttributeModifier(WARRIOR_ARMOR_MODIFIER_ID, 2.0f, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier WARRIOR_KNOCKBACK_RESISTANCE_ATTRIBUTE_MODIFIER = new AttributeModifier(WARRIOR_ARMOR_MODIFIER_ID, 0.6f, AttributeModifier.Operation.ADD_VALUE);

    public WarriorGolemDefinition() {
        super(Optional.of(Blocks.DIAMOND_BLOCK), CopperGolemMeleeAttackBehavior.EnemyMobAttackBehavior::new, CopperGolemMeleeAttackBehavior.MEMORY_TYPES,
                CopperGolemMeleeAttackBehavior::isValidItemToHold, CopperGolemMeleeAttackBehavior.MAX_HELD_ITEM_STACK_SIZE);
    }

    @Override
    public boolean shouldAnimalPanic() {
        return false;
    }

    @Override
    public void initializeOnce(CopperGolem copperGolem) {
        super.initializeOnce(copperGolem);

        AttributeInstance armorAttributeInstance = copperGolem.getAttribute(Attributes.ARMOR);
        AttributeInstance armorToughnessAttributeInstance = copperGolem.getAttribute(Attributes.ARMOR_TOUGHNESS);
        AttributeInstance knockbackResistanceAttributeInstance = copperGolem.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

        Preconditions.checkNotNull(armorAttributeInstance);
        Preconditions.checkNotNull(armorToughnessAttributeInstance);
        Preconditions.checkNotNull(knockbackResistanceAttributeInstance);

        armorAttributeInstance.addOrReplacePermanentModifier(WARRIOR_ARMOR_ATTRIBUTE_MODIFIER);
        armorToughnessAttributeInstance.addOrReplacePermanentModifier(WARRIOR_ARMOR_TOUGHNESS_ATTRIBUTE_MODIFIER);
        knockbackResistanceAttributeInstance.addOrReplacePermanentModifier(WARRIOR_KNOCKBACK_RESISTANCE_ATTRIBUTE_MODIFIER);
    }

    @Override
    public void actuallyHurt(CopperGolem copperGolem, ServerLevel level, DamageSource damageSource, float amount) {
        super.actuallyHurt(copperGolem, level, damageSource, amount);

        // If we are attacked by a living entity, immediately reset the attack task cooldown and target the entity in question
        if (damageSource.getEntity() instanceof LivingEntity livingEntity && !damageSource.is(DamageTypeTags.NO_ANGER)) {
            copperGolem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, Optional.empty());
            copperGolem.getBrain().setMemoryWithExpiry(ExtraGolemsMemoryModuleTypes.LAST_ATTACKED_TARGET, livingEntity, CopperGolemMeleeAttackBehavior.ANGER_LAST_ATTACKED_ENTITY_MEMORY_TYPE);
            copperGolem.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.IDLE));
        }
    }
}
