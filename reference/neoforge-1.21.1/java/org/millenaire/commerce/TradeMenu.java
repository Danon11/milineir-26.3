package org.millenaire.commerce;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.discovery.DiscoveryTracker;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.impl.SellerGoal;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.TradeStockUpdatePayload;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class TradeMenu extends AbstractContainerMenu {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int TOGGLE_DONATION_BUTTON_ID = Integer.MAX_VALUE;
   private static final int DONATION_REP_MULTIPLIER = 4;
   private final String villageName;
   private final String buildingName;
   private final String cultureKey;
   private final int playerReputation;
   private volatile List<TradeMenu.ClientGoodEntry> clientGoods;
   private int goodsVersion = 0;
   private boolean donationMode;
   @Nullable
   private final Village village;
   @Nullable
   private final BuildingInstance building;
   @Nullable
   private final List<TradeGood> serverGoods;
   @Nullable
   private final List<TradeMenu.GoodDirections> serverDirections;
   @Nullable
   private final BlockPos shopPos;
   @Nullable
   private ResourceLocation merchantCultureId;
   private static final ResourceLocation LOGS_TAG_ID = AnywoodHelper.LOGS_TAG.location();

   public TradeMenu(
      int containerId,
      Inventory playerInventory,
      Village village,
      BuildingInstance building,
      ShopProfile shopProfile,
      List<TradeGood> tradeCatalog,
      BlockPos sellingPos
   ) {
      super(ModMenuTypes.TRADE.get(), containerId);
      this.village = village;
      this.building = building;
      this.shopPos = sellingPos;
      this.villageName = village.getVillageName();
      this.buildingName = this.resolveBuildingName(building);
      this.cultureKey = village.getCultureId().getPath();
      if (playerInventory.player.level() instanceof ServerLevel sl) {
         this.playerReputation = village.getCombinedReputation(sl, playerInventory.player.getUUID());
         this.donationMode = PlayerCultureReputation.get(sl).isDonationMode(playerInventory.player.getUUID());
      } else {
         this.playerReputation = village.getReputation().get(playerInventory.player.getUUID());
      }

      this.serverGoods = this.resolveGoods(shopProfile, tradeCatalog, playerInventory);
      this.serverDirections = this.resolveDirections(shopProfile, this.serverGoods, playerInventory);
      this.clientGoods = this.buildClientEntries(this.serverGoods, this.serverDirections, building, playerInventory.player);
      if ((Boolean)MillenaireServerConfig.SERVER.travelBookLearning.get() && playerInventory.player.level() instanceof ServerLevel sl) {
         String cultureKey = village.getCultureId().getPath();
         List<String> goodKeys = this.serverGoods.stream().map(TradeGood::id).toList();
         DiscoveryTracker tracker = DiscoveryTracker.get(sl);
         List<Boolean> results = tracker.unlockTradeGoods(playerInventory.player.getUUID(), cultureKey, goodKeys);
         if (playerInventory.player instanceof ServerPlayer sp) {
            for (int i = 0; i < results.size(); i++) {
               if (results.get(i)) {
                  TradeGood good = this.serverGoods.get(i);
                  Item resolvedItem = good.resolveItem();
                  Component goodName = (Component)(resolvedItem != null ? new ItemStack(resolvedItem).getHoverName() : Component.literal(good.id()));
                  sp.sendSystemMessage(Component.translatable("travelbook.discovered.trade_good", new Object[]{goodName}));
               }
            }
         }
      }

      this.addPlayerInventorySlots(playerInventory, 140);
   }

   public static TradeMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
      return new TradeMenu(containerId, playerInventory, buf);
   }

   private TradeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
      super(ModMenuTypes.TRADE.get(), containerId);
      this.village = null;
      this.building = null;
      this.serverGoods = null;
      this.serverDirections = null;
      this.shopPos = null;
      this.villageName = buf.readUtf();
      this.buildingName = buf.readUtf();
      this.cultureKey = buf.readUtf();
      this.playerReputation = buf.readVarInt();
      this.donationMode = buf.readBoolean();
      int goodCount = buf.readVarInt();
      List<TradeMenu.ClientGoodEntry> goods = new ArrayList<>(goodCount);

      for (int i = 0; i < goodCount; i++) {
         String id = buf.readUtf();
         ResourceLocation itemLoc = buf.readResourceLocation();
         Item item = ItemHelper.resolve(itemLoc);
         int sellingPrice = buf.readVarInt();
         int buyingPrice = buf.readVarInt();
         int stock = buf.readVarInt();
         int minReputation = buf.readVarInt();
         boolean autoGenerate = buf.readBoolean();
         boolean isSelling = buf.readBoolean();
         String category = buf.readUtf();
         boolean hasTag = buf.readBoolean();
         String tagId = hasTag ? buf.readUtf() : null;
         int targetQuantity = buf.readVarInt();
         if (item != null) {
            goods.add(
               new TradeMenu.ClientGoodEntry(
                  id, item, sellingPrice, buyingPrice, stock, minReputation, autoGenerate, isSelling, category, tagId, targetQuantity
               )
            );
         }
      }

      this.clientGoods = new ArrayList<>(goods);
      this.addPlayerInventorySlots(playerInventory, 140);
   }

   private void addPlayerInventorySlots(Inventory playerInventory, int yOffset) {
      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 44 + col * 18, yOffset + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, 44 + col * 18, yOffset + 58));
      }
   }

   public void writeToBuffer(FriendlyByteBuf buf) {
      buf.writeUtf(this.villageName);
      buf.writeUtf(this.buildingName);
      buf.writeUtf(this.cultureKey);
      buf.writeVarInt(this.playerReputation);
      buf.writeBoolean(this.donationMode);
      buf.writeVarInt(this.clientGoods.size());

      for (TradeMenu.ClientGoodEntry entry : this.clientGoods) {
         buf.writeUtf(entry.id());
         buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(entry.item()));
         buf.writeVarInt(entry.sellingPrice());
         buf.writeVarInt(entry.buyingPrice());
         buf.writeVarInt(entry.stock());
         buf.writeVarInt(entry.minReputation());
         buf.writeBoolean(entry.autoGenerate());
         buf.writeBoolean(entry.isSelling());
         buf.writeUtf(entry.category());
         buf.writeBoolean(entry.tagId() != null);
         if (entry.tagId() != null) {
            buf.writeUtf(entry.tagId());
         }

         buf.writeVarInt(entry.targetQuantity());
      }
   }

   public String getVillageName() {
      return this.villageName;
   }

   public String getBuildingName() {
      return this.buildingName;
   }

   public String getCultureKey() {
      return this.cultureKey;
   }

   public int getPlayerReputation() {
      return this.playerReputation;
   }

   public List<TradeMenu.ClientGoodEntry> getClientGoods() {
      return this.clientGoods;
   }

   public int getGoodsVersion() {
      return this.goodsVersion;
   }

   public boolean isDonationMode() {
      return this.donationMode;
   }

   public void setMerchantCultureId(@Nullable ResourceLocation cultureId) {
      this.merchantCultureId = cultureId;
   }

   private ResourceLocation getEffectiveCultureId() {
      return this.merchantCultureId != null ? this.merchantCultureId : this.village.getCultureId();
   }

   public void toggleDonationModeClient() {
      this.donationMode = !this.donationMode;
   }

   public int getGoodsCount() {
      return this.clientGoods != null ? this.clientGoods.size() : 0;
   }

   public static int getToggleDonationButtonId() {
      return Integer.MAX_VALUE;
   }

   public void updateStocks(List<Integer> newStocks, boolean newDonationMode) {
      this.donationMode = newDonationMode;
      if (newStocks.size() != this.clientGoods.size()) {
         LOGGER.warn("TradeStockUpdate : inconsistent size (received={}, expected={})", newStocks.size(), this.clientGoods.size());
      } else {
         List<TradeMenu.ClientGoodEntry> updated = new ArrayList<>(this.clientGoods.size());

         for (int i = 0; i < this.clientGoods.size(); i++) {
            TradeMenu.ClientGoodEntry old = this.clientGoods.get(i);
            updated.add(
               new TradeMenu.ClientGoodEntry(
                  old.id(),
                  old.item(),
                  old.sellingPrice(),
                  old.buyingPrice(),
                  newStocks.get(i),
                  old.minReputation(),
                  old.autoGenerate(),
                  old.isSelling(),
                  old.category(),
                  old.tagId(),
                  old.targetQuantity()
               )
            );
         }

         this.clientGoods = updated;
         this.goodsVersion++;
      }
   }

   private void sendStockUpdate(Player player) {
      if (player instanceof ServerPlayer sp) {
         if (this.serverGoods != null && this.serverDirections != null && this.building != null) {
            List<Integer> stocks = new ArrayList<>(this.clientGoods.size());

            for (int i = 0; i < this.serverGoods.size(); i++) {
               TradeGood good = this.serverGoods.get(i);
               TradeMenu.GoodDirections dir = this.serverDirections.get(i);
               int stock = 0;
               int available = 0;
               if (!good.autoGenerate()) {
                  BuildingInventory inv = this.building.getInventory();
                  if (inv != null) {
                     stock = this.countStockForGood(good, inv, player.level());
                     if (dir.buy() && player.level() instanceof ServerLevel sl) {
                        available = this.computeAvailableForBuyCheck(good, inv, sl);
                     }
                  }
               }

               if (dir.sell()) {
                  stocks.add(stock);
               }

               if (dir.buy()) {
                  stocks.add(good.autoGenerate() ? stock : available);
               }
            }

            TradeStockUpdatePayload payload = new TradeStockUpdatePayload(this.containerId, stocks, this.donationMode);

            try {
               if (sp.connection != null) {
                  sp.connection.send(new ClientboundCustomPayloadPacket(payload));
               }
            } catch (Exception e) {
               LOGGER.warn("Unable to send stock update : {}", e.getMessage());
            }
         }
      }
   }

   private int resolvePrice(TradeGood good, boolean isSelling) {
      if (this.village != null) {
         VillageType vt = ModCultures.getVillageType(this.village.getVillageTypeId());
         if (vt != null) {
            Map<String, Integer> overrides = isSelling ? vt.sellingPriceOverrides() : vt.buyingPriceOverrides();
            if (overrides.containsKey(good.id())) {
               return overrides.get(good.id());
            }
         }
      }

      return isSelling ? good.sellingPrice() : good.buyingPrice();
   }

   public boolean clickMenuButton(Player player, int buttonId) {
      if (this.serverGoods == null || this.village == null || this.building == null) {
         return false;
      }

      if (buttonId == Integer.MAX_VALUE) {
         this.donationMode = !this.donationMode;
         if (player.level() instanceof ServerLevel sl) {
            PlayerCultureReputation.get(sl).setDonationMode(player.getUUID(), this.donationMode);
         }

         this.sendStockUpdate(player);
         return true;
      } else {
         TradeAction.DecodedButton decoded = TradeAction.decodeButtonId(buttonId);
         if (decoded == null) {
            return false;
         }

         int clientIndex = decoded.goodIndex();
         TradeAction action = decoded.action();
         if (clientIndex >= 0 && clientIndex < this.clientGoods.size()) {
            TradeMenu.ResolvedEntry resolved = this.resolveEntryByClientIndex(clientIndex);
            if (resolved == null) {
               return false;
            }

            TradeGood good = resolved.good();
            boolean requestingBuyFromShop = action.isBuyFromShop();
            boolean directionAllowed = requestingBuyFromShop ? resolved.isSellingEntry() : !resolved.isSellingEntry();
            if (!directionAllowed) {
               LOGGER.warn(
                  "Rejected trade action: good '{}' has no declared direction for action {} (clientIndex={}, buttonId={}, player={})",
                  new Object[]{good.id(), action, clientIndex, buttonId, player.getName().getString()}
               );
               return false;
            }

            int quantity = action.quantity();
            if (requestingBuyFromShop) {
               this.executeBuy(player, good, quantity);
            } else {
               this.executeSell(player, good, quantity);
            }

            this.sendStockUpdate(player);
            return true;
         } else {
            return false;
         }
      }
   }

   private void executeBuy(Player player, TradeGood good, int requestedQty) {
      if (good.canSell()) {
         if (this.village != null && this.building != null) {
            int playerMoney = MoneyHelper.getTotalDeniers(player.getInventory());
            int price = this.resolvePrice(good, true);
            if (price > 0) {
               int rep;
               if (player.level() instanceof ServerLevel sl) {
                  rep = this.village.getCombinedReputation(sl, player.getUUID());
               } else {
                  rep = this.village.getReputation().get(player.getUUID());
               }

               if (rep < good.minReputation()) {
                  LOGGER.debug("Insufficient reputation to buy {} (required={}, actual={})", new Object[]{good.id(), good.minReputation(), rep});
               } else {
                  int maxByMoney = playerMoney / price;
                  int maxByStock = requestedQty;
                  if (!good.autoGenerate()) {
                     BuildingInventory inv = this.building.getInventory();
                     if (inv != null) {
                        maxByStock = this.countStockForGood(good, inv, player.level());
                     } else {
                        maxByStock = 0;
                     }
                  }

                  Item item = good.resolveItem();
                  if (item != null) {
                     int maxBySpace = this.countAvailableSpace(player.getInventory(), item);
                     int actual = Math.min(requestedQty, Math.min(maxByMoney, Math.min(maxByStock, maxBySpace)));
                     if (actual > 0) {
                        int totalCost = price * actual;
                        if (MoneyHelper.removeDeniers(player.getInventory(), totalCost)) {
                           if (!good.autoGenerate()) {
                              BuildingInventory inv = this.building.getInventory();
                              if (inv != null) {
                                 this.removeStockForGood(good, inv, player.level(), actual);
                              }
                           }

                           this.giveItems(player, item, actual);
                           if (player.level() instanceof ServerLevel serverLevel) {
                              this.village.adjustReputation(serverLevel, player.getUUID(), totalCost);
                           }

                           if (player.level() instanceof ServerLevel sl) {
                              PlayerCultureReputation.get(sl).addLanguageKnowledge(player.getUUID(), this.getEffectiveCultureId(), actual);
                           }

                           LOGGER.debug("Purchase: {} x{} for {} deniers by {}", new Object[]{good.id(), actual, totalCost, player.getName().getString()});
                           this.markActiveSellerTraded();
                           if (player instanceof ServerPlayer sp) {
                              sp.playNotifySound((SoundEvent)SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                           }

                           this.broadcastChanges();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void executeSell(Player player, TradeGood good, int requestedQty) {
      if (good.canBuy()) {
         if (this.village != null && this.building != null) {
            int playerCount = this.countPlayerItems(player.getInventory(), good);
            int actual = Math.min(requestedQty, playerCount);
            if (actual > 0) {
               if (good.targetQuantity() > 0 && this.building != null && player.level() instanceof ServerLevel sl) {
                  BuildingInventory inv = this.building.getInventory();
                  if (inv != null) {
                     int rawCount = this.countStockForGood(good, inv, sl);
                     int constructionNeeds = 0;
                     if (!good.isTag()) {
                        Item asItem = good.resolveItem();
                        if (asItem != null) {
                           constructionNeeds = GoodAvailabilityHelper.getConstructionReservedQuantity(asItem, sl, this.village, null);
                        }
                     }

                     int effectiveCap = good.targetQuantity() + constructionNeeds;
                     int remaining = effectiveCap - rawCount;
                     if (remaining <= 0) {
                        return;
                     }

                     actual = Math.min(actual, remaining);
                  }
               }

               Item concreteItem = this.findConcreteItem(player.getInventory(), good);
               if (concreteItem != null) {
                  int buyPrice = this.resolvePrice(good, false);
                  if (buyPrice > 0 || this.donationMode) {
                     this.removePlayerItems(player.getInventory(), good, actual);
                     BuildingInventory inv = this.building.getInventory();
                     if (inv != null) {
                        int var19 = inv.add(player.level(), concreteItem, actual);
                        if (var19 < actual) {
                           this.giveItems(player, concreteItem, actual - var19);
                           actual = var19;
                        }
                     }

                     if (actual > 0) {
                        int totalRevenue = buyPrice * actual;
                        boolean donating = this.donationMode;
                        if (!donating) {
                           MoneyHelper.addDeniers(player.getInventory(), totalRevenue, player);
                        }

                        int repMultiplier = donating ? 4 : 1;
                        if (player.level() instanceof ServerLevel serverLevel) {
                           this.village.adjustReputation(serverLevel, player.getUUID(), totalRevenue * repMultiplier);
                        }

                        if (player.level() instanceof ServerLevel sl) {
                           PlayerCultureReputation.get(sl).addLanguageKnowledge(player.getUUID(), this.getEffectiveCultureId(), actual);
                        }

                        LOGGER.debug(
                           "Sale{} : {} x{} for {} deniers by {}",
                           new Object[]{donating ? " (donation)" : "", good.id(), actual, totalRevenue, player.getName().getString()}
                        );
                        this.markActiveSellerTraded();
                        if (player instanceof ServerPlayer sp) {
                           sp.playNotifySound((SoundEvent)SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                        }

                        this.broadcastChanges();
                     }
                  }
               }
            }
         }
      }
   }

   private void markActiveSellerTraded() {
      if (this.village != null && this.shopPos != null) {
         MillVillager seller = this.village.getActiveSellers().get(this.shopPos);
         if (seller != null) {
            GoalScheduler scheduler = seller.getGoalScheduler();
            if (scheduler != null) {
               if (scheduler.getCurrentTask() instanceof SellerGoal.SellerTask task) {
                  task.markTraded();
               }
            }
         }
      }
   }

   private int computeAvailableForBuyCheck(TradeGood good, BuildingInventory inv, ServerLevel level) {
      if (this.village != null && this.building != null) {
         ResourceLocation cultureId = this.village.getCultureId();
         if (!good.isTag()) {
            Item item = good.resolveItem();
            return item == null ? 0 : GoodAvailabilityHelper.nbGoodAvailable(this.building, item, level, this.village, cultureId, false, false);
         }

         int total = this.countStockForGood(good, inv, level);
         if (LOGS_TAG_ID.equals(good.itemLocation())) {
            total -= GoodAvailabilityHelper.getAnywoodReservedQuantity(level, this.village, null);
         }

         return Math.max(total, 0);
      } else {
         return 0;
      }
   }

   private int countStockForGood(TradeGood good, BuildingInventory inv, Level level) {
      if (!good.isTag()) {
         Item item = good.resolveItem();
         return item == null ? 0 : inv.getCount(level, item);
      }

      Map<Item, Integer> cache = inv.getCachedContents();
      if (cache == null) {
         inv.scanChests(level);
         cache = inv.getCachedContents();
      }

      if (cache == null) {
         return 0;
      }

      TagKey<Item> tag = TagKey.create(Registries.ITEM, good.itemLocation());
      int total = 0;

      for (Entry<Item, Integer> entry : cache.entrySet()) {
         if (entry.getKey().builtInRegistryHolder().is(tag)) {
            total += entry.getValue();
         }
      }

      return total;
   }

   private void removeStockForGood(TradeGood good, BuildingInventory inv, Level level, int count) {
      if (!good.isTag()) {
         Item item = good.resolveItem();
         if (item != null) {
            inv.remove(level, item, count);
         }
      } else {
         TagKey<Item> tag = TagKey.create(Registries.ITEM, good.itemLocation());
         int remaining = count;
         Map<Item, Integer> cache = inv.getCachedContents();
         if (cache == null) {
            inv.scanChests(level);
            cache = inv.getCachedContents();
         }

         if (cache != null) {
            for (Entry<Item, Integer> entry : cache.entrySet()) {
               if (remaining <= 0) {
                  break;
               }

               if (entry.getKey().builtInRegistryHolder().is(tag)) {
                  int toRemove = Math.min(remaining, entry.getValue());
                  inv.remove(level, entry.getKey(), toRemove);
                  remaining -= toRemove;
               }
            }
         }
      }
   }

   private int countPlayerItems(Inventory inventory, TradeGood good) {
      int count = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (good.matchesItem(stack) && !isDamaged(stack)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   @Nullable
   private Item findConcreteItem(Inventory inventory, TradeGood good) {
      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (good.matchesItem(stack) && !isDamaged(stack)) {
            return stack.getItem();
         }
      }

      return good.resolveItem();
   }

   private void removePlayerItems(Inventory inventory, TradeGood good, int count) {
      int remaining = count;

      for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
         ItemStack stack = inventory.getItem(i);
         if (good.matchesItem(stack) && !isDamaged(stack)) {
            int toRemove = Math.min(remaining, stack.getCount());
            stack.shrink(toRemove);
            if (stack.isEmpty()) {
               inventory.setItem(i, ItemStack.EMPTY);
            }

            remaining -= toRemove;
         }
      }
   }

   private static boolean isDamaged(ItemStack stack) {
      return stack.isDamageableItem() && stack.isDamaged();
   }

   private int countBuildingSpace(BuildingInventory inv, Level level, Item item) {
      Map<Item, Integer> cache = inv.getCachedContents();
      if (cache == null) {
         inv.scanChests(level);
         cache = inv.getCachedContents();
      }

      if (cache == null) {
         return 64;
      }

      int maxStackSize = item.getDefaultMaxStackSize();
      int totalItems = cache.values().stream().mapToInt(Integer::intValue).sum();
      int totalSlots = inv.getChestCount() * 27;
      int totalCapacity = totalSlots * maxStackSize;
      return Math.max(0, totalCapacity - totalItems);
   }

   private int countAvailableSpace(Inventory inventory, Item item) {
      int space = 0;
      int maxStackSize = item.getDefaultMaxStackSize();

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.isEmpty()) {
            space += maxStackSize;
         } else if (stack.is(item) && stack.getCount() < maxStackSize) {
            space += maxStackSize - stack.getCount();
         }
      }

      return space;
   }

   private void giveItems(Player player, Item item, int count) {
      int remaining = count;
      int maxStackSize = item.getDefaultMaxStackSize();

      while (remaining > 0) {
         int toGive = Math.min(remaining, maxStackSize);
         ItemStack stack = new ItemStack(item, toGive);
         if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
         }

         remaining -= toGive;
      }
   }

   @Nullable
   private TradeMenu.ResolvedEntry resolveEntryByClientIndex(int clientIndex) {
      if (this.serverGoods != null && this.serverDirections != null) {
         int running = 0;

         for (int i = 0; i < this.serverGoods.size(); i++) {
            TradeMenu.GoodDirections dir = this.serverDirections.get(i);
            if (dir.sell()) {
               if (running == clientIndex) {
                  return new TradeMenu.ResolvedEntry(this.serverGoods.get(i), true);
               }

               running++;
            }

            if (dir.buy()) {
               if (running == clientIndex) {
                  return new TradeMenu.ResolvedEntry(this.serverGoods.get(i), false);
               }

               running++;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private List<TradeGood> resolveGoods(ShopProfile shopProfile, List<TradeGood> catalog, Inventory playerInventory) {
      List<TradeGood> goods = new ArrayList<>();

      for (String sellId : shopProfile.sells()) {
         TradeGood good = this.findGoodById(catalog, sellId);
         if (good != null && good.canSell() && good.resolveItem() != null) {
            goods.add(good);
         }
      }

      for (String buyId : shopProfile.buys()) {
         TradeGood good = this.findGoodById(catalog, buyId);
         if (good != null && good.canBuy() && good.resolveItem() != null && !goods.contains(good)) {
            goods.add(good);
         }
      }

      for (String optId : shopProfile.buysOptional()) {
         TradeGood good = this.findGoodById(catalog, optId);
         if (good != null && good.canBuy() && good.resolveItem() != null && !goods.contains(good) && this.playerHasItem(playerInventory, good)) {
            goods.add(good);
         }
      }

      this.injectConstructionNeedsIfTownhall(goods, catalog);
      return goods;
   }

   private void injectConstructionNeedsIfTownhall(List<TradeGood> goods, List<TradeGood> catalog) {
      if (this.village != null && this.building != null) {
         BuildingPlan plan = ModCultures.getBuildingPlan(this.building.getPlanId());
         boolean isTownhall = plan != null && "townhall".equals(plan.shopId());
         if (isTownhall) {
            Set<ResourceLocation> needed = GoodAvailabilityHelper.collectConstructionNeedItems(this.village);
            if (!needed.isEmpty()) {
               Set<String> alreadyByItemId = new HashSet<>();

               for (TradeGood g : goods) {
                  if (!g.isTag()) {
                     alreadyByItemId.add(g.itemLocation().toString());
                  }
               }

               for (ResourceLocation itemId : needed) {
                  String itemStr = itemId.toString();
                  if (!alreadyByItemId.contains(itemStr)) {
                     Item item = ItemHelper.resolve(itemStr);
                     if (item != null) {
                        TradeGood existing = this.findGoodByItemId(catalog, itemStr);
                        if (existing != null && existing.canBuy()) {
                           goods.add(existing);
                           alreadyByItemId.add(itemStr);
                        } else {
                           String synthId = "_construction_" + itemId.getPath();
                           TradeGood synth = new TradeGood(synthId, itemStr, 0, 1, 0, 0, false, 0, "construction", false, 0);
                           goods.add(synth);
                           alreadyByItemId.add(itemStr);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private TradeGood findGoodByItemId(List<TradeGood> catalog, String itemId) {
      for (TradeGood good : catalog) {
         if (!good.isTag() && itemId.equals(good.itemLocation().toString())) {
            return good;
         }
      }

      return null;
   }

   private List<TradeMenu.GoodDirections> resolveDirections(ShopProfile shopProfile, List<TradeGood> goods, Inventory playerInventory) {
      Set<String> sellsIds = new HashSet<>(shopProfile.sells());
      Set<String> buysIds = new HashSet<>(shopProfile.buys());
      Set<String> optionalIds = new HashSet<>(shopProfile.buysOptional());
      List<TradeMenu.GoodDirections> result = new ArrayList<>(goods.size());

      for (TradeGood good : goods) {
         boolean sellDir = sellsIds.contains(good.id()) && good.canSell();
         boolean inAnyShopList = sellsIds.contains(good.id()) || buysIds.contains(good.id()) || optionalIds.contains(good.id());
         boolean buyDir = (buysIds.contains(good.id()) || optionalIds.contains(good.id()) && this.playerHasItem(playerInventory, good) || !inAnyShopList)
            && good.canBuy();
         result.add(new TradeMenu.GoodDirections(good, sellDir, buyDir));
      }

      return result;
   }

   private boolean playerHasItem(Inventory inventory, TradeGood good) {
      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && good.matchesItem(stack)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   private TradeGood findGoodById(List<TradeGood> catalog, String id) {
      for (TradeGood good : catalog) {
         if (good.id().equals(id)) {
            return good;
         }
      }

      return null;
   }

   private List<TradeMenu.ClientGoodEntry> buildClientEntries(
      List<TradeGood> goods, List<TradeMenu.GoodDirections> directions, BuildingInstance building, Player player
   ) {
      List<TradeMenu.ClientGoodEntry> entries = new ArrayList<>();
      BuildingInventory inv = building.getInventory();

      for (int i = 0; i < goods.size(); i++) {
         TradeGood good = goods.get(i);
         TradeMenu.GoodDirections dir = directions.get(i);
         Item item = good.resolveItem();
         if (item != null) {
            int stock = 0;
            if (!good.autoGenerate() && inv != null) {
               stock = this.countStockForGood(good, inv, player.level());
            }

            int sellingPrice = this.resolvePrice(good, true);
            int buyingPrice = this.resolvePrice(good, false);
            String tagId = good.isTag() ? good.item() : null;
            if (dir.sell()) {
               entries.add(
                  new TradeMenu.ClientGoodEntry(
                     good.id(),
                     item,
                     sellingPrice,
                     buyingPrice,
                     stock,
                     good.minReputation(),
                     good.autoGenerate(),
                     true,
                     good.category(),
                     tagId,
                     good.targetQuantity()
                  )
               );
            }

            if (dir.buy()) {
               entries.add(
                  new TradeMenu.ClientGoodEntry(
                     good.id(),
                     item,
                     sellingPrice,
                     buyingPrice,
                     stock,
                     good.minReputation(),
                     good.autoGenerate(),
                     false,
                     good.category(),
                     tagId,
                     good.targetQuantity()
                  )
               );
            }
         }
      }

      return Collections.unmodifiableList(entries);
   }

   private String resolveBuildingName(BuildingInstance building) {
      ResourceLocation planSetId = building.getPlanSetId();
      if (planSetId != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
         if (planSet != null) {
            String variant = building.getVariant();
            if (variant != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, building.getLevel());
               if (levelDef != null && levelDef.nativeName() != null) {
                  return levelDef.nativeName();
               }
            }

            return planSet.nativeName();
         }
      }

      return building.getPlanId().getPath();
   }

   public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
   }

   public boolean stillValid(Player player) {
      if (this.shopPos == null) {
         return true;
      }

      double dx = player.getX() - (this.shopPos.getX() + 0.5);
      double dz = player.getZ() - (this.shopPos.getZ() + 0.5);
      return dx * dx + dz * dz <= 256.0;
   }

   public record ClientGoodEntry(
      String id,
      Item item,
      int sellingPrice,
      int buyingPrice,
      int stock,
      int minReputation,
      boolean autoGenerate,
      boolean isSelling,
      String category,
      @Nullable String tagId,
      int targetQuantity
   ) {
   }

   private record GoodDirections(TradeGood good, boolean sell, boolean buy) {
   }

   private record ResolvedEntry(TradeGood good, boolean isSellingEntry) {
   }
}
