package me.archengius.extra_golems.ai;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;

public class CopperGolemHealthRegeneration {
    private static final int GOLEM_HEALTH_REGENERATION_DELAY = 80;
    private static final int GOLEM_HEALTH_REGENERATION_INTERVAL = 20;
    private static final float GOLEM_HEALTH_REGENERATION_AMOUNT = 1.0f;

    private final Mob mob;
    private int healthRegenerationTicks = 0;

    public CopperGolemHealthRegeneration(Mob mob) {
        this.mob = mob;
    }

    public void tickHealthRegeneration() {
        if (!mob.level().isClientSide()) {
            int ticksSinceLastHurt = mob.tickCount - mob.getLastHurtByMobTimestamp();
            if (ticksSinceLastHurt > GOLEM_HEALTH_REGENERATION_DELAY && mob.getHealth() < mob.getMaxHealth()) {
                healthRegenerationTicks++;
                if (healthRegenerationTicks >= GOLEM_HEALTH_REGENERATION_INTERVAL) {
                    mob.playSound(SoundEvents.COPPER_GOLEM_SPAWN, 0.5f, 1.0f);
                    mob.heal(GOLEM_HEALTH_REGENERATION_AMOUNT);
                    healthRegenerationTicks = 0;
                }
            } else {
                healthRegenerationTicks = 0;
            }
        }
    }
}
