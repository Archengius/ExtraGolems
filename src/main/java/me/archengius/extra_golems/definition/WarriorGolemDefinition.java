package me.archengius.extra_golems.definition;

import com.google.common.base.Preconditions;
import me.archengius.extra_golems.ExtraGolemsMod;
import me.archengius.extra_golems.ai.CopperGolemMeleeAttackBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class WarriorGolemDefinition extends GolemDefinitionWithItemFilter {

    private static final ResourceLocation WARRIOR_ARMOR_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, "warrior_golem_armor");
    private static final AttributeModifier WARRIOR_ARMOR_ATTRIBUTE_MODIFIER = new AttributeModifier(WARRIOR_ARMOR_MODIFIER_ID, 20.0f, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier WARRIOR_ARMOR_TOUGHNESS_ATTRIBUTE_MODIFIER = new AttributeModifier(WARRIOR_ARMOR_MODIFIER_ID, 2.0f, AttributeModifier.Operation.ADD_VALUE);

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
        AttributeInstance armorToughnessAttributeInstance =  copperGolem.getAttribute(Attributes.ARMOR_TOUGHNESS);

        Preconditions.checkNotNull(armorAttributeInstance);
        Preconditions.checkNotNull(armorToughnessAttributeInstance);

        armorAttributeInstance.addOrReplacePermanentModifier(WARRIOR_ARMOR_ATTRIBUTE_MODIFIER);
        armorToughnessAttributeInstance.addOrReplacePermanentModifier(WARRIOR_ARMOR_TOUGHNESS_ATTRIBUTE_MODIFIER);
    }
}
