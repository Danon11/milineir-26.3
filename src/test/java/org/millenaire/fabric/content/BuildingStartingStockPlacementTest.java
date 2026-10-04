package org.millenaire.fabric.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.millenaire.fabric.economy.LegacyGoodsCatalog;
import org.millenaire.fabric.economy.StartingStock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BuildingStartingStockPlacementTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        if (!Items.BREAD.builtInRegistryHolder().areComponentsBound())
            Items.BREAD.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
    private static LegacyGoodsCatalog goods() { return new LegacyGoodsCatalog(Map.of("bread", new LegacyGoodsCatalog.Good("bread", "minecraft:bread", 0)), List.of()); }
    private static LegacyPalette palette() { return new LegacyPalette(Map.of(0xff0000, new LegacyPalette.Point(0xff0000, "lockedchestGuess", "", "0", false, "", "", 0))); }
    private LegacyBuildingPlan plan(String rule, int length) throws Exception {
        var image = new BufferedImage(1, length, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < length; z++) image.setRGB(0, z, 0xff0000);
        Path path = temp.resolve(UUID.randomUUID() + ".png"); ImageIO.write(image, "png", path.toFile());
        return new LegacyBuildingPlan("demo", UUID.randomUUID().toString(), 'A', 0, 1, length, 0, path, Map.of("startinggood", List.of(rule)));
    }
    private static BuildingPlacement.Prepared prepare(LegacyBuildingPlan plan) throws Exception {
        return BuildingPlacement.prepare(plan, palette(), BlockPos.ZERO, 0, point -> Blocks.CHEST.defaultBlockState(), goods(), 1234);
    }
    @Test void requiresStockSupportAndCapsOverflowAtChestCapacity() throws Exception {
        var prepared = prepare(plan("bread,1,70,0", 1)); assertTrue(prepared.supported(), prepared.issues().toString());
        var unsupported = new MemoryWorld() { @Override public String stockRejection(StartingStock.Inventory inventory) { return "Stock unsupported"; } };
        assertThrows(IllegalArgumentException.class, () -> BuildingPlacement.place(prepared, unsupported, false)); assertEquals(0, unsupported.writes);
        var overflow = prepare(plan("bread,1,1729,0", 1)); assertTrue(overflow.supported(), overflow.issues().toString());
        assertEquals(1, overflow.startingStock().size());
    }
    @Test void validatesEveryHouseAndFillsEveryBuildingStockInAGroup() throws Exception {
        var first = plan("bread,1,70,0", 1); var last = plan("bread,1,11,0", 1);
        var layout = layout(first, last);
        var prepared = VillagePlacement.prepare(layout, palette(), point -> Blocks.CHEST.defaultBlockState(), goods());
        assertTrue(prepared.supported(), prepared.combined().issues().toString());
        assertEquals(2, prepared.combined().startingStock().size());
        var world = new MemoryWorld(); assertEquals(2, VillagePlacement.place(prepared, world, false)); assertEquals(81, world.itemCount());
        assertEquals(0, world.droppedItems);
        var broken = VillagePlacement.prepare(layout(first, plan("unknown,1,1,0", 1)), palette(), point -> Blocks.CHEST.defaultBlockState(), goods());
        assertFalse(broken.supported()); assertTrue(broken.combined().startingStock().isEmpty());
        var blocked = new MemoryWorld(); assertThrows(IllegalArgumentException.class, () -> VillagePlacement.place(broken, blocked, false)); assertEquals(0, blocked.writes);
    }
    @Test void clearsNewInventoriesBeforeRollbackSoFilledChestsCannotDropItems() throws Exception {
        var prepared = prepare(plan("bread,1,90,0", 2)); assertTrue(prepared.supported(), prepared.issues().toString());
        var world = new MemoryWorld(); world.failFill = 2;
        assertThrows(IllegalStateException.class, () -> BuildingPlacement.place(prepared, world, false));
        assertEquals(0, world.droppedItems); assertEquals(0, world.itemCount()); assertTrue(world.chests.isEmpty());
        for (var change : prepared.changes()) assertTrue(world.get(change.pos()).isAir());
    }
    private static VillageLayout.Layout layout(LegacyBuildingPlan first, LegacyBuildingPlan last) {
        var type = VillageTypeDefinition.from("demo", "test", new LegacyDocument("test", "UTF-8", List.of(),
                Map.of("centre", List.of(first.key()), "start", List.of(last.key()))));
        return new VillageLayout.Layout(type, new LegacyBuildingPlan.Position(0, 64, 0), 1234, 80, List.of(
                new VillageLayout.Building(first, new LegacyBuildingPlan.Position(0, 64, 0), 0, true, new VillageLayout.Bounds(-1, -1, 1, 1)),
                new VillageLayout.Building(last, new LegacyBuildingPlan.Position(10, 64, 0), 0, false, new VillageLayout.Bounds(9, -1, 11, 1))), List.of());
    }
    private static class MemoryWorld implements BuildingPlacement.WorldAccess {
        final Map<BlockPos, BlockState> blocks = new HashMap<>(); final Map<BlockPos, SimpleContainer> chests = new HashMap<>();
        int writes, fills, failFill = -1, droppedItems;
        public BlockState get(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public String rejection(BlockPos pos, boolean replace) { return null; }
        public boolean set(BlockPos pos, BlockState state) {
            writes++;
            if (state.isAir()) {
                var chest = chests.remove(pos);
                if (chest != null) for (var stack : chest) droppedItems += stack.getCount();
            }
            blocks.put(pos, state); return true;
        }
        public String setupRejection(BuildingPlacement.Setup setup) { return null; }
        public void initialize(BuildingPlacement.Setup setup) { chests.put(setup.pos(), new SimpleContainer(27)); }
        public String stockRejection(StartingStock.Inventory stock) { return null; }
        public void fill(StartingStock.Inventory stock) {
            if (++fills == failFill) throw new IllegalStateException("Stock initialization failed");
            StartingStock.apply(chests.get(stock.pos()), stock);
        }
        public void discardStartingStock(List<StartingStock.Inventory> stocks) {
            for (var stock : stocks) if (chests.containsKey(stock.pos())) chests.get(stock.pos()).clearContent();
        }
        int itemCount() { return chests.values().stream().flatMap(chest -> java.util.stream.StreamSupport.stream(chest.spliterator(), false)).mapToInt(stack -> stack.getCount()).sum(); }
    }
}
