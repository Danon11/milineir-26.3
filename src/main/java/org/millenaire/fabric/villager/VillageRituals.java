package org.millenaire.fabric.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.millenaire.fabric.economy.TradeOffers;
import org.millenaire.fabric.economy.Wallet;

import java.util.*;

/**
 * Pujas (Indian pandits) and sacrifices (Mayan shamans, tag {@code performssacrifices}): while the priest leads
 * the ceremony at the temple, a player holding an enchantable item may pay for one more level of one of the
 * ceremony's blessings. The price grows with the square of the level.
 */
public final class VillageRituals {
    public static final Set<String> CEREMONIES = Set.of("performpujas", "bepujaperformer");
    private static final List<ResourceKey<Enchantment>> PUJA = List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION,
            Enchantments.PROJECTILE_PROTECTION, Enchantments.FEATHER_FALLING, Enchantments.EFFICIENCY, Enchantments.UNBREAKING,
            Enchantments.FORTUNE, Enchantments.SMITE, Enchantments.POWER, Enchantments.LUCK_OF_THE_SEA, Enchantments.AQUA_AFFINITY);
    private static final List<ResourceKey<Enchantment>> SACRIFICE = List.of(Enchantments.SHARPNESS, Enchantments.FIRE_ASPECT,
            Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.FLAME, Enchantments.PUNCH, Enchantments.THORNS,
            Enchantments.BLAST_PROTECTION, Enchantments.RESPIRATION, Enchantments.BANE_OF_ARTHROPODS);

    private VillageRituals() {}

    public static boolean performing(MillVillagerEntity priest) {
        return priest.activity().map(CEREMONIES::contains).orElse(false);
    }

    static boolean sacrifices(MillVillagerEntity priest) {
        return priest.profile().map(p -> p.tags().contains("performssacrifices")).orElse(false);
    }

    public static int price(int level) { return TradeOffers.SILVER * 4 * level * level; }

    /** Blessings of this ceremony the held item can still receive, with their next level. */
    static Map<Holder<Enchantment>, Integer> blessings(ServerPlayer player, MillVillagerEntity priest, ItemStack item) {
        var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Map<Holder<Enchantment>, Integer> result = new LinkedHashMap<>();
        for (var key : sacrifices(priest) ? SACRIFICE : PUJA) {
            var holder = registry.get(key);
            if (holder.isEmpty() || !holder.get().value().canEnchant(item)) continue;
            int level = EnchantmentHelper.getItemEnchantmentLevel(holder.get(), item);
            if (level < holder.get().value().getMaxLevel()) result.put(holder.get(), level + 1);
        }
        return result;
    }

    /** Offers the blessings when a player brings an item to a priest during the ceremony. */
    public static boolean offer(ServerPlayer player, MillVillagerEntity priest) {
        ItemStack item = player.getMainHandItem();
        if (!performing(priest) || item.isEmpty()) return false;
        var blessings = blessings(player, priest, item);
        String rite = sacrifices(priest) ? "sacrifice" : "puja";
        if (blessings.isEmpty()) {
            player.sendSystemMessage(Component.literal(priest.getName().getString() + ": the gods have nothing more to give this " + item.getHoverName().getString() + ".")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        var menu = org.millenaire.fabric.ui.MillMenu.builder(priest.getName().getString())
                .subtitle((sacrifices(priest) ? "Sacrifice" : "Puja") + " for your " + item.getHoverName().getString());
        menu.text("Which blessing should the " + rite + " ask for? One level per offering.");
        blessings.forEach((holder, level) -> {
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            menu.row(Enchantment.getFullname(holder, level).getString() + " — " + VillagerHiring.money(price(level)),
                    org.millenaire.fabric.ui.MillMenu.button("Bless", "/" + org.millenaire.fabric.village.SummoningWand.COMMAND + " bless "
                            + priest.getStringUUID() + " " + id, org.millenaire.fabric.ui.MillMenu.Tone.GOOD));
        });
        org.millenaire.fabric.ui.MillMenus.show(player, menu.build());
        return true;
    }

    public static String bless(ServerPlayer player, UUID priestId, String enchantment) {
        if (!(player.level().getEntity(priestId) instanceof MillVillagerEntity priest) || priest.distanceTo(player) > 8)
            return "The priest is not near you.";
        if (!performing(priest)) return priest.getName().getString() + " is not leading a ceremony now.";
        ItemStack item = player.getMainHandItem();
        var id = Identifier.tryParse(enchantment);
        var entry = blessings(player, priest, item).entrySet().stream()
                .filter(e -> e.getKey().unwrapKey().map(key -> key.identifier().equals(id)).orElse(false)).findFirst();
        if (entry.isEmpty()) return "That blessing cannot be given to the item in your hand.";
        int level = entry.get().getValue();
        if (!Wallet.pay(player, price(level))) return "The offering for this blessing is " + VillagerHiring.money(price(level)) + ".";
        EnchantmentHelper.updateEnchantments(item, enchantments -> enchantments.set(entry.get().getKey(), level));
        player.level().playSound(null, priest.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return "The " + (sacrifices(priest) ? "sacrifice" : "puja") + " blesses your " + item.getHoverName().getString() + " with "
                + Enchantment.getFullname(entry.get().getKey(), level).getString() + ".";
    }
}
