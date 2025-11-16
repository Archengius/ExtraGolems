package me.archengius.extra_golems.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Dynamic;
import me.archengius.extra_golems.util.ExtraGolemsUtil;
import me.archengius.extra_golems.ai.CopperGolemHealthRegeneration;
import me.archengius.extra_golems.definition.GolemDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CopperGolem.class)
public abstract class CopperGolemMixin {

    @Unique private CopperGolemHealthRegeneration extra_golems$healthRegeneration;

    @Inject(at = @At("HEAD"), method = "makeBrain", cancellable = true)
    private void handleMakeBrain(Dynamic<?> dynamic, CallbackInfoReturnable<Brain<CopperGolem>> callbackInfo) {
        CopperGolem golem = ((CopperGolem) (Object) this);
        Optional<GolemDefinition> copperGolemType = ExtraGolemsUtil.getCopperGolemType(golem);
        copperGolemType.ifPresent(golemType -> {
            Brain<CopperGolem> resultEntityBrain = ExtraGolemsUtil.createCopperGolemBrain(golemType, dynamic);
            callbackInfo.setReturnValue(resultEntityBrain);
        });
    }

    @Inject(at = @At("HEAD"), method = "mobInteract", cancellable = true)
    private void handleMobInteract(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        CopperGolem golem = ((CopperGolem) (Object) this);
        if (!golem.level().isClientSide()) {
            InteractionResult assignGolemTypeResult = ExtraGolemsUtil.tryAssignGolemType(golem, player, interactionHand);
            if (assignGolemTypeResult != InteractionResult.PASS) {
                ExtraGolemsUtil.forceUpdateGolemMainHandSlot(golem, player);
                callbackInfo.setReturnValue(assignGolemTypeResult);
                return;
            }

            Optional<GolemDefinition> golemDefinition = ExtraGolemsUtil.getCopperGolemType(golem);
            if (golemDefinition.isPresent()) {
                InteractionResult behaviorInteractionResult = golemDefinition.get().mobInteract(golem, player, interactionHand);
                if (behaviorInteractionResult != InteractionResult.PASS) {
                    ExtraGolemsUtil.forceUpdateGolemMainHandSlot(golem, player);
                    callbackInfo.setReturnValue(behaviorInteractionResult);
                    return;
                }
            }

            // We do not want to allow the golem to drop the held item when interacting with an empty off-hand because that would result in item given
            // to golem from main hand being immediately dropped due to an empty off-hand interaction
            if (player.getItemInHand(interactionHand).isEmpty() && !golem.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && interactionHand == InteractionHand.OFF_HAND) {
                ExtraGolemsUtil.forceUpdateGolemMainHandSlot(golem, player);
                callbackInfo.setReturnValue(InteractionResult.SUCCESS_SERVER);
            }
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/AbstractGolem;mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), method = "mobInteract", cancellable = true)
    private void handleMobInteractLowPriority(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        CopperGolem golem = ((CopperGolem) (Object) this);
        if (!golem.level().isClientSide()) {
            InteractionResult assignGolemTypeResult = ExtraGolemsUtil.tryAssignGolemType(golem, player, interactionHand);
            if (assignGolemTypeResult != InteractionResult.PASS) {
                callbackInfo.setReturnValue(assignGolemTypeResult);
                return;
            }

            Optional<GolemDefinition> golemDefinition = ExtraGolemsUtil.getCopperGolemType(golem);
            if (golemDefinition.isPresent()) {
                InteractionResult behaviorInteractionResult = golemDefinition.get().mobInteract(golem, player, interactionHand);
                if (behaviorInteractionResult != InteractionResult.PASS) {
                    callbackInfo.setReturnValue(behaviorInteractionResult);
                }
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void handleTick(CallbackInfo callbackInfo) {
        CopperGolem golem = ((CopperGolem) (Object) this);
        if (!golem.level().isClientSide()) {
            if (extra_golems$healthRegeneration == null) {
                extra_golems$healthRegeneration = new CopperGolemHealthRegeneration(golem);
            }
            this.extra_golems$healthRegeneration.tickHealthRegeneration();

            Optional<GolemDefinition> golemDefinition = ExtraGolemsUtil.getCopperGolemType(golem);
            golemDefinition.ifPresent(definition -> definition.tick(golem));
        }
    }

    @Inject(at = @At("HEAD"), method = "dropEquipment")
    private void handleDropEquipment(ServerLevel level, CallbackInfo callbackInfo) {
        CopperGolem golem = ((CopperGolem) (Object) this);
        Optional<GolemDefinition> golemDefinition = ExtraGolemsUtil.getCopperGolemType(golem);
        golemDefinition.ifPresent(definition -> definition.dropEquipment(golem, level));
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "createAttributes")
    private static AttributeSupplier.Builder handleCreateAttributes(AttributeSupplier.Builder original) {
        return original.add(Attributes.ATTACK_DAMAGE, 1.0f).add(Attributes.MINING_EFFICIENCY).add(Attributes.BLOCK_BREAK_SPEED);
    }
}