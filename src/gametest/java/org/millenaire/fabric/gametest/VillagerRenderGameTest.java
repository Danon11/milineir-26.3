package org.millenaire.fabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.fabric.villager.MillVillagerEntity;
import org.millenaire.fabric.villager.VillagerProfile;
import org.millenaire.fabric.villager.VillagerSpawning;

import java.util.*;

/** Spawns villagers of several cultures in front of the player and saves screenshots for visual review. */
public final class VillagerRenderGameTest implements FabricClientGameTest {
    private static final List<String> CULTURES = List.of("norman", "japanese", "indian", "mayan", "byzantines", "inuits", "seljuk");

    private static final List<String> VILLAGES = List.of("japanese:nogyo", "norman:agricole");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender(true, ClientGameTestContext.DEFAULT_TIMEOUT);
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("gamerule advance_time false");
            world.getServer().runCommand("weather clear");
            int[] count = new int[1];
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
                player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), 0, 15, true);
                List<VillagerProfile> picks = pick();
                Random random = new Random(1);
                for (int i = 0; i < picks.size(); i++) {
                    int x = base.getX() - picks.size() / 2 * 2 + i * 2;
                    int z = base.getZ() + 6;
                    BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                    MillVillagerEntity villager = VillagerSpawning.spawn(level, at, picks.get(i), "", new SplittableRandom(random.nextLong()));
                    villager.setNoAi(true);
                    villager.setYRot(180);
                    villager.setYHeadRot(180);
                    villager.setYBodyRot(180);
                    count[0]++;
                }
            });
            if (count[0] == 0) throw new AssertionError("No villager profiles loaded");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(60);
            world.getConnection().waitForChunksRender(true, ClientGameTestContext.DEFAULT_TIMEOUT);
            context.takeScreenshot("millenaire-villagers");

            // Second scene: a placed village seen from above, to review block models and textures.
            world.getServer().runCommand("kill @e[type=millenaire:villager]");
            world.getServer().runCommand("gamemode spectator @a");
            for (int index = 0; index < VILLAGES.size(); index++) {
                String village = VILLAGES.get(index);
                int x = index * 400;
                int y = world.getServer().computeOnServer(server -> {
                    var level = server.overworld();
                    level.getChunk(x >> 4, 48 >> 4);
                    return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, 48)).getY();
                });
                world.getServer().runCommand("execute positioned " + x + " " + y + " 48 run millenaire village replace 7 " + village);
                world.getServer().runCommand("execute positioned " + x + " " + y + " 48 run millenaire village paths");
                int top = world.getServer().computeOnServer(server -> server.overworld()
                        .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, 30)).getY());
                world.getServer().runCommand("tp @p " + (x + 0.5) + " " + (Math.max(y, top) + 24) + " 4.5 0 35");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(80);
                world.getConnection().waitForChunksRender(true, ClientGameTestContext.DEFAULT_TIMEOUT);
                context.takeScreenshot("millenaire-village-" + village.replace(':', '-'));
            }
        }
    }

    private static List<VillagerProfile> pick() {
        Map<String, VillagerProfile> profiles = VillagerSpawning.snapshot().profiles();
        List<VillagerProfile> result = new ArrayList<>();
        for (String culture : CULTURES)
            profiles.keySet().stream().filter(key -> key.startsWith(culture + "/")).sorted().limit(2)
                    .map(profiles::get).forEach(result::add);
        return result;
    }
}
