package org.millenaire.fabric.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.millenaire.fabric.economy.TradeOffers;
import org.millenaire.fabric.economy.Wallet;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Hired villagers, from the legacy {@code hiringcost}: for that many silver deniers a day a villager follows the
 * player, fights whoever attacks the player and whatever the player attacks, and goes home when the time is up
 * or the player releases them.
 */
public final class VillagerHiring {
    public static final long DAY = 24000;

    private VillagerHiring() {}

    public static int pricePerDay(VillagerProfile profile) { return profile.hiringCost() * TradeOffers.SILVER; }

    public static boolean hireable(MillVillagerEntity villager) {
        return villager.profile().map(p -> p.hiringCost() > 0 && !p.child()).orElse(false)
                && villager.combatRole() != VillagerCombat.Role.HOSTILE;
    }

    /** The chat offer shown when a player sneak-uses a hireable villager. */
    public static void offer(ServerPlayer player, MillVillagerEntity villager) {
        var profile = villager.profile().orElseThrow();
        String id = villager.getStringUUID();
        String command = "/" + org.millenaire.fabric.village.SummoningWand.COMMAND + " ";
        var menu = org.millenaire.fabric.ui.MillMenu.builder(villager.getName().getString()).subtitle("Hire a fighter");
        String price = money(pricePerDay(profile));
        if (villager.hiredBy(player)) {
            long left = Math.max(0, villager.hiredUntil() - player.level().getGameTime());
            menu.text("I serve you for " + (left / 1000) + " more hours.");
            menu.row("One more day costs " + price + ".", org.millenaire.fabric.ui.MillMenu.button("Extend", command + "hire " + id, org.millenaire.fabric.ui.MillMenu.Tone.GOOD),
                    org.millenaire.fabric.ui.MillMenu.button("Release", command + "release " + id, org.millenaire.fabric.ui.MillMenu.Tone.BAD));
        } else if (villager.isHired()) {
            menu.text("I already serve someone else.");
        } else {
            menu.text("I can follow you and fight for you for a day: I defend you against whoever attacks you and strike what you strike.");
            menu.row("A day's service costs " + price + ".", org.millenaire.fabric.ui.MillMenu.button("Hire", command + "hire " + id, org.millenaire.fabric.ui.MillMenu.Tone.GOOD));
        }
        org.millenaire.fabric.ui.MillMenus.show(player, menu.build());
    }

    static String money(int deniers) {
        int gold = deniers / TradeOffers.GOLD, silver = deniers % TradeOffers.GOLD / TradeOffers.SILVER, copper = deniers % TradeOffers.SILVER;
        StringBuilder text = new StringBuilder();
        if (gold > 0) text.append(gold).append("g ");
        if (silver > 0) text.append(silver).append("s ");
        if (copper > 0 || text.isEmpty()) text.append(copper).append("d");
        return text.toString().trim();
    }

    private static MutableComponent button(String label, ChatFormatting colour, String arguments) {
        return Component.literal(label).withStyle(style -> style.withColor(colour)
                .withClickEvent(new ClickEvent.RunCommand("/" + org.millenaire.fabric.village.SummoningWand.COMMAND + " " + arguments)));
    }

    public static String hire(ServerPlayer player, UUID id) {
        if (!(player.level().getEntity(id) instanceof MillVillagerEntity villager) || villager.distanceTo(player) > 12)
            return "That villager is not near you.";
        if (!hireable(villager)) return villager.getName().getString() + " cannot be hired.";
        if (villager.isHired() && !villager.hiredBy(player)) return villager.getName().getString() + " already serves someone else.";
        int price = pricePerDay(villager.profile().orElseThrow());
        if (!Wallet.pay(player, price)) return "You need " + money(price) + " to hire " + villager.getName().getString() + ".";
        long now = player.level().getGameTime();
        villager.hire(player.getUUID(), Math.max(now, villager.hiredUntil()) + DAY);
        return villager.getName().getString() + " now serves you until tomorrow at this time.";
    }

    public static String release(ServerPlayer player, UUID id) {
        if (!(player.level().getEntity(id) instanceof MillVillagerEntity villager) || !villager.hiredBy(player)) return "That villager does not serve you.";
        villager.hire(null, 0);
        return villager.getName().getString() + " goes back home.";
    }

    static Player hirer(MillVillagerEntity villager) {
        if (!villager.isHired() || !(villager.level() instanceof ServerLevel level)) return null;
        return level.getPlayerByUUID(villager.hirer());
    }

    /** Keeps a hired villager near the player, teleporting when left far behind. */
    static final class FollowHirerGoal extends Goal {
        private final MillVillagerEntity villager;
        private Player hirer;
        FollowHirerGoal(MillVillagerEntity villager) {
            this.villager = villager;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            hirer = hirer(villager);
            return hirer != null && villager.getTarget() == null && villager.distanceToSqr(hirer) > 25;
        }
        @Override public boolean canContinueToUse() {
            return hirer != null && hirer.isAlive() && villager.getTarget() == null && villager.distanceToSqr(hirer) > 9;
        }
        @Override public void tick() {
            villager.getLookControl().setLookAt(hirer);
            if (villager.distanceToSqr(hirer) > 32 * 32 && hirer.level() == villager.level()) {
                villager.snapTo(hirer.getX(), hirer.getY(), hirer.getZ(), villager.getYRot(), villager.getXRot());
                villager.getNavigation().stop();
            } else if (villager.tickCount % 10 == 0) villager.getNavigation().moveTo(hirer, 0.75);
        }
        @Override public void stop() { villager.getNavigation().stop(); }
    }

    /** A hired villager attacks whoever hurt its hirer and whatever its hirer last attacked. */
    static final class DefendHirerGoal extends TargetGoal {
        private final MillVillagerEntity villager;
        private LivingEntity enemy;
        DefendHirerGoal(MillVillagerEntity villager) {
            super(villager, false);
            this.villager = villager;
            setFlags(EnumSet.of(Flag.TARGET));
        }
        @Override public boolean canUse() {
            Player hirer = hirer(villager);
            if (hirer == null) return false;
            LivingEntity attacker = hirer.getLastHurtByMob();
            LivingEntity target = hirer.getLastHurtMob();
            enemy = attacker != null && attacker.isAlive() && attacker != villager ? attacker
                    : target != null && target.isAlive() && target != villager && hirer.tickCount - hirer.getLastHurtMobTimestamp() < 200 ? target : null;
            return enemy != null && !(enemy instanceof Player p && p == hirer);
        }
        @Override public void start() { villager.setTarget(enemy); super.start(); }
    }
}
