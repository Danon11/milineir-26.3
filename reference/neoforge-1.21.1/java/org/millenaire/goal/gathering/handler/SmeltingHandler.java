package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.millenaire.block.FirePitBlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.slf4j.Logger;

public class SmeltingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int DEFAULT_MINIMUM_TO_COOK = 3;
   private static final int MINIMUM_FUEL = 4;
   private static final int MAX_FURNACE_INPUT = 32;
   private static final int MAX_FUEL_PER_FURNACE = 16;
   @Nullable
   private static List<Item> fuelItemsCache;

   private int getMinimumToCook(GatheringType type) {
      return GsonHelper.getAsInt(type.handlerParams(), "minimumToCook", 3);
   }

   public String id() {
      return "smelting";
   }

   public void onClear() {
      fuelItemsCache = null;
   }

   public List<String> validate(GatheringType type) {
      return this.validateItemList(type, "inputs", true);
   }

   public boolean supportsRemoteAction() {
      return true;
   }

   public boolean canStart(GoalContext ctx, GatheringType type) {
      Map<Item, Integer> inputs = this.parseItemList(type.handlerParams(), "inputs");
      if (inputs.isEmpty()) {
         return false;
      }

      int minimumToCook = this.getMinimumToCook(type);

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         BuildingInventory inv = building.getInventory();
         if (inv != null) {
            int totalInputAvailable = this.countInputAvailable(ctx, building, inputs);

            for (BlockPos furnacePos : building.getFurnacePositions()) {
               if (ctx.level().getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
                  Item inputItem = inputs.keySet().iterator().next();
                  ItemStack inputSlot = furnace.getItem(0);
                  ItemStack outputSlot = furnace.getItem(2);
                  if (totalInputAvailable >= minimumToCook
                     && (inputSlot.isEmpty() || inputSlot.is(inputItem) && inputSlot.getCount() < 32)
                     && this.countFuelAvailable(ctx, building) >= 4) {
                     return true;
                  }

                  if (!outputSlot.isEmpty() && outputSlot.getCount() >= minimumToCook) {
                     return true;
                  }

                  if (!inputSlot.isEmpty() && this.hasFuelNeedForFurnace(furnace, ctx, building)) {
                     return true;
                  }
               }
            }

            Item inputItem = inputs.keySet().iterator().next();
            boolean firepitBurnable = FirePitBlockEntity.isFirePitBurnable(new ItemStack(inputItem), ctx.level());
            if (firepitBurnable) {
               for (BlockPos fpPos : building.getFirePitPositions()) {
                  if (ctx.level().getBlockEntity(fpPos) instanceof FirePitBlockEntity firePit) {
                     for (int lane = 0; lane < 3; lane++) {
                        ItemStack fpInput = firePit.getInputItem(lane);
                        ItemStack fpOutput = firePit.getOutputItem(lane);
                        if (totalInputAvailable >= minimumToCook
                           && (fpInput.isEmpty() || fpInput.is(inputItem) && fpInput.getCount() < 32)
                           && this.countFuelAvailable(ctx, building) >= 4) {
                           return true;
                        }

                        if (!fpOutput.isEmpty() && fpOutput.getCount() >= minimumToCook) {
                           return true;
                        }
                     }

                     if (this.hasFuelNeedForFirePit(firePit, ctx, building)) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   @Nullable
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      Map<Item, Integer> inputs = this.parseItemList(type.handlerParams(), "inputs");
      if (inputs.isEmpty()) {
         return null;
      }

      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos bestPos = null;
      double bestDistSq = Double.MAX_VALUE;
      Item inputItem = inputs.keySet().iterator().next();
      boolean firepitBurnable = FirePitBlockEntity.isFirePitBurnable(new ItemStack(inputItem), ctx.level());

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         for (BlockPos furnacePos : building.getFurnacePositions()) {
            if (ctx.level().getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace
               && this.furnaceNeedsAttention(ctx, type, building, furnace, inputs)) {
               double distSq = reference.distSqr(furnacePos);
               if (distSq < bestDistSq) {
                  bestDistSq = distSq;
                  bestPos = furnacePos;
               }
            }
         }

         if (firepitBurnable) {
            for (BlockPos fpPos : building.getFirePitPositions()) {
               if (ctx.level().getBlockEntity(fpPos) instanceof FirePitBlockEntity firePit && this.firePitNeedsAttention(ctx, type, building, firePit, inputs)) {
                  double distSq = reference.distSqr(fpPos);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     bestPos = fpPos;
                  }
               }
            }
         }
      }

      return bestPos != null ? new GatheringTarget.BlockTarget(bestPos) : null;
   }

   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      Map<Item, Integer> inputs = this.parseItemList(type.handlerParams(), "inputs");
      int minimumToCook = this.getMinimumToCook(type);
      BlockPos pos = target.navigationPos();
      BlockEntity be = ctx.level().getBlockEntity(pos);
      if (be instanceof FirePitBlockEntity firePit) {
         return this.performActionFirePit(ctx, type, inputs, minimumToCook, pos, firePit);
      } else if (be instanceof AbstractFurnaceBlockEntity furnace) {
         BuildingInstance ownerBuilding = this.findBuildingOwning(ctx, type, pos);
         if (ownerBuilding == null) {
            return true;
         }

         BuildingInventory inv = ownerBuilding.getInventory();
         if (inv == null) {
            return true;
         }

         ServerLevel level = ctx.level();
         ItemStack outputSlot = furnace.getItem(2);
         if (!outputSlot.isEmpty()) {
            inv.add(level, outputSlot.getItem(), outputSlot.getCount());
            furnace.setItem(2, ItemStack.EMPTY);
            furnace.setChanged();
            LOGGER.debug("Retrieved {} {} from furnace at {}", new Object[]{outputSlot.getCount(), outputSlot.getItem(), pos});
         }

         if (!inputs.isEmpty()) {
            Item inputItem = inputs.keySet().iterator().next();
            ItemStack inputSlot = furnace.getItem(0);
            int totalAvailable = this.countInputAvailable(ctx, ownerBuilding, inputs);
            if (totalAvailable >= minimumToCook) {
               if (inputSlot.isEmpty()) {
                  int toPlace = Math.min(64, totalAvailable);
                  furnace.setItem(0, new ItemStack(inputItem, toPlace));
                  this.removeInputFromSources(ctx, ownerBuilding, inputItem, toPlace, level);
                  furnace.setChanged();
               } else if (inputSlot.is(inputItem) && inputSlot.getCount() < 64 && totalAvailable > 0) {
                  int toAdd = Math.min(64 - inputSlot.getCount(), totalAvailable);
                  inputSlot.grow(toAdd);
                  furnace.setItem(0, inputSlot);
                  this.removeInputFromSources(ctx, ownerBuilding, inputItem, toAdd, level);
                  furnace.setChanged();
               }
            }
         }

         this.addFuelIfNeeded(ctx, ownerBuilding, furnace, level);
         return true;
      } else {
         return true;
      }
   }

   private boolean performActionFirePit(
      GoalContext ctx, GatheringType type, Map<Item, Integer> inputs, int minimumToCook, BlockPos pos, FirePitBlockEntity firePit
   ) {
      BuildingInstance ownerBuilding = this.findBuildingOwning(ctx, type, pos);
      if (ownerBuilding == null) {
         return true;
      }

      BuildingInventory inv = ownerBuilding.getInventory();
      if (inv == null) {
         return true;
      }

      ServerLevel level = ctx.level();
      Item inputItem = inputs.isEmpty() ? null : inputs.keySet().iterator().next();
      if (inputItem != null) {
         for (int lane = 0; lane < 3; lane++) {
            ItemStack laneInput = firePit.getInputItem(lane);
            int countGoods = this.countInputAvailable(ctx, ownerBuilding, inputs);
            if (laneInput.isEmpty() && countGoods >= minimumToCook) {
               int nb = Math.min(64, countGoods);
               firePit.setItem(0 + lane, new ItemStack(inputItem, nb));
               this.removeInputFromSources(ctx, ownerBuilding, inputItem, nb, level);
            } else if (!laneInput.isEmpty() && laneInput.is(inputItem) && laneInput.getCount() < 64 && countGoods > 0) {
               int nb = Math.min(64 - laneInput.getCount(), countGoods);
               laneInput.grow(nb);
               firePit.setItem(0 + lane, laneInput);
               this.removeInputFromSources(ctx, ownerBuilding, inputItem, nb, level);
            }
         }
      }

      for (int lane = 0; lane < 3; lane++) {
         ItemStack laneOutput = firePit.getOutputItem(lane);
         if (!laneOutput.isEmpty()) {
            inv.add(level, laneOutput.getItem(), laneOutput.getCount());
            firePit.setItem(4 + lane, ItemStack.EMPTY);
            LOGGER.debug("Retrieved {} {} from fire pit lane {} at {}", new Object[]{laneOutput.getCount(), laneOutput.getItem(), lane, pos});
         }
      }

      this.addFuelToFirePitIfNeeded(ctx, ownerBuilding, firePit, level);
      firePit.setChanged();
      return true;
   }

   @Nullable
   public BuildingInstance resolveBuildingLimitTarget(GoalContext ctx, GatheringType type) {
      List<BuildingInstance> buildings = this.resolveTargetBuildings(ctx, type);
      return buildings.isEmpty() ? null : buildings.getFirst();
   }

   private int countInputAvailable(GoalContext ctx, BuildingInstance smeltingBuilding, Map<Item, Integer> inputs) {
      int total = 0;
      ServerLevel level = ctx.level();
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null) {
         for (Item item : inputs.keySet()) {
            total += inv.getCount(level, item);
         }
      }

      for (Item item : inputs.keySet()) {
         total += ctx.villager().getInventory().getCount(item);
      }

      BuildingInstance home = this.getVillagerHome(ctx);
      if (home != null && home != smeltingBuilding) {
         BuildingInventory homeInv = home.getInventory();
         if (homeInv != null) {
            for (Item item : inputs.keySet()) {
               total += homeInv.getCount(level, item);
            }
         }
      }

      return total;
   }

   private int removeInputFromSources(GoalContext ctx, BuildingInstance smeltingBuilding, Item inputItem, int amount, ServerLevel level) {
      int remaining = amount;
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null && remaining > 0) {
         remaining -= inv.remove(level, inputItem, remaining);
      }

      if (remaining > 0) {
         int removed = Math.min(remaining, ctx.villager().getInventory().getCount(inputItem));
         ctx.villager().getInventory().remove(inputItem, removed);
         remaining -= removed;
      }

      if (remaining > 0) {
         BuildingInstance home = this.getVillagerHome(ctx);
         if (home != null && home != smeltingBuilding) {
            BuildingInventory homeInv = home.getInventory();
            if (homeInv != null) {
               remaining -= homeInv.remove(level, inputItem, remaining);
            }
         }
      }

      return amount - remaining;
   }

   private boolean hasFuelNeedForFurnace(AbstractFurnaceBlockEntity furnace, GoalContext ctx, BuildingInstance building) {
      ItemStack fuelSlot = furnace.getItem(1);
      return !fuelSlot.isEmpty() && fuelSlot.getCount() >= 32 ? false : this.countFuelAvailable(ctx, building) >= 4;
   }

   private boolean furnaceNeedsAttention(
      GoalContext ctx, GatheringType type, BuildingInstance building, AbstractFurnaceBlockEntity furnace, Map<Item, Integer> inputs
   ) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return false;
      }

      int minimumToCook = this.getMinimumToCook(type);
      Item inputItem = inputs.isEmpty() ? null : inputs.keySet().iterator().next();
      ItemStack outputSlot = furnace.getItem(2);
      if (!outputSlot.isEmpty() && outputSlot.getCount() >= minimumToCook) {
         return true;
      }

      if (inputItem != null) {
         int totalAvailable = this.countInputAvailable(ctx, building, Map.of(inputItem, 1));
         ItemStack inputSlot = furnace.getItem(0);
         if (totalAvailable >= minimumToCook && (inputSlot.isEmpty() || inputSlot.is(inputItem) && inputSlot.getCount() < 32)) {
            return true;
         }
      }

      ItemStack inputSlot = furnace.getItem(0);
      if (!inputSlot.isEmpty()) {
         ItemStack fuelSlot = furnace.getItem(1);
         if ((fuelSlot.isEmpty() || fuelSlot.getCount() < 16) && this.countFuelAvailable(ctx, building) >= 4) {
            return true;
         }
      }

      return false;
   }

   private int countFuelAvailable(GoalContext ctx, BuildingInstance building) {
      int count = 0;

      for (Item fuel : getAllFuelItems()) {
         count += this.countFuelInSource(ctx, building, fuel);
      }

      return count;
   }

   private int countFuelInSource(GoalContext ctx, BuildingInstance smeltingBuilding, Item fuel) {
      int count = 0;
      ServerLevel level = ctx.level();
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null) {
         count += inv.getCount(level, fuel);
      }

      count += ctx.villager().getInventory().getCount(fuel);
      BuildingInstance home = this.getVillagerHome(ctx);
      if (home != null && home != smeltingBuilding) {
         BuildingInventory homeInv = home.getInventory();
         if (homeInv != null) {
            count += homeInv.getCount(level, fuel);
         }
      }

      return count;
   }

   private void addFuelIfNeeded(GoalContext ctx, BuildingInstance building, AbstractFurnaceBlockEntity furnace, ServerLevel level) {
      ItemStack fuelSlot = furnace.getItem(1);
      if (fuelSlot.isEmpty()) {
         Item fuelItem = this.findBestFuel(ctx, building);
         if (fuelItem == null) {
            return;
         }

         int available = this.countFuelInSource(ctx, building, fuelItem);
         int toPlace = Math.min(16, available);
         if (toPlace < 4) {
            return;
         }

         furnace.setItem(1, new ItemStack(fuelItem, toPlace));
         this.removeFuelFromSources(ctx, building, fuelItem, toPlace, level);
         furnace.setChanged();
      } else if (fuelSlot.getCount() < 16) {
         Item fuelItem = fuelSlot.getItem();
         int available = this.countFuelInSource(ctx, building, fuelItem);
         int toAdd = Math.min(16 - fuelSlot.getCount(), available);
         if (toAdd <= 0) {
            return;
         }

         fuelSlot.grow(toAdd);
         furnace.setItem(1, fuelSlot);
         this.removeFuelFromSources(ctx, building, fuelItem, toAdd, level);
         furnace.setChanged();
      }
   }

   private void removeFuelFromSources(GoalContext ctx, BuildingInstance smeltingBuilding, Item fuel, int amount, ServerLevel level) {
      int remaining = amount;
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null && remaining > 0) {
         remaining -= inv.remove(level, fuel, remaining);
      }

      if (remaining > 0) {
         int removed = Math.min(remaining, ctx.villager().getInventory().getCount(fuel));
         ctx.villager().getInventory().remove(fuel, removed);
         remaining -= removed;
      }

      if (remaining > 0) {
         BuildingInstance home = this.getVillagerHome(ctx);
         if (home != null && home != smeltingBuilding) {
            BuildingInventory homeInv = home.getInventory();
            if (homeInv != null) {
               homeInv.remove(level, fuel, remaining);
            }
         }
      }
   }

   private static List<Item> getAllFuelItems() {
      if (fuelItemsCache != null) {
         return fuelItemsCache;
      }

      ArrayList<Item> list = new ArrayList<>();
      list.add(Items.COAL);
      list.add(Items.CHARCOAL);

      for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
         list.add((Item)holder.value());
      }

      for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOGS)) {
         list.add((Item)holder.value());
      }

      fuelItemsCache = List.copyOf(list);
      return fuelItemsCache;
   }

   @Nullable
   private Item findBestFuel(GoalContext ctx, BuildingInstance building) {
      Item best = null;
      int bestCount = 0;

      for (Item fuel : getAllFuelItems()) {
         int count = this.countFuelInSource(ctx, building, fuel);
         if (count > bestCount) {
            bestCount = count;
            best = fuel;
         }
      }

      return bestCount >= 4 ? best : null;
   }

   @Nullable
   private BuildingInstance findBuildingOwning(GoalContext ctx, GatheringType type, BlockPos furnacePos) {
      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         for (BlockPos pos : building.getFurnacePositions()) {
            if (pos.equals(furnacePos)) {
               return building;
            }
         }

         for (BlockPos pos : building.getFirePitPositions()) {
            if (pos.equals(furnacePos)) {
               return building;
            }
         }
      }

      return null;
   }

   @Nullable
   private BuildingInstance getVillagerHome(GoalContext ctx) {
      BuildingId homeId = ctx.villager().getHomeBuilding();
      return homeId == null ? null : ctx.village().getBuilding(homeId);
   }

   private boolean firePitNeedsAttention(GoalContext ctx, GatheringType type, BuildingInstance building, FirePitBlockEntity firePit, Map<Item, Integer> inputs) {
      int minimumToCook = this.getMinimumToCook(type);
      Item inputItem = inputs.isEmpty() ? null : inputs.keySet().iterator().next();

      for (int lane = 0; lane < 3; lane++) {
         ItemStack laneOutput = firePit.getOutputItem(lane);
         if (!laneOutput.isEmpty() && laneOutput.getCount() >= minimumToCook) {
            return true;
         }

         if (inputItem != null) {
            int totalAvailable = this.countInputAvailable(ctx, building, Map.of(inputItem, 1));
            ItemStack laneInput = firePit.getInputItem(lane);
            if (totalAvailable >= minimumToCook && (laneInput.isEmpty() || laneInput.is(inputItem) && laneInput.getCount() < 32)) {
               return true;
            }
         }
      }

      return this.hasFuelNeedForFirePit(firePit, ctx, building);
   }

   private boolean hasFuelNeedForFirePit(FirePitBlockEntity firePit, GoalContext ctx, BuildingInstance building) {
      boolean hasInput = false;

      for (int lane = 0; lane < 3; lane++) {
         if (!firePit.getInputItem(lane).isEmpty()) {
            hasInput = true;
            break;
         }
      }

      if (!hasInput) {
         return false;
      }

      ItemStack fuelSlot = firePit.getFuelItem();
      return fuelSlot.isEmpty() && this.countFuelAvailable(ctx, building) > 4
         ? true
         : !fuelSlot.isEmpty() && fuelSlot.getCount() < 16 && this.countFuelAvailable(ctx, building) >= 4;
   }

   private void addFuelToFirePitIfNeeded(GoalContext ctx, BuildingInstance building, FirePitBlockEntity firePit, ServerLevel level) {
      ItemStack fuelSlot = firePit.getFuelItem();
      if (fuelSlot.isEmpty()) {
         Item fuelItem = this.findBestFuel(ctx, building);
         if (fuelItem == null) {
            return;
         }

         int available = this.countFuelInSource(ctx, building, fuelItem);
         int toPlace = Math.min(16, available);
         if (toPlace < 4) {
            return;
         }

         firePit.setItem(3, new ItemStack(fuelItem, toPlace));
         this.removeFuelFromSources(ctx, building, fuelItem, toPlace, level);
      } else if (fuelSlot.getCount() < 16) {
         Item fuelItem = fuelSlot.getItem();
         int available = this.countFuelInSource(ctx, building, fuelItem);
         int toAdd = Math.min(16 - fuelSlot.getCount(), available);
         if (toAdd <= 0) {
            return;
         }

         fuelSlot.grow(toAdd);
         firePit.setItem(3, fuelSlot);
         this.removeFuelFromSources(ctx, building, fuelItem, toAdd, level);
      }
   }
}
