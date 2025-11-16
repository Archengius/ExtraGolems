package me.archengius.extra_golems.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import me.archengius.extra_golems.definition.CopperGolemDefinitions;
import me.archengius.extra_golems.ExtraGolemsMod;
import me.archengius.extra_golems.ai.ExtraGolemsCopperGolemAi;
import me.archengius.extra_golems.definition.GolemDefinition;
import me.archengius.extra_golems.mixin.LivingEntityAccessor;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class ExtraGolemsUtil {
    public static final String COPPER_GOLEM_TYPE_TAG = "golem_extras_golem_type";

    public static Optional<GolemDefinition> getCopperGolemType(CopperGolem entity) {
        return Optional.ofNullable(entity.get(DataComponents.CUSTOM_DATA))
                .flatMap(customData -> customData.copyTag().getString(COPPER_GOLEM_TYPE_TAG))
                .map(golemTypeTagString -> {
                    if (golemTypeTagString.indexOf(ResourceLocation.NAMESPACE_SEPARATOR) != -1) {
                        return ResourceLocation.parse(golemTypeTagString);
                    }
                    return ResourceLocation.fromNamespaceAndPath(ExtraGolemsMod.MOD_ID, golemTypeTagString);
                }).flatMap(CopperGolemDefinitions.REGISTRY::getOptional);
    }

    private static void setCopperGolemTypeInternal(CopperGolem entity, GolemDefinition golemDefinition) {
        ResourceLocation golemTypeKey = CopperGolemDefinitions.REGISTRY.getKey(golemDefinition);
        Preconditions.checkNotNull(golemTypeKey, "Unregister golem type %s", golemDefinition);

        CustomData customData = entity.get(DataComponents.CUSTOM_DATA);
        CompoundTag compoundTag = customData != null ? customData.copyTag() : new CompoundTag();
        compoundTag.putString(COPPER_GOLEM_TYPE_TAG, golemTypeKey.toString());
        entity.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(compoundTag));
    }

    public static void setCopperGolemType(CopperGolem entity, GolemDefinition golemDefinition) {
        if (!entity.level().isClientSide()) {
            setCopperGolemTypeInternal(entity, golemDefinition);

            entity.getBrain().stopAll((ServerLevel) entity.level(), entity);
            ((LivingEntityAccessor) entity).golem_extras$setBrain(ExtraGolemsCopperGolemAi.makeBrain(golemDefinition, LivingEntityAccessor.golem_extras$getEmptyBrain()));

            golemDefinition.initializeOnce(entity);
        }
    }

    public static Brain<CopperGolem> createCopperGolemBrain(GolemDefinition golemDefinition, Dynamic<?> dynamic) {
        return ExtraGolemsCopperGolemAi.makeBrain(golemDefinition, dynamic);
    }

    private static Optional<GolemDefinition> findGolemDefinitionByBehaviorItem(ItemStack itemStack) {
        for (GolemDefinition golemDefinition : CopperGolemDefinitions.REGISTRY) {
            if (golemDefinition.isBehaviorItem(itemStack)) {
                return Optional.of(golemDefinition);
            }
        }
        return Optional.empty();
    }

    public static InteractionResult tryAssignGolemType(CopperGolem entity, Player player, InteractionHand interactionHand) {
        ItemStack itemInHand = player.getItemInHand(interactionHand);
        Optional<GolemDefinition> currentGolemDefinition = getCopperGolemType(entity);

        if (!entity.level().isClientSide() && !itemInHand.isEmpty() && entity.getItemBySlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA).isEmpty() && currentGolemDefinition.isEmpty()) {
            Optional<GolemDefinition> newCopperGolemType = findGolemDefinitionByBehaviorItem(itemInHand);
            if (newCopperGolemType.isPresent()) {
                setCopperGolemType(entity, newCopperGolemType.get());
                entity.playSound(SoundEvents.ITEM_PICKUP, 0.3f, 1.0f);
                entity.setItemSlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA, itemInHand.copyWithCount(1));
                itemInHand.consume(1, player);
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return InteractionResult.PASS;
    }

    public static void forceUpdateGolemMainHandSlot(CopperGolem copperGolem, Player player) {
        // This is needed to correct a possible client misprediction when interacting with a golem with an item in hand with an empty main or off-hand
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.hasDisconnected()) {
            ItemStack slotInMainHand = copperGolem.getItemBySlot(EquipmentSlot.MAINHAND);
            serverPlayer.connection.send(new ClientboundSetEquipmentPacket(copperGolem.getId(), ImmutableList.of(Pair.of(EquipmentSlot.MAINHAND, slotInMainHand))));
        }
    }

    public static float calculateBlockDestroySpeed(PathfinderMob mob, BlockState blockState, ItemStack itemStack) {
        float resultDestroySpeed = itemStack.getDestroySpeed(blockState);
        if (resultDestroySpeed > 1.0f && mob.getAttributes().hasAttribute(Attributes.MINING_EFFICIENCY)) {
            resultDestroySpeed += (float)mob.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }
        if (MobEffectUtil.hasDigSpeed(mob)) {
            resultDestroySpeed *= 1.0f + (MobEffectUtil.getDigSpeedAmplification(mob) + 1) * 0.2F;
        }
        if (mob.hasEffect(MobEffects.MINING_FATIGUE)) {
            float miningFatigueSpeedMultiplier = switch (Objects.requireNonNull(mob.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1E-4f;
            };
            resultDestroySpeed *= miningFatigueSpeedMultiplier;
        }
        if (mob.getAttributes().hasAttribute(Attributes.BLOCK_BREAK_SPEED)) {
            resultDestroySpeed *= (float)mob.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        }
        return resultDestroySpeed;
    }

    public static boolean checkShouldDestroyBlock(ServerLevel level, PathfinderMob mob, BlockPos blockPos, BlockState blockState, ItemStack itemStack) {
        if (!checkCanDestroyBlock(level, blockPos, blockState)) {
            return false;
        }
        return !blockState.requiresCorrectToolForDrops() || itemStack.isCorrectToolForDrops(blockState);
    }

    public static boolean checkCanDestroyBlock(ServerLevel level, BlockPos blockPos, BlockState blockState) {
        Player fallbackPlayer = FakePlayer.get(level);
        if (!level.mayInteract(fallbackPlayer, blockPos)) {
            return false;
        }
        float blockDestroySpeed = blockState.getDestroySpeed(level, blockPos);
        return blockDestroySpeed != -1.0f;
    }

    public static void startDestroyingBlock(ServerLevel level, PathfinderMob mob, BlockPos blockPos, BlockState blockState, ItemStack itemStack, @Nullable EquipmentSlot equipmentSlot) {
        if (equipmentSlot != null) {
            EnchantmentHelper.onHitBlock(level, itemStack, mob, mob, equipmentSlot, Vec3.atCenterOf(blockPos), blockState, (item) -> mob.onEquippedItemBroken(item, equipmentSlot));
        }
        Player fallbackPlayer = FakePlayer.get(level);
        blockState.attack(level, blockPos, fallbackPlayer);
    }

    public static float calculateBlockDestroyProgress(Level level, PathfinderMob mob, BlockPos blockPos, BlockState blockState, ItemStack itemStack) {
        float blockDestroySpeed = blockState.getDestroySpeed(level, blockPos);
        if (blockDestroySpeed == -1.0f) {
            return 0.0f;
        }
        float entityDestroySpeed = calculateBlockDestroySpeed(mob, blockState, itemStack);
        boolean hasCorrectToolForBlock = !blockState.requiresCorrectToolForDrops() || itemStack.isCorrectToolForDrops(blockState);
        int destroySpeedDivisor = hasCorrectToolForBlock ? 30 : 100;
        return entityDestroySpeed / blockDestroySpeed / destroySpeedDivisor;
    }

    public static boolean destroyBlock(ServerLevel level, PathfinderMob mob, BlockPos blockPos, BlockState blockState, ItemStack itemStack) {
        if (!itemStack.getItem().canDestroyBlock(itemStack, blockState, level, blockPos, mob)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        Player fallbackPlayer = FakePlayer.get(level);
        BlockState destroyedBlockState = blockState.getBlock().playerWillDestroy(level, blockPos, blockState, fallbackPlayer);
        if (!level.removeBlock(blockPos, false)) {
            return false;
        }
        blockState.getBlock().destroy(level, blockPos, destroyedBlockState);

        ItemStack itemStackBeforeBreak = itemStack.copy();
        itemStack.getItem().mineBlock(itemStack, level, destroyedBlockState, blockPos, mob);

        boolean hasCorrectToolForBlock = !destroyedBlockState.requiresCorrectToolForDrops() || itemStackBeforeBreak.isCorrectToolForDrops(destroyedBlockState);
        if (hasCorrectToolForBlock) {
            blockState.getBlock().playerDestroy(level, fallbackPlayer, blockPos, destroyedBlockState, blockEntity, itemStackBeforeBreak);
        }
        return true;
    }
}
