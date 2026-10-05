package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillagerState;
import org.millenaire.fabric.MillenaireCommands;

import java.util.*;

/**
 * Wall panels as in the original town halls: the town hall's first panel shows the village name and population,
 * the second what is being built; other buildings' panels show their name and residents. Refreshed every minute.
 */
public final class VillagePanels {
    static final int INTERVAL = 1200, WIDTH = 15;

    private VillagePanels() {}

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % INTERVAL != 900) return;
        for (var settlement : FabricSettlementState.get(server).settlements()) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, settlement.dimension()));
            if (level == null) continue;
            update(level, settlement);
        }
    }

    public static int update(ServerLevel level, FabricSettlementState.Settlement settlement) {
        var catalog = MillenaireCommands.contentCatalog();
        var villagers = FabricVillagerState.get(level.getServer()).villagers();
        String key = VillageGrowth.key(settlement);
        int updated = 0;
        for (var building : settlement.buildings()) {
            var panels = building.placement().servicePoints().getOrDefault("panels", List.of());
            if (panels.isEmpty()) continue;
            String buildingKey = VillagePopulation.baseKey(VillagePopulation.buildingKey(building.placement().plan(), building.placement().origin()));
            long residents = villagers.stream().filter(v -> v.alive() && VillagePopulation.baseKey(v.building()).equals(buildingKey)).count();
            var plan = catalog.plan(building.placement().plan());
            String name = plan == null ? building.placement().plan() : plan.parameters().getOrDefault("nativename", List.of(plan.key())).getLast();
            List<List<String>> texts = new ArrayList<>();
            if (building.centre()) {
                long population = FabricVillagerState.get(level.getServer()).inSettlement(settlement).stream().filter(FabricVillagerState.Villager::alive).count();
                texts.add(lines(settlement.name(), population + " villagers", settlement.buildings().size() + " buildings"));
                texts.add(lines("Building:", VillageGrowth.pending(key).map(VillageGrowth.Project::label).orElse("nothing")));
            } else {
                texts.add(lines(name, residents == 0 ? "" : residents + " resident" + (residents == 1 ? "" : "s")));
            }
            for (int i = 0; i < panels.size() && i < texts.size(); i++) {
                var p = panels.get(i);
                BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
                if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
                var text = SignText.EMPTY.asMutable();
                List<String> content = texts.get(i);
                for (int line = 0; line < 4; line++) {
                    var component = Component.literal(line < content.size() ? content.get(line) : "");
                    text.setLine(line, component, component);
                }
                sign.setText(text.asImmutable(), SignTextSlot.FRONT);
                sign.setChanged();
                level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
                updated++;
            }
        }
        return updated;
    }

    /** Wraps text to the four sign lines. */
    static List<String> lines(String... parts) {
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            String rest = part;
            while (rest.length() > WIDTH && lines.size() < 4) {
                int cut = rest.lastIndexOf(' ', WIDTH);
                if (cut <= 0) cut = WIDTH;
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut).trim();
            }
            if (lines.size() < 4) lines.add(rest);
        }
        return lines.subList(0, Math.min(4, lines.size()));
    }
}
