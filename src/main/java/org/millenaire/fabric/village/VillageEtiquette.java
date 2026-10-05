package org.millenaire.fabric.village;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CropBlock;
import org.millenaire.fabric.FabricReputationState;
import org.millenaire.fabric.FabricVillageOwnership;
import org.millenaire.fabric.quest.FabricQuestState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How villages react to what players do: harvesting a village's crops is theft (reputation falls, and the
 * villagers say so), and painting bricks in every colour earns the Rainbow advancement of the original.
 */
public final class VillageEtiquette {
    static final int THEFT_PENALTY = 16;
    static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final Map<UUID, Long> WARNED = new ConcurrentHashMap<>();

    private VillageEtiquette() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || player.isCreative()) return;
            if (!(state.getBlock() instanceof CropBlock)) return;
            var settlement = PlayerVillages.settlementAt(serverLevel, pos);
            if (settlement.isEmpty()) return;
            String key = VillageGrowth.key(settlement.get());
            if (FabricVillageOwnership.get(serverLevel.getServer()).owns(key, player.getUUID())) return;
            FabricReputationState.get(serverLevel.getServer()).add(key, player.getUUID(), -THEFT_PENALTY);
            long now = serverLevel.getGameTime();
            if (now - WARNED.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) > 200) {
                WARNED.put(player.getUUID(), now);
                serverPlayer.sendSystemMessage(Component.literal("[Millénaire] The villagers of " + settlement.get().name()
                        + " saw you take their crops!").withStyle(ChatFormatting.RED));
            }
        });
    }

    /** Remembers a colour the player painted with; all sixteen award the Rainbow advancement. */
    public static void painted(ServerPlayer player, String colour) {
        var state = FabricQuestState.get(player.level().getServer());
        state.setPlayerTag(player.getUUID(), "painted_" + colour, true);
        for (String c : COLOURS) if (!state.playerTag(player.getUUID(), "painted_" + c)) return;
        var advancement = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("millenaire", "rainbow"));
        if (advancement != null) player.getAdvancements().award(advancement, "rainbow");
    }
}
