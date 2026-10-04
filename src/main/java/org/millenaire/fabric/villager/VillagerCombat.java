package org.millenaire.fabric.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fighting behaviour from the legacy villager tags: {@code helpinattacks}, {@code raider}, {@code defender} and
 * {@code archer} villagers fight monsters, bandits and anyone attacking their neighbours; {@code hostile}
 * villagers (bandits, madmen) attack players and other villagers; everybody else runs away from danger.
 */
public final class VillagerCombat {
    public enum Role { CIVILIAN, FIGHTER, HOSTILE }

    private static final List<String> FIGHTER_TAGS = List.of("helpinattacks", "raider", "defender", "archer");
    /** Radius in which villagers hear a neighbour being attacked. */
    static final double ALERT_RADIUS = 32.0;
    /** Reputation lost per point of damage a player deals to a peaceful villager, and for killing one. */
    static final float REPUTATION_PER_DAMAGE = 10.0F;
    static final int REPUTATION_PER_KILL = 500;

    private VillagerCombat() {}

    public static Role role(VillagerProfile profile) {
        if (profile == null) return Role.CIVILIAN;
        if (profile.tags().contains("hostile")) return Role.HOSTILE;
        return profile.tags().stream().anyMatch(FIGHTER_TAGS::contains) ? Role.FIGHTER : Role.CIVILIAN;
    }

    public static boolean archer(VillagerProfile profile) { return profile != null && profile.tags().contains("archer"); }

    /** Main-hand item for a profile: a bow for archers, else the legacy {@code defaultweapon} good, if it resolves. */
    public static ItemStack weapon(VillagerProfile profile, java.util.function.Function<String, Optional<ItemStack>> goods) {
        if (profile == null) return ItemStack.EMPTY;
        if (archer(profile)) return new ItemStack(Items.BOW);
        String good = profile.defaultWeapon().trim().toLowerCase(Locale.ROOT);
        if (good.isEmpty()) return ItemStack.EMPTY;
        return goods.apply(good).map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    /** Whether {@code villager} considers {@code other} an enemy to attack (never another member of its own side). */
    public static boolean enemy(MillVillagerEntity villager, LivingEntity other) {
        if (other == villager || !other.isAlive()) return false;
        Role role = villager.combatRole();
        if (role == Role.HOSTILE) {
            if (other instanceof Player player) return !player.isCreative() && !player.isSpectator();
            return other instanceof MillVillagerEntity peer && peer.combatRole() != Role.HOSTILE;
        }
        if (other instanceof MillVillagerEntity peer) return peer.combatRole() == Role.HOSTILE;
        // Creepers are left alone: fighting them next to houses would blow the village up.
        return other instanceof Enemy && !(other instanceof Creeper);
    }

    /** Something a civilian should run from. */
    static boolean danger(MillVillagerEntity villager, LivingEntity other) {
        if (other instanceof MillVillagerEntity peer) return peer.combatRole() == Role.HOSTILE && villager.combatRole() != Role.HOSTILE;
        return other instanceof Enemy;
    }

    static void register(MillVillagerEntity villager) {
        var goals = villager.goalSelector();
        var targets = villager.targetSelector();
        goals.addGoal(1, new AvoidEntityGoal<>(villager, LivingEntity.class, 10.0F, 0.6, 0.75,
                other -> villager.combatRole() == Role.CIVILIAN && danger(villager, other)));
        goals.addGoal(1, new RangedAttackGoal(villager, 0.6, 30, 15.0F) {
            @Override public boolean canUse() { return villager.isArcher() && villager.combatRole() != Role.CIVILIAN && super.canUse(); }
        });
        goals.addGoal(1, new MeleeAttackGoal(villager, 0.75, true) {
            @Override public boolean canUse() { return !villager.isArcher() && villager.combatRole() != Role.CIVILIAN && super.canUse(); }
        });
        targets.addGoal(1, new NearestAttackableTargetGoal<>(villager, LivingEntity.class, 10, true, false,
                (other, level) -> villager.combatRole() != Role.CIVILIAN && enemy(villager, other)) {
            @Override public boolean canUse() { return villager.combatRole() != Role.CIVILIAN && !villager.isSleeping() && super.canUse(); }
        });
    }

    /**
     * Called when a villager is hurt: it wakes up, and fighters within earshot (and the victim itself, if it
     * fights) turn on the attacker. Villagers never turn on their own side over friendly fire.
     */
    static void alert(MillVillagerEntity victim, LivingEntity attacker) {
        if (attacker == null || attacker == victim || !(victim.level() instanceof ServerLevel level)) return;
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        if (attacker instanceof MillVillagerEntity peer && peer.combatRole() == victim.combatRole()) return;
        if (victim.isSleeping()) victim.stopSleeping();
        AABB area = victim.getBoundingBox().inflate(ALERT_RADIUS);
        for (MillVillagerEntity defender : level.getEntitiesOfClass(MillVillagerEntity.class, area,
                other -> other.combatRole() != Role.CIVILIAN && other.isAlive())) {
            boolean sameSide = (defender.combatRole() == Role.HOSTILE) == (victim.combatRole() == Role.HOSTILE);
            if (!sameSide || defender == attacker) continue;
            if (defender.getTarget() == null || defender == victim) defender.setTarget(attacker);
        }
    }
}
