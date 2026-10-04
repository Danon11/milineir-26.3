package org.millenaire.test;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.GameTestPlayer;
import org.millenaire.item.ModItems;
import org.millenaire.item.VillageBookItem;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class VillageBookGameTests {
   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testVillageBook_validVillage(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
      VillageId villageId = VillageId.random();
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", "norman/agricole");
      Village village = new Village(villageId, cultureId, villageTypeId, center);
      village.setVillageName("TestVillage");
      VillageSavedData.get(level).getVillageManager().addVillage(village);
      ItemStack scroll = new ItemStack((ItemLike)ModItems.VILLAGE_SCROLL.get());
      CompoundTag tag = new CompoundTag();
      tag.putString("village_id", villageId.uuid().toString());
      tag.putString("village_name", "TestVillage");
      scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      InteractionResultHolder<ItemStack> result = ((VillageBookItem)ModItems.VILLAGE_SCROLL.get()).use(level, player, InteractionHand.MAIN_HAND);
      if (result.getResult() != InteractionResult.SUCCESS) {
         helper.fail("Expected SUCCESS for valid village, got " + result);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testVillageBook_noNbt(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      ItemStack scroll = new ItemStack((ItemLike)ModItems.VILLAGE_SCROLL.get());
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      InteractionResultHolder<ItemStack> result = ((VillageBookItem)ModItems.VILLAGE_SCROLL.get()).use(level, player, InteractionHand.MAIN_HAND);
      if (result.getResult() != InteractionResult.FAIL) {
         helper.fail("Expected FAIL for scroll without NBT, got " + result);
      } else {
         helper.succeed();
      }
   }

   @GameTest(template = "empty_platform", setupTicks = 20L, timeoutTicks = 200)
   public static void testVillageBook_nonexistentVillage(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      UUID unknownUuid = UUID.randomUUID();
      ItemStack scroll = new ItemStack((ItemLike)ModItems.VILLAGE_SCROLL.get());
      CompoundTag tag = new CompoundTag();
      tag.putString("village_id", unknownUuid.toString());
      tag.putString("village_name", "GhostVillage");
      scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      GameTestPlayer player = GameTestPlayers.create(helper, GameType.SURVIVAL);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      InteractionResultHolder<ItemStack> result = ((VillageBookItem)ModItems.VILLAGE_SCROLL.get()).use(level, player, InteractionHand.MAIN_HAND);
      if (result.getResult() != InteractionResult.FAIL) {
         helper.fail("Expected FAIL for nonexistent village UUID, got " + result);
      } else {
         helper.succeed();
      }
   }
}
