package me.archengius.extra_golems.definition;

import com.google.common.collect.ImmutableList;
import me.archengius.extra_golems.ai.CopperGolemBaseBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class GolemDefinitionWithItemFilter extends SimpleGolemDefinition {

    protected final Predicate<ItemStack> itemFilter;
    protected final int maxHeldStackSize;

    public GolemDefinitionWithItemFilter(Optional<ItemLike> behaviorItem, Supplier<? extends CopperGolemBaseBehavior> golemBehavior, ImmutableList<MemoryModuleType<?>> additionalMemoryTypes, Predicate<ItemStack> itemFilter, int maxHeldStackSize) {
        super(behaviorItem, golemBehavior, additionalMemoryTypes);
        this.itemFilter = itemFilter;
        this.maxHeldStackSize = maxHeldStackSize;
    }

    public GolemDefinitionWithItemFilter(Predicate<ItemStack> behaviorItemPredicate, Supplier<? extends CopperGolemBaseBehavior> golemBehavior, ImmutableList<MemoryModuleType<?>> additionalMemoryTypes, Predicate<ItemStack> itemFilter, int maxHeldStackSize) {
        super(behaviorItemPredicate, golemBehavior, additionalMemoryTypes);
        this.itemFilter = itemFilter;
        this.maxHeldStackSize = maxHeldStackSize;
    }

    @Override
    public InteractionResult mobInteract(CopperGolem copperGolem, Player player, InteractionHand interactionHand) {
        if (interactionHand == InteractionHand.MAIN_HAND && !player.getItemInHand(interactionHand).isEmpty() && player.isCrouching()) {
            ItemStack itemInGolemHandCopy = copperGolem.getItemBySlot(EquipmentSlot.MAINHAND).copy();
            ItemStack itemInPlayerHand = player.getItemInHand(interactionHand);

            if (!itemInPlayerHand.isEmpty() && this.itemFilter.test(itemInPlayerHand) && (itemInGolemHandCopy.isEmpty() ||
                    ItemStack.isSameItemSameComponents(itemInGolemHandCopy, itemInPlayerHand)) && itemInGolemHandCopy.getCount() < this.maxHeldStackSize) {
                int maxItemsToTake = Math.max(this.maxHeldStackSize - itemInGolemHandCopy.getCount(), 0);
                int itemAmountToTake = Math.min(itemInPlayerHand.getCount(), maxItemsToTake);
                if (itemAmountToTake > 0) {
                    if (itemInGolemHandCopy.isEmpty()) {
                        itemInGolemHandCopy = itemInPlayerHand.copyWithCount(itemAmountToTake);
                    } else {
                        itemInGolemHandCopy.grow(itemAmountToTake);
                    }
                    itemInPlayerHand.consume(itemAmountToTake, player);
                    copperGolem.setItemSlot(EquipmentSlot.MAINHAND, itemInGolemHandCopy);
                    copperGolem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                    copperGolem.playSound(SoundEvents.ITEM_PICKUP, 0.2f, 2.0f);
                    notifyPlayerGaveItemToGolem(copperGolem, player, itemInGolemHandCopy);
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
        }
        return super.mobInteract(copperGolem, player, interactionHand);
    }

    protected void notifyPlayerGaveItemToGolem(CopperGolem copperGolem, Player player, ItemStack itemStack) {
    }
}
