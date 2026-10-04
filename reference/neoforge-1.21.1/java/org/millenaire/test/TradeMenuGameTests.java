package org.millenaire.test;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeMenu;
import org.millenaire.item.MoneyHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class TradeMenuGameTests {
   private static TradeMenuGameTests.TradeContext setupTradeContext(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();

      for (int x = 0; x < 10; x++) {
         for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
         }
      }

      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      VillageSavedData savedData = VillageSavedData.get(level);
      savedData.getVillageManager().addVillage(village);
      ResourceLocation planId = ResourceLocation.fromNamespaceAndPath("millenaire", "test_shop");
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         planId,
         center,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/shop"),
         "a",
         0
      );
      ServerPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.moveTo(center.getX(), center.getY(), center.getZ());
      return new TradeMenuGameTests.TradeContext(village, building, player, center);
   }

   private static TradeGood appleGood(int sellingPrice, int buyingPrice, boolean autoGenerate, int minReputation) {
      return new TradeGood("apple", "minecraft:apple", sellingPrice, buyingPrice, 0, 0, autoGenerate, minReputation, "food", true, 0);
   }

   private static ShopProfile shopSellsAndBuys(String... goodIds) {
      List<String> ids = List.of(goodIds);
      return new ShopProfile(ids, ids, Collections.emptyList(), Collections.emptyList());
   }

   private static int countItem(ServerPlayer player, Item item) {
      int count = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_bulkBuy8(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood good = appleGood(10, 5, true, 0);
      ShopProfile profile = shopSellsAndBuys("apple");
      MoneyHelper.addDeniers(ctx.player.getInventory(), 200);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(good), ctx.center);
      menu.clickMenuButton(ctx.player, 1);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      int apples = countItem(ctx.player, Items.APPLE);
      if (money != 120) {
         helper.fail("Expected 120 deniers (200 - 8*10), got " + money);
      } else if (apples != 8) {
         helper.fail("Expected 8 apples, got " + apples);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_insufficientMoney(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood good = appleGood(10, 5, true, 0);
      ShopProfile profile = shopSellsAndBuys("apple");
      MoneyHelper.addDeniers(ctx.player.getInventory(), 35);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(good), ctx.center);
      menu.clickMenuButton(ctx.player, 1);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      int apples = countItem(ctx.player, Items.APPLE);
      if (apples != 3) {
         helper.fail("Expected 3 apples (clamped by money: 35/10=3), got " + apples);
      } else if (money != 5) {
         helper.fail("Expected 5 deniers remaining (35 - 3*10), got " + money);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_insufficientStock(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      ServerLevel level = helper.getLevel();
      BlockPos chestPos = ctx.center.east(2);
      level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
      if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
         chest.setItem(0, new ItemStack(Items.APPLE, 3));
      }

      final BuildingInventory inv = new BuildingInventory(List.of(chestPos));
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_shop"),
         ctx.center,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/shop"),
         "a",
         0
      ) {
         public BuildingInventory getInventory() {
            return inv;
         }
      };
      TradeGood good = appleGood(10, 5, false, 0);
      ShopProfile profile = shopSellsAndBuys("apple");
      MoneyHelper.addDeniers(ctx.player.getInventory(), 200);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, building, profile, List.of(good), ctx.center);
      menu.clickMenuButton(ctx.player, 1);
      int apples = countItem(ctx.player, Items.APPLE);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      if (apples != 3) {
         helper.fail("Expected 3 apples (clamped by stock), got " + apples);
      } else if (money != 170) {
         helper.fail("Expected 170 deniers (200 - 3*10), got " + money);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_reputationGate(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood good = appleGood(10, 5, true, 1000);
      ShopProfile profile = shopSellsAndBuys("apple");
      MoneyHelper.addDeniers(ctx.player.getInventory(), 100);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(good), ctx.center);
      menu.clickMenuButton(ctx.player, 0);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      int apples = countItem(ctx.player, Items.APPLE);
      if (apples != 0) {
         helper.fail("Expected 0 apples (reputation gate), got " + apples);
      } else if (money != 100) {
         helper.fail("Expected 100 deniers unchanged (buy blocked), got " + money);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_sellClampByPlayerItems(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood good = appleGood(10, 5, false, 0);
      ShopProfile profile = shopSellsAndBuys("apple");
      ctx.player.getInventory().add(new ItemStack(Items.APPLE, 3));
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(good), ctx.center);
      menu.clickMenuButton(ctx.player, 10);
      int apples = countItem(ctx.player, Items.APPLE);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      if (apples != 0) {
         helper.fail("Expected 0 apples (sold all 3), got " + apples);
      } else if (money != 15) {
         helper.fail("Expected 15 deniers (3 * 5), got " + money);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_multipleGoods(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood appleGood = appleGood(10, 5, true, 0);
      TradeGood stickGood = new TradeGood("stick", "minecraft:stick", 2, 1, 0, 0, true, 0, "material", true, 0);
      ShopProfile profile = new ShopProfile(List.of("apple", "stick"), List.of("apple", "stick"), Collections.emptyList(), Collections.emptyList());
      MoneyHelper.addDeniers(ctx.player.getInventory(), 50);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(appleGood, stickGood), ctx.center);
      menu.clickMenuButton(ctx.player, 12);
      int sticks = countItem(ctx.player, Items.STICK);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      if (sticks != 1) {
         helper.fail("Expected 1 stick, got " + sticks);
      } else if (money != 48) {
         helper.fail("Expected 48 deniers (50 - 2), got " + money);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_tagSellAddsCorrectItem(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      ServerLevel level = helper.getLevel();
      BlockPos chestPos = ctx.center.east(2);
      level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
      final BuildingInventory inv = new BuildingInventory(List.of(chestPos));
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_shop"),
         ctx.center,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/shop"),
         "a",
         0
      ) {
         public BuildingInventory getInventory() {
            return inv;
         }
      };
      TradeGood logGood = new TradeGood("logs", "#minecraft:logs", 10, 5, 0, 0, false, 0, "material", true, 0);
      ShopProfile profile = new ShopProfile(List.of("logs"), List.of("logs"), Collections.emptyList(), Collections.emptyList());
      ctx.player.getInventory().add(new ItemStack(Items.BIRCH_LOG, 5));
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, building, profile, List.of(logGood), ctx.center);
      menu.clickMenuButton(ctx.player, 9);
      if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
         boolean foundBirch = false;

         for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty()) {
               if (!stack.is(Items.BIRCH_LOG)) {
                  helper.fail("Chest contains " + stack.getItem() + " instead of birch_log — tag sell added wrong item");
                  return;
               }

               foundBirch = true;
            }
         }

         if (!foundBirch) {
            helper.fail("Chest should contain birch_log after selling birch logs");
         } else {
            helper.succeed();
         }
      } else {
         helper.fail("No chest block entity at expected position");
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_sellDoesNotLoseItemsInFullChest(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      ServerLevel level = helper.getLevel();
      BlockPos chestPos = ctx.center.east(2);
      level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
      if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
         Item[] fillers = new Item[]{
            Items.STONE,
            Items.DIRT,
            Items.COBBLESTONE,
            Items.OAK_PLANKS,
            Items.BIRCH_PLANKS,
            Items.SPRUCE_PLANKS,
            Items.JUNGLE_PLANKS,
            Items.ACACIA_PLANKS,
            Items.DARK_OAK_PLANKS,
            Items.SAND,
            Items.GRAVEL,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.DIAMOND,
            Items.EMERALD,
            Items.LAPIS_LAZULI,
            Items.REDSTONE,
            Items.COAL,
            Items.COPPER_INGOT,
            Items.BONE,
            Items.STRING,
            Items.LEATHER,
            Items.PAPER,
            Items.BOOK,
            Items.GLASS,
            Items.BRICK
         };

         for (int i = 0; i < 26; i++) {
            chest.setItem(i, new ItemStack(fillers[i], 1));
         }
      }

      final BuildingInventory inv = new BuildingInventory(List.of(chestPos));
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         ResourceLocation.fromNamespaceAndPath("millenaire", "test_shop"),
         ctx.center,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         ResourceLocation.fromNamespaceAndPath("millenaire", "test/shop"),
         "a",
         0
      ) {
         public BuildingInventory getInventory() {
            return inv;
         }
      };
      if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
         chest.setItem(26, new ItemStack(Items.FEATHER, 1));
      }

      TradeGood appleGood = new TradeGood("apple", "minecraft:apple", 10, 5, 0, 0, false, 0, "food", true, 0);
      ShopProfile profile = shopSellsAndBuys("apple");
      ctx.player.getInventory().add(new ItemStack(Items.APPLE, 5));
      int applesBefore = countItem(ctx.player, Items.APPLE);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, building, profile, List.of(appleGood), ctx.center);
      menu.clickMenuButton(ctx.player, 3);
      int applesAfter = countItem(ctx.player, Items.APPLE);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      if (applesAfter != applesBefore) {
         helper.fail("Player lost " + (applesBefore - applesAfter) + " apples in a full chest! countBuildingSpace overestimated");
      } else if (money != 0) {
         helper.fail("No sale should happen when chest is full, but player got " + money + " deniers");
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_rejectForgedBuyOnBuyOnlyGood(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood good = appleGood(10, 5, true, 0);
      ShopProfile profile = new ShopProfile(Collections.emptyList(), List.of("apple"), Collections.emptyList(), Collections.emptyList());
      MoneyHelper.addDeniers(ctx.player.getInventory(), 200);
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(good), ctx.center);
      boolean accepted = menu.clickMenuButton(ctx.player, 0);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      int apples = countItem(ctx.player, Items.APPLE);
      if (accepted) {
         helper.fail("Forged buy action on buy-only good should be rejected, but clickMenuButton returned true");
      } else if (apples != 0) {
         helper.fail("Expected 0 apples (forged buy rejected), got " + apples);
      } else if (money != 200) {
         helper.fail("Expected 200 deniers unchanged (forged buy rejected), got " + money);
      } else {
         ctx.player.getInventory().add(new ItemStack(Items.APPLE, 2));
         boolean sellAccepted = menu.clickMenuButton(ctx.player, 3);
         if (!sellAccepted) {
            helper.fail("Legitimate sell-to-shop action should be accepted on buy-only good");
         } else {
            int applesAfter = countItem(ctx.player, Items.APPLE);
            if (applesAfter != 1) {
               helper.fail("Expected 1 apple left after selling 1, got " + applesAfter);
            } else {
               helper.succeed();
            }
         }
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testTrade_rejectForgedSellOnSellOnlyGood(GameTestHelper helper) {
      TradeMenuGameTests.TradeContext ctx = setupTradeContext(helper);
      TradeGood good = appleGood(10, 5, true, 0);
      ShopProfile profile = new ShopProfile(List.of("apple"), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
      ctx.player.getInventory().add(new ItemStack(Items.APPLE, 5));
      TradeMenu menu = new TradeMenu(1, ctx.player.getInventory(), ctx.village, ctx.building, profile, List.of(good), ctx.center);
      boolean accepted = menu.clickMenuButton(ctx.player, 3);
      int money = MoneyHelper.getTotalDeniers(ctx.player.getInventory());
      int apples = countItem(ctx.player, Items.APPLE);
      if (accepted) {
         helper.fail("Forged sell action on sell-only good should be rejected, but clickMenuButton returned true");
      } else if (apples != 5) {
         helper.fail("Expected 5 apples unchanged (forged sell rejected), got " + apples);
      } else if (money != 0) {
         helper.fail("Expected 0 deniers (forged sell rejected), got " + money);
      } else {
         helper.succeed();
      }
   }

   private record TradeContext(Village village, BuildingInstance building, ServerPlayer player, BlockPos center) {
   }
}
