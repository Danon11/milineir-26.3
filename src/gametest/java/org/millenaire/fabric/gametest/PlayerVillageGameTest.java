package org.millenaire.fabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.fabric.FabricSettlementState;
import org.millenaire.fabric.FabricVillageOwnership;
import org.millenaire.fabric.village.VillageGrowth;

/**
 * Founds player villages the way the summoning wand's chat buttons do: a built village on a gold block, a custom
 * village around a player-built town hall, then registers a player-built farm and orders a building.
 */
public final class PlayerVillageGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender(true, ClientGameTestContext.DEFAULT_TIMEOUT);
            var server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");

            // 1. A built player village (norman:controlled) on a gold block next to the player.
            BlockPos gold = server.computeOnServer(s -> {
                var level = s.overworld();
                var player = s.getPlayerList().getPlayers().getFirst();
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(3, 0, 0));
                level.setBlockAndUpdate(at, Blocks.GOLD_BLOCK.defaultBlockState());
                return at;
            });
            playerCommand(context, "millenaire_village found " + gold.getX() + " " + gold.getY() + " " + gold.getZ() + " norman:controlled");
            context.waitTicks(40);
            check(server, gold, "norman:controlled");
            server.runOnServer(s -> {
                if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath("millenaire", "summoningwand")))
                    throw new AssertionError("Summoning wand is not registered");
                // The owner may open the locked chests of the village they founded; the test player is in survival.
                var player = s.getPlayerList().getPlayers().getFirst();
                var level = s.overworld();
                var chest = settlement(s, gold).buildings().stream().flatMap(b -> b.placement().servicePoints().getOrDefault("chests", java.util.List.of()).stream())
                        .map(p -> level.getBlockEntity(new BlockPos(p.x(), p.y(), p.z())))
                        .filter(e -> e instanceof org.millenaire.fabric.storage.VillageChestBlockEntity).findFirst()
                        .map(e -> (org.millenaire.fabric.storage.VillageChestBlockEntity) e).orElseThrow(() -> new AssertionError("No village chest"));
                if (player.isCreative()) throw new AssertionError("Test player should be in survival");
                if (!chest.canOpen(player)) throw new AssertionError("Owner cannot open the village chest");
            });

            // 2. A custom village: the player's own town hall (craft 1, chests 6, signs 9) around a gold block, 300 blocks away.
            BlockPos centre = server.computeOnServer(s -> {
                var level = s.overworld();
                level.getChunk(300 >> 4, 0);
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(300, 0, 0));
                level.setBlockAndUpdate(at, Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(at.offset(2, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
                for (int i = 0; i < 6; i++) level.setBlockAndUpdate(at.offset(-3, 0, i * 2 - 5), Blocks.CHEST.defaultBlockState());
                for (int i = 0; i < 9; i++) level.setBlockAndUpdate(at.offset(4, 0, i - 4), Blocks.OAK_SIGN.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    var bed = Blocks.BED.red().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
                    level.setBlockAndUpdate(at.offset(i * 2 - 2, 0, 5), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                    level.setBlockAndUpdate(at.offset(i * 2 - 2, 0, 6), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                }
                var player = s.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, at.getX() + 0.5, at.getY() + 1, at.getZ() - 3.5, java.util.Set.of(), 0, 30, true);
                return at;
            });
            world.getConnection().waitForChunksRender(true, ClientGameTestContext.DEFAULT_TIMEOUT);
            playerCommand(context, "millenaire_village found " + centre.getX() + " " + centre.getY() + " " + centre.getZ() + " norman:customcontrolled");
            context.waitTicks(40);
            check(server, centre, "norman:customcontrolled");

            // 3. A player-built farm in that village: a sign, a chest and ten farmland blocks.
            BlockPos sign = server.computeOnServer(s -> {
                var level = s.overworld();
                BlockPos at = centre.offset(0, 0, -20);
                level.setBlockAndUpdate(at, Blocks.OAK_SIGN.defaultBlockState());
                level.setBlockAndUpdate(at.offset(1, 0, 0), Blocks.CHEST.defaultBlockState());
                for (int i = 0; i < 10; i++) level.setBlockAndUpdate(at.offset(i - 5, -1, 3), Blocks.FARMLAND.defaultBlockState());
                return at;
            });
            server.runCommand("tp @p " + sign.getX() + " " + (sign.getY() + 1) + " " + (sign.getZ() - 3));
            context.waitTicks(10);
            playerCommand(context, "millenaire_village custom " + sign.getX() + " " + sign.getY() + " " + sign.getZ() + " norman:custom_farm");
            context.waitTicks(20);
            int buildings = server.computeOnServer(s -> settlement(s, centre).buildings().size());
            if (buildings != 2) throw new AssertionError("Custom farm was not registered: " + buildings + " buildings");

            // 4. The owner orders a farm; a rushed growth pass builds it and clears the order.
            playerCommand(context, "millenaire_village order farm");
            context.waitTicks(10);
            String key = server.computeOnServer(s -> VillageGrowth.key(settlement(s, centre)));
            var orders = server.computeOnServer(s -> FabricVillageOwnership.get(s).owner(key).orElseThrow().orders());
            if (!orders.contains("farm")) throw new AssertionError("Order not recorded: " + orders);
            server.runCommand("execute positioned " + centre.getX() + " " + centre.getY() + " " + centre.getZ() + " run millenaire village grow rush");
            context.waitTicks(40);
            var after = server.computeOnServer(s -> FabricVillageOwnership.get(s).owner(key).orElseThrow().orders());
            int built = server.computeOnServer(s -> settlement(s, centre).buildings().size());
            if (!after.isEmpty() || built < 3) throw new AssertionError("Ordered farm not built: orders " + after + ", buildings " + built);
            playerCommand(context, "millenaire_village info");
            context.waitTicks(20);
            context.takeScreenshot("millenaire-player-village");
        }
    }

    private static void playerCommand(ClientGameTestContext context, String command) {
        context.runOnClient(client -> client.player.connection.sendCommand(command));
        context.waitTicks(2);
    }

    private static FabricSettlementState.Settlement settlement(MinecraftServer server, BlockPos pos) {
        return FabricSettlementState.get(server).containing(server.overworld().dimension().identifier(), pos.getX(), pos.getZ()).orElseThrow(
                () -> new AssertionError("No settlement at " + pos));
    }

    private static void check(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos pos, String type) {
        server.runOnServer(s -> {
            var settlement = settlement(s, pos);
            if (!settlement.type().equals(type)) throw new AssertionError("Expected " + type + " but found " + settlement.type());
            var player = s.getPlayerList().getPlayers().getFirst();
            if (!FabricVillageOwnership.get(s).owns(VillageGrowth.key(settlement), player.getUUID()))
                throw new AssertionError(type + " is not owned by the player");
        });
    }
}
