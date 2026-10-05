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
        if (other instanceof MillVillagerEntity peer) {
            // Raiders fight the fighters of the village they raid, and that village's fighters fight them.
            var raiding = org.millenaire.fabric.village.VillageRaids.raidTarget(villager);
            if (raiding.isPresent() && raiding.get().equals(peer.villageKey()) && peer.fights()) return true;
            var raided = org.millenaire.fabric.village.VillageRaids.raidTarget(peer);
            if (raided.isPresent() && !villager.villageKey().isEmpty() && raided.get().equals(villager.villageKey())) return true;
            return peer.combatRole() == Role.HOSTILE;
        }
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
                other -> !villager.fights() && danger(villager, other)));
        goals.addGoal(1, new RangedAttackGoal(villager, 0.6, 30, 15.0F) {
            @Override public boolean canUse() { return villager.isArcher() && villager.fights() && super.canUse(); }
        });
        goals.addGoal(1, new MeleeAttackGoal(villager, 0.75, true) {
            @Override public boolean canUse() { return !villager.isArcher() && villager.fights() && super.canUse(); }
        });
        targets.addGoal(0, new VillagerHiring.DefendHirerGoal(villager));
        goals.addGoal(1, new VillagerHiring.FollowHirerGoal(villager));
        goals.addGoal(1, new RaidMarchGoal(villager));
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
        // Friendly fire within a village (or between bandits) starts no fight.
        if (attacker instanceof MillVillagerEntity peer && (peer.combatRole() == Role.HOSTILE) == (victim.combatRole() == Role.HOSTILE)
                && peer.villageKey().equals(victim.villageKey())) return;
        if (victim.isSleeping()) victim.stopSleeping();
        AABB area = victim.getBoundingBox().inflate(ALERT_RADIUS);
        for (MillVillagerEntity defender : level.getEntitiesOfClass(MillVillagerEntity.class, area,
                other -> other.combatRole() != Role.CIVILIAN && other.isAlive())) {
            boolean sameSide = (defender.combatRole() == Role.HOSTILE) == (victim.combatRole() == Role.HOSTILE)
                    && (victim.villageKey().isEmpty() || defender.villageKey().equals(victim.villageKey()));
            if (!sameSide || defender == attacker || attacker instanceof MillVillagerEntity peer && peer.villageKey().equals(defender.villageKey())) continue;
            if (defender.getTarget() == null || defender == victim) defender.setTarget(attacker);
        }
    }

    /** Raiders walk to the raided town hall, and home again with the loot, between fights. */
    static final class RaidMarchGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final MillVillagerEntity villager;
        private net.minecraft.core.BlockPos destination;
        private net.minecraft.world.phys.Vec3 lastPosition;
        private int stuckTicks;
        RaidMarchGoal(MillVillagerEntity villager) {
            this.villager = villager;
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }
        @Override public boolean canUse() {
            destination = org.millenaire.fabric.village.VillageRaids.destination(villager).orElse(null);
            return destination != null && villager.getTarget() == null && !villager.blockPosition().closerThan(destination, 3);
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            if (villager.tickCount % 20 != 0 && !villager.getNavigation().isDone()) return;
            // Walls, cliffs or water can leave no path: after ten seconds without progress, slip through.
            var now = villager.position();
            if (lastPosition != null && now.distanceToSqr(lastPosition) < 1) stuckTicks += 20; else stuckTicks = 0;
            lastPosition = now;
            double dx = destination.getX() + 0.5 - villager.getX(), dz = destination.getZ() + 0.5 - villager.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= 30) {
                if (stuckTicks >= 200) {
                    villager.snapTo(destination.getX() + 0.5, destination.getY() + 1, destination.getZ() + 0.5, villager.getYRot(), villager.getXRot());
                    stuckTicks = 0;
                } else villager.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 0.75);
                return;
            }
            // Navigation only plans within the follow range: march by waypoints 24 blocks apart on the surface.
            int x = (int) Math.floor(villager.getX() + dx / distance * 24), z = (int) Math.floor(villager.getZ() + dz / distance * 24);
            if (!villager.level().isLoaded(new net.minecraft.core.BlockPos(x, 0, z))) return;
            int y = villager.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (stuckTicks >= 200) {
                villager.snapTo(x + 0.5, y, z + 0.5, villager.getYRot(), villager.getXRot());
                villager.getNavigation().stop();
                stuckTicks = 0;
                return;
            }
            villager.getNavigation().moveTo(x + 0.5, y, z + 0.5, 0.75);
        }
        @Override public void stop() { villager.getNavigation().stop(); }
    }
}
