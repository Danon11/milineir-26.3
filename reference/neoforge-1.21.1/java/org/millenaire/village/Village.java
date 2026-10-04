package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.TickConstants;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ConstructionTask;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.item.ModItems;
import org.millenaire.network.FireplacePositionsPayload;
import org.millenaire.quest.MarvelManager;
import org.millenaire.village.path.VillagePathManager;
import org.millenaire.world.PlacedLocation;
import org.slf4j.Logger;

public class Village {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int INTEGRITY_CHECK_INTERVAL = 600;
   private static final int RESERVATION_EXPIRY_TICKS = 200;
   private static final int PANEL_UPDATE_INTERVAL = 40;
   private final VillageId id;
   private final ResourceLocation cultureId;
   private final ResourceLocation villageTypeId;
   private final BlockPos center;
   private String villageName;
   private final VillageReputation reputation = new VillageReputation();
   private final Map<VillageId, Integer> relations = new ConcurrentHashMap<>();
   private final ResidentSlotManager residentSlotManager = new ResidentSlotManager(this);
   private final VillageWaypointGraph waypointGraph = new VillageWaypointGraph();
   private final VillagePathManager pathManager = new VillagePathManager();
   private final List<BuildingInstance> buildings = new ArrayList<>();
   private final Map<UUID, VillagerRecord> villagerRecords = new LinkedHashMap<>();
   private int cachedDefenderCount = -1;
   private final Map<UUID, Integer> missingCounts = new HashMap<>();
   private int integrityTickCounter;
   private int growthTickCounter;
   private long noProjectsLeftUntil;
   private final Map<ResourceLocation, Long> placementCooldowns = new HashMap<>();
   @Nullable
   private Village.PendingProject pendingProject;
   private final Set<ResourceLocation> buildingsBought = new HashSet<>();
   @Nullable
   private BrickColourTheme brickTheme;
   private static final long DUSK_TIME = 13000L;
   private long lastNightActionDay = -1L;
   @Nullable
   private VillageId parentVillageId;
   @Nullable
   private UUID ownerUUID;
   @Nullable
   private String ownerName;
   @Nullable
   private String bannerNbt;
   private static final int MAX_CHRONICLE_SIZE = 500;
   private static final int MAX_HISTORY_SIZE = 1000;
   private final List<VillageEvent> chronicle = new ArrayList<>();
   private final List<VillageHistoryEntry> history = new ArrayList<>();
   private long historyStartTick = -1L;
   private int panelTickCounter;
   private long lastGoodsRefresh;
   private transient long lastWaypointRebuildTick = Long.MIN_VALUE;
   private transient int waypointRebuildBackoff;
   private transient long lastWaypointRebuildRequestTick = Long.MIN_VALUE;
   private static final long WAYPOINT_REBUILD_BACKOFF_WINDOW = 600L;
   private static final int WAYPOINT_REBUILD_BACKOFF_CAP = 6;
   private static final long WAYPOINT_REBUILD_BACKOFF_MAX_TICKS = 1200L;
   private boolean restockNightActionDone;
   private boolean allBedManagersInitialized;
   @Nullable
   private transient AABB dangerousMobsArea;
   @Nullable
   private transient Map<String, List<BuildingInstance>> buildingsByTag;
   private static final long RESTOCK_INTERVAL_LOCKED = 20L;
   private static final long RESTOCK_INTERVAL_UNLOCKED = 100L;
   private final transient Map<BlockPos, MillVillager> activeSellers = new HashMap<>();
   @Nullable
   private transient Map<Item, Integer> importsNeededCache;
   private transient long importsNeededCacheExpiry;
   @Nullable
   private transient Map<String, Integer> villageStockCache;
   private transient long villageStockCacheTick = -1L;
   private static final long VILLAGE_STOCK_CACHE_TTL_TICKS = 60L;
   @Nullable
   private MarvelManager marvelManager;
   private boolean chestLocked = false;
   private boolean dirty;
   static final int CHUNK_MARGIN = 1;
   private boolean active = false;
   private boolean chunksForceLoaded = false;
   private boolean forceActive = false;
   private Set<ChunkPos> loadedChunks = Set.of();
   private boolean chunksNeedRefresh = false;
   private boolean needsOrphanCleanup = false;
   @Nullable
   private String rawBrickThemeName;
   private transient Map<UUID, ResourceLocation> villagerTypesCache;

   static int getKeepActiveRadius() {
      return MillenaireServerConfig.SERVER.keepActiveRadius.getAsInt();
   }

   static int getUnloadRadius() {
      return MillenaireServerConfig.SERVER.keepActiveRadius.getAsInt() + 32;
   }

   public Village(VillageId id, ResourceLocation cultureId, ResourceLocation villageTypeId, BlockPos center) {
      this.id = id;
      this.cultureId = cultureId;
      this.villageTypeId = villageTypeId;
      this.center = center;
   }

   @Nullable
   public static Village resolve(ServerLevel level, VillageId villageId) {
      return level != null && villageId != null ? VillageSavedData.get(level).getVillageManager().getVillage(villageId) : null;
   }

   public VillageId getId() {
      return this.id;
   }

   public String getVillageName() {
      return this.villageName;
   }

   public void setVillageName(String villageName) {
      this.villageName = villageName;
   }

   public ResourceLocation getCultureId() {
      return this.cultureId;
   }

   public int getRelation(VillageId otherId) {
      return this.relations.getOrDefault(otherId, 0);
   }

   public void setRelation(VillageId otherId, int value) {
      int clamped = Math.max(-100, Math.min(100, value));
      this.relations.put(otherId, clamped);
      this.markDirty();
   }

   public void adjustRelation(VillageId otherId, int delta, boolean reset) {
      if (reset) {
         this.setRelation(otherId, delta);
      } else {
         int current = this.getRelation(otherId);
         this.setRelation(otherId, current + delta);
      }
   }

   public void adjustRelationSymmetric(ServerLevel level, VillageId targetId, int change, boolean reset) {
      int currentValue = this.getRelation(targetId);
      int newValue = reset ? change : currentValue + change;
      newValue = Math.max(-100, Math.min(100, newValue));
      this.relations.put(targetId, newValue);
      this.markDirty();
      Village other = resolve(level, targetId);
      if (other != null && !other.getId().equals(this.id)) {
         other.relations.put(this.id, newValue);
         other.markDirty();
      }
   }

   public void removeRelation(VillageId otherId) {
      if (this.relations.remove(otherId) != null) {
         this.markDirty();
      }
   }

   public Map<VillageId, Integer> getRelations() {
      return Collections.unmodifiableMap(this.relations);
   }

   void setRelationsFromNbt(Map<VillageId, Integer> loaded) {
      this.relations.clear();
      this.relations.putAll(loaded);
   }

   public ResourceLocation getVillageTypeId() {
      return this.villageTypeId;
   }

   public BlockPos getCenter() {
      return this.center;
   }

   public VillageReputation getReputation() {
      return this.reputation;
   }

   public int getCombinedReputation(ServerLevel level, UUID playerId) {
      int villageRep = this.reputation.get(playerId);
      int cultureRep = PlayerCultureReputation.get(level).get(playerId, this.cultureId);
      return villageRep + cultureRep;
   }

   public boolean isUnderAttack() {
      return false;
   }

   public boolean isControlledBy(UUID playerId) {
      return this.ownerUUID != null && this.ownerUUID.equals(playerId);
   }

   public boolean isLoneBuilding() {
      VillageType vType = ModCultures.getVillageType(this.villageTypeId);
      return vType != null && vType.loneBuilding();
   }

   public boolean areChestsLocked() {
      return this.chestLocked;
   }

   public void setChestLocked(boolean locked) {
      this.chestLocked = locked;
   }

   private void lockAllChests(VillageSavedData savedData) {
      this.chestLocked = true;
      savedData.setDirty();
   }

   private void unlockAllChests(ServerLevel level, VillageSavedData savedData) {
      this.chestLocked = false;
      savedData.setDirty();
      String name = this.villageName != null ? this.villageName : this.villageTypeId.getPath();
      Component message = Component.translatable("ui.allchestsunlocked", new Object[]{name});

      for (ServerPlayer player : level.players()) {
         if (player.blockPosition().closerToCenterThan(Vec3.atCenterOf(this.center), 64.0)) {
            player.sendSystemMessage(message);
         }
      }

      this.placeNegationWandInChests(level);
   }

   private void placeNegationWandInChests(ServerLevel level) {
      Item wand = (Item)ModItems.NEGATION_WAND.get();
      BuildingInstance townhall = this.getTownhall();
      if (townhall != null && townhall.getInventory() != null) {
         if (townhall.getInventory().getCount(level, wand) != 0) {
            return;
         }

         if (townhall.getInventory().add(level, wand, 1) > 0) {
            return;
         }
      }

      for (BuildingInstance building : this.buildings) {
         if (building.getInventory() != null && building.getInventory().add(level, wand, 1) > 0) {
            return;
         }
      }

      LOGGER.warn("[Millénaire] Could not place negation wand — all inventories full for village {}", this.villageName);
   }

   private void checkBattleStatus(ServerLevel level, VillageSavedData savedData) {
      int nbLiveDefenders = this.getDefenderCount();
      if (this.chestLocked && nbLiveDefenders == 0) {
         this.unlockAllChests(level, savedData);
      } else if (!this.chestLocked && nbLiveDefenders > 0) {
         this.lockAllChests(savedData);
      }
   }

   int getDefenderCount() {
      if (this.cachedDefenderCount < 0) {
         int count = 0;

         for (VillagerRecord record : this.villagerRecords.values()) {
            if (!record.isKilled()) {
               ResourceLocation typeId = record.getVillagerTypeId();
               if (typeId != null) {
                  VillagerType type = ModCultures.getVillagerType(typeId);
                  if (type != null && type.hasTag("helpInAttacks")) {
                     count++;
                  }
               }
            }
         }

         this.cachedDefenderCount = count;
      }

      return this.cachedDefenderCount;
   }

   private void invalidateDefenderCount() {
      this.cachedDefenderCount = -1;
   }

   public VillagePathManager getPathManager() {
      return this.pathManager;
   }

   public VillageWaypointGraph getWaypointGraph() {
      return this.waypointGraph;
   }

   public long getLastGoodsRefresh() {
      return this.lastGoodsRefresh;
   }

   public void setLastGoodsRefresh(long lastGoodsRefresh) {
      this.lastGoodsRefresh = lastGoodsRefresh;
   }

   public long getNoProjectsLeftUntil() {
      return this.noProjectsLeftUntil;
   }

   public void setNoProjectsLeftUntil(long tick) {
      this.noProjectsLeftUntil = tick;
   }

   public Map<ResourceLocation, Long> getPlacementCooldowns() {
      return Collections.unmodifiableMap(this.placementCooldowns);
   }

   public void addPlacementCooldown(ResourceLocation planSetId, long untilTick) {
      this.placementCooldowns.put(planSetId, untilTick);
   }

   @Nullable
   public Village.PendingProject getPendingProject() {
      return this.pendingProject;
   }

   public void setPendingProject(@Nullable Village.PendingProject project) {
      this.pendingProject = project;
      this.dirty = true;
   }

   public void addBoughtBuilding(ResourceLocation planSetId) {
      if (this.buildingsBought.add(planSetId)) {
         this.markDirty();
      }
   }

   public boolean isBuildingBought(ResourceLocation planSetId) {
      return this.buildingsBought.contains(planSetId);
   }

   public Set<ResourceLocation> getBuildingsBought() {
      return Collections.unmodifiableSet(this.buildingsBought);
   }

   public void loadBuildingsBought(Set<ResourceLocation> bought) {
      this.buildingsBought.clear();
      this.buildingsBought.addAll(bought);
   }

   @Nullable
   public String getBannerNbt() {
      return this.bannerNbt;
   }

   public void setBannerNbt(@Nullable String nbt) {
      this.bannerNbt = nbt;
      this.markDirty();
   }

   public void loadBannerNbt(@Nullable String nbt) {
      this.bannerNbt = nbt;
   }

   public ItemStack getBannerStack(RegistryAccess registryAccess) {
      return this.bannerNbt == null ? ItemStack.EMPTY : VillageBannerService.parseLegacyBanner(this.bannerNbt, registryAccess);
   }

   @Nullable
   public BrickColourTheme getBrickTheme() {
      return this.brickTheme;
   }

   public void setBrickTheme(@Nullable BrickColourTheme theme) {
      this.brickTheme = theme;
      this.markDirty();
   }

   @Nullable
   public String getBrickThemeName() {
      return this.brickTheme != null ? this.brickTheme.name() : this.rawBrickThemeName;
   }

   public void setRawBrickThemeName(@Nullable String name) {
      this.rawBrickThemeName = name;
   }

   @Nullable
   public String getRawBrickThemeName() {
      return this.rawBrickThemeName;
   }

   public void resolveBrickTheme(VillageType villageType) {
      if (this.rawBrickThemeName != null && this.brickTheme == null) {
         for (BrickColourTheme theme : villageType.brickColourThemes()) {
            if (theme.name().equals(this.rawBrickThemeName)) {
               this.brickTheme = theme;
               break;
            }
         }

         if (this.brickTheme == null) {
            LogUtils.getLogger().warn("Brick theme '{}' not found for village {}", this.rawBrickThemeName, this.villageName);
         }
      }
   }

   public List<BuildingInstance> getBuildings() {
      return Collections.unmodifiableList(this.buildings);
   }

   @Nullable
   public BuildingInstance findBuildingById(BuildingId buildingId) {
      for (BuildingInstance b : this.buildings) {
         if (b.getId().equals(buildingId)) {
            return b;
         }
      }

      return null;
   }

   @Nullable
   public BuildingInstance getBuildingAt(BlockPos pos) {
      for (BuildingInstance b : this.buildings) {
         if (b.containsPos(pos)) {
            return b;
         }
      }

      return null;
   }

   public List<BuildingInstance> getBuildingsWithTag(String tag) {
      if (this.buildingsByTag == null) {
         this.rebuildBuildingTagCache();
      }

      return this.buildingsByTag.getOrDefault(tag, List.of());
   }

   public List<BuildingInstance> getOperationalBuildingsWithTag(String tag) {
      List<BuildingInstance> all = this.getBuildingsWithTag(tag);
      if (all.isEmpty()) {
         return List.of();
      }

      List<BuildingInstance> result = new ArrayList<>(all.size());

      for (BuildingInstance b : all) {
         if (b.isOperational()) {
            result.add(b);
         }
      }

      return result;
   }

   public void invalidateBuildingTagCache() {
      this.buildingsByTag = null;
   }

   private void rebuildBuildingTagCache() {
      Map<String, List<BuildingInstance>> cache = new HashMap<>();

      for (BuildingInstance building : this.buildings) {
         Set<String> allTags = new HashSet<>();
         BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
         if (plan != null) {
            allTags.addAll(plan.tags());
         }

         allTags.addAll(building.getRuntimeTags());

         for (String tag : allTags) {
            cache.computeIfAbsent(tag, k -> new ArrayList<>()).add(building);
         }
      }

      this.buildingsByTag = cache;
   }

   public Set<UUID> getVillagerUuids() {
      return Collections.unmodifiableSet(this.villagerRecords.keySet());
   }

   public Map<UUID, ResourceLocation> getVillagerTypes() {
      if (this.villagerTypesCache == null) {
         Map<UUID, ResourceLocation> result = new LinkedHashMap<>();

         for (Entry<UUID, VillagerRecord> entry : this.villagerRecords.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getVillagerTypeId());
         }

         this.villagerTypesCache = Collections.unmodifiableMap(result);
      }

      return this.villagerTypesCache;
   }

   public Map<UUID, VillagerRecord> getVillagerRecords() {
      return Collections.unmodifiableMap(this.villagerRecords);
   }

   public int getMissingCount(UUID uuid) {
      return this.missingCounts.getOrDefault(uuid, 0);
   }

   public void putMissingCount(UUID uuid, int count) {
      this.missingCounts.put(uuid, count);
   }

   public void removeMissingCount(UUID uuid) {
      this.missingCounts.remove(uuid);
   }

   public Map<BlockPos, MillVillager> getActiveSellers() {
      return this.activeSellers;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void removeVillagerRecord(UUID uuid) {
      this.villagerRecords.remove(uuid);
      this.villagerTypesCache = null;
      this.invalidateDefenderCount();
   }

   public boolean markVillagerKilled(UUID uuid) {
      VillagerRecord record = this.villagerRecords.get(uuid);
      if (record == null) {
         return false;
      }

      record.setKilled(true);
      this.invalidateDefenderCount();
      return true;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public boolean consumeDirty() {
      boolean wasDirty = this.dirty;
      this.dirty = false;
      return wasDirty;
   }

   public void addBuilding(BuildingInstance building) {
      this.buildings.add(building);
      this.chunksNeedRefresh = true;
      this.allBedManagersInitialized = false;
      this.buildingsByTag = null;
      this.dirty = true;
   }

   public void addVillager(UUID uuid, ResourceLocation villagerTypeId) {
      this.villagerRecords.put(uuid, new VillagerRecord(uuid, villagerTypeId, null));
      this.villagerTypesCache = null;
      this.invalidateDefenderCount();
      this.dirty = true;
   }

   public void addVillager(UUID uuid, ResourceLocation villagerTypeId, @Nullable BuildingId homeBuilding) {
      this.villagerRecords.put(uuid, new VillagerRecord(uuid, villagerTypeId, homeBuilding));
      this.villagerTypesCache = null;
      this.invalidateDefenderCount();
      this.dirty = true;
   }

   public void addVillager(VillagerRecord record) {
      this.villagerRecords.put(record.getUuid(), record);
      this.villagerTypesCache = null;
      this.invalidateDefenderCount();
      this.dirty = true;
   }

   public void transferVillagerPermanently(ServerLevel level, UUID villagerId, Village destination, BuildingId destBuilding) {
      VillagerRecord record = this.villagerRecords.get(villagerId);
      if (record == null) {
         LOGGER.warn("[Millenaire] transferVillagerPermanently: no record for {}", villagerId);
      } else {
         this.removeVillagerRecord(villagerId);
         record.setHomeBuilding(destBuilding);
         destination.addVillager(record);
         if (level.getEntity(villagerId) instanceof MillVillager villager) {
            villager.setVillageId(destination.getId());
            villager.setHomeBuilding(destBuilding);
            if (destination.isActive()) {
               BuildingInstance destBldg = destination.getBuilding(destBuilding);
               BlockPos teleportPos;
               if (destBldg != null) {
                  BlockPos sleepPos = destBldg.getFirstPointPos("sleepingPos");
                  teleportPos = sleepPos != null ? sleepPos : destBldg.getOrigin();
               } else {
                  teleportPos = destination.getCenter();
               }

               NavigationHelperUtils.teleportToSafe(villager, teleportPos);
            } else {
               villager.discard();
            }
         }

         this.markDirty();
         destination.markDirty();
         LOGGER.debug(
            "[Millenaire] Transferred villager {} from {} to {}",
            new Object[]{
               villagerId.toString().substring(0, 8),
               this.villageName != null ? this.villageName : this.villageTypeId,
               destination.villageName != null ? destination.villageName : destination.villageTypeId
            }
         );
      }
   }

   @Nullable
   public VillagerRecord getVillagerRecord(UUID uuid) {
      return this.villagerRecords.get(uuid);
   }

   @Nullable
   public BuildingId getVillagerHome(UUID uuid) {
      VillagerRecord record = this.villagerRecords.get(uuid);
      return record != null ? record.getHomeBuilding() : null;
   }

   public void setVillagerHome(UUID uuid, @Nullable BuildingId homeBuilding) {
      VillagerRecord record = this.villagerRecords.get(uuid);
      if (record != null) {
         record.setHomeBuilding(homeBuilding);
      }

      this.dirty = true;
   }

   public int addReputation(UUID playerId, int amount) {
      int newValue = this.reputation.add(playerId, amount);
      this.dirty = true;
      return newValue;
   }

   public int adjustReputation(ServerLevel level, UUID playerId, int amount) {
      int newValue = this.addReputation(playerId, amount);
      int cultureAmount = amount / 10;
      int remainder = Math.abs(amount % 10);
      if (remainder != 0 && ThreadLocalRandom.current().nextInt(10) < remainder) {
         cultureAmount += amount > 0 ? 1 : -1;
      }

      if (cultureAmount != 0) {
         PlayerCultureReputation.get(level).add(playerId, this.cultureId, cultureAmount);
      }

      ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
      if (player != null) {
         if (newValue > 8192) {
            MillAdvancements.grant(player, MillAdvancements.FRIEND_INDEED);
         }

         if (newValue > 32768) {
            String cultureKey = this.cultureId.getPath().toLowerCase(Locale.ROOT);
            ResourceLocation repAdv = MillAdvancements.REP.get(cultureKey);
            if (repAdv != null) {
               MillAdvancements.grant(player, repAdv);
            }
         }

         PlayerCultureReputation.get(level).checkPendingAttila(player);
      }

      return newValue;
   }

   public void recordEvent(ServerLevel level, String message) {
      long tick = level.getServer().getTickCount();
      if (this.historyStartTick < 0L) {
         this.historyStartTick = tick;
      }

      if (this.history.size() >= 1000) {
         this.history.subList(0, 100).clear();
      }

      this.history.add(new VillageHistoryEntry(tick, message));
   }

   public List<VillageHistoryEntry> getHistory() {
      return Collections.unmodifiableList(this.history);
   }

   public long getHistoryStartTick() {
      return this.historyStartTick;
   }

   public void clearHistory() {
      this.history.clear();
      this.historyStartTick = -1L;
   }

   public void recordChronicleEvent(ServerLevel level, VillageEventType type, String param1, @Nullable String param2) {
      long gameTime = level.getGameTime();
      this.addChronicleEventDirect(new VillageEvent(gameTime, type, param1, param2));
      VillageSavedData.get(level).setDirty();
   }

   public void addChronicleEventDirect(VillageEvent event) {
      this.chronicle.add(event);
      if (this.chronicle.size() > 500) {
         this.chronicle.subList(0, this.chronicle.size() - 500).clear();
      }
   }

   public List<VillageEvent> getChronicle() {
      return Collections.unmodifiableList(this.chronicle);
   }

   @Nullable
   public BuildingInstance getBuilding(BuildingId buildingId) {
      for (BuildingInstance b : this.buildings) {
         if (b.getId().equals(buildingId)) {
            return b;
         }
      }

      return null;
   }

   @Nullable
   public BuildingInstance getTownhall() {
      if (this.buildings.isEmpty()) {
         return null;
      }

      for (BuildingInstance b : this.buildings) {
         ResourceLocation planSetId = b.getPlanSetId();
         if (planSetId != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
            if (planSet != null && planSet.isTownHall()) {
               return b;
            }
         }
      }

      return this.buildings.get(0);
   }

   public AABB computeBounds() {
      if (this.buildings.isEmpty()) {
         return new AABB(
            this.center.getX() - 8, this.center.getY() - 8, this.center.getZ() - 8, this.center.getX() + 8, this.center.getY() + 8, this.center.getZ() + 8
         );
      }

      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double minZ = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      double maxZ = -Double.MAX_VALUE;

      for (BuildingInstance b : this.buildings) {
         BlockPos o = b.getOrigin();
         int w = b.getEffectiveWidth();
         int d = b.getEffectiveDepth();
         minX = Math.min(minX, o.getX());
         minY = Math.min(minY, o.getY());
         minZ = Math.min(minZ, o.getZ());
         maxX = Math.max(maxX, w > 0 ? o.getX() + w - 1 : o.getX());
         maxY = Math.max(maxY, o.getY());
         maxZ = Math.max(maxZ, d > 0 ? o.getZ() + d - 1 : o.getZ());
      }

      return new AABB(minX - 8.0, minY - 8.0, minZ - 8.0, maxX + 8.0, maxY + 8.0, maxZ + 8.0);
   }

   public boolean isActive() {
      return this.active;
   }

   public void setActive(boolean active) {
      if (active && !this.active) {
         this.needsOrphanCleanup = true;
      }

      this.active = active;
   }

   public boolean isChunksForceLoaded() {
      return this.chunksForceLoaded;
   }

   public void setChunksForceLoaded(boolean chunksForceLoaded) {
      this.chunksForceLoaded = chunksForceLoaded;
   }

   public boolean isForceActive() {
      return this.forceActive;
   }

   public void setForceActive(boolean forceActive) {
      this.forceActive = forceActive;
   }

   public Set<ChunkPos> getLoadedChunks() {
      return this.loadedChunks;
   }

   public void setLoadedChunks(Set<ChunkPos> loadedChunks) {
      this.loadedChunks = loadedChunks;
   }

   public boolean isChunksNeedRefresh() {
      return this.chunksNeedRefresh;
   }

   public void setChunksNeedRefresh(boolean chunksNeedRefresh) {
      this.chunksNeedRefresh = chunksNeedRefresh;
   }

   public Set<ChunkPos> computeVillageChunks() {
      AABB bounds = this.computeBounds();
      int minCX = SectionPos.blockToSectionCoord((int)bounds.minX) - 1;
      int maxCX = SectionPos.blockToSectionCoord((int)bounds.maxX) + 1;
      int minCZ = SectionPos.blockToSectionCoord((int)bounds.minZ) - 1;
      int maxCZ = SectionPos.blockToSectionCoord((int)bounds.maxZ) + 1;
      Set<ChunkPos> chunks = new HashSet<>();

      for (int cx = minCX; cx <= maxCX; cx++) {
         for (int cz = minCZ; cz <= maxCZ; cz++) {
            chunks.add(new ChunkPos(cx, cz));
         }
      }

      return chunks;
   }

   public void syncRecords(ServerLevel level) {
      for (Entry<UUID, VillagerRecord> entry : this.villagerRecords.entrySet()) {
         if (level.getEntity(entry.getKey()) instanceof MillVillager villager && villager.isAlive()) {
            entry.getValue().updateFromEntity(villager);
         }
      }

      this.dirty = true;
   }

   @Nullable
   public BuildingInstance findUnreservedConstruction() {
      VillageType villageType = ModCultures.getVillageType(this.villageTypeId);
      int maxWallBuilders = villageType != null ? VillageGrowthManager.computeMaxWallSlots(this, villageType) : Integer.MAX_VALUE;
      int activeWallBuilders = VillageGrowthManager.countActiveWallBuilders(this);
      BuildingInstance wallFallback = null;

      for (BuildingInstance b : this.buildings) {
         if (b.isBeingBuilt()) {
            ConstructionTask task = b.getConstructionTask();
            if (task != null && !task.isReserved() && !task.isBlocked()) {
               if (!VillageGrowthManager.isWallSegment(b)) {
                  return b;
               }

               if (wallFallback == null && activeWallBuilders < maxWallBuilders) {
                  wallFallback = b;
               }
            }
         }
      }

      return wallFallback;
   }

   public void rebuildWaypointGraph(ServerLevel level) {
      this.waypointGraph.rebuild(this.buildings, this.center, level, this);
      this.lastWaypointRebuildTick = level.getGameTime();
   }

   public boolean rebuildWaypointGraphIfStale(ServerLevel level, long minIntervalTicks) {
      long now = level.getGameTime();
      long sinceLastRebuild = now - this.lastWaypointRebuildTick;
      long sinceLastRequest = now - this.lastWaypointRebuildRequestTick;
      if (sinceLastRequest > 600L) {
         this.waypointRebuildBackoff = 0;
      }

      this.lastWaypointRebuildRequestTick = now;
      long effectiveInterval = Math.min(minIntervalTicks << Math.min(this.waypointRebuildBackoff, 6), 1200L);
      if (sinceLastRebuild < effectiveInterval) {
         return false;
      }

      this.rebuildWaypointGraph(level);
      if (this.waypointRebuildBackoff < 6) {
         this.waypointRebuildBackoff++;
      }

      return true;
   }

   public void sendFireplacePositions(ServerLevel level) {
      Set<BuildingId> occupiedBuildings = new HashSet<>();

      for (VillagerRecord rec : this.villagerRecords.values()) {
         if (!rec.isKilled() && rec.getHomeBuilding() != null) {
            occupiedBuildings.add(rec.getHomeBuilding());
         }
      }

      List<BlockPos> allPositions = new ArrayList<>();

      for (BuildingInstance b : this.buildings) {
         if (b.isOperational() && occupiedBuildings.contains(b.getId())) {
            allPositions.addAll(b.getFireplacePositions());
         }
      }

      FireplacePositionsPayload payload = new FireplacePositionsPayload(this.id.uuid(), this.center, allPositions);

      for (ServerPlayer player : level.players()) {
         if (player.blockPosition().distSqr(this.center) < 16384.0) {
            PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
         }
      }
   }

   public void backgroundTick(ServerLevel level) {
      long currentDay = level.getDayTime() / 24000L;
      long timeOfDay = level.getDayTime() % 24000L;
      if (timeOfDay >= 13000L && currentDay > this.lastNightActionDay) {
         this.lastNightActionDay = currentDay;
         VillageDiplomacyHelper.performNightlyDiplomacyDrift(level, this);
         VillageDiplomacyHelper.regenerateDiplomacyPointsForPlayers(level, this);
         this.markDirty();
      }
   }

   void performNightlyActions(ServerLevel level) {
      VillageDiplomacyHelper.performNightlyDiplomacyDrift(level, this);
      VillageDiplomacyHelper.regenerateDiplomacyPointsForPlayers(level, this);
      if (!this.isLoneBuilding()) {
         LocalMerchantHelper.attemptMerchantMoves(level, this);
      }

      this.pathManager.nightlyRecheck(level, this);
   }

   public void tick(ServerLevel level) {
      if (!this.allBedManagersInitialized) {
         boolean allDone = true;

         for (BuildingInstance b : this.buildings) {
            if (b.isOperational() && !b.isBedManagerInitialized()) {
               allDone = false;
               BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
               if (plan != null) {
                  b.backfillBedPositions(level, plan);
               }

               this.markDirty();
            }
         }

         if (allDone) {
            this.allBedManagersInitialized = true;
         }
      }

      for (BuildingInstance b : this.buildings) {
         ConstructionTask task = b.getConstructionTask();
         if (task != null && task.isReserved()) {
            UUID builderUuid = task.getReservedBuilder();
            if (level.getEntity(builderUuid) == null) {
               task.tickReservation();
               if (task.isReservationExpired(200)) {
                  task.releaseReservation();
                  this.dirty = true;
               }
            } else {
               task.resetReservationAge();
            }
         }
      }

      if (this.integrityTickCounter == 599) {
         this.cleanupStaleReservations(level);
      }

      this.integrityTickCounter++;
      if (this.needsOrphanCleanup || this.integrityTickCounter >= 600) {
         boolean wasOrphanCleanup = this.needsOrphanCleanup;
         this.needsOrphanCleanup = false;
         this.integrityTickCounter = 0;
         this.syncRecords(level);
         VillageIntegrityChecker.checkIntegrity(level, this);
         VillageIntegrityChecker.cleanupOrphanedEntities(level, this);
      }

      if (this.integrityTickCounter == 300) {
         Set<UUID> knownUuids = this.getVillagerUuids();

         for (BuildingInstance b : this.buildings) {
            if (b.hasBedManager() && b.getBedManager().validate(level, knownUuids)) {
               this.markDirty();
            }
         }
      }

      this.growthTickCounter++;
      if (this.growthTickCounter >= 20) {
         this.growthTickCounter = 0;
         VillageGrowthManager.evaluateGrowth(level, this);
         VillageGrowthManager.evaluateWallGrowth(level, this);
      }

      if (level.getGameTime() % 20L == 0L) {
         this.despawnDangerousMobs(level);
      }

      if (level.getGameTime() % 20L == 10L) {
         this.checkBattleStatus(level, VillageSavedData.get(level));
      }

      if (this.isPlayerControlled() && level.getGameTime() % 1000L == 0L) {
         this.placeNegationWandInChests(level);
      }

      if (level.getGameTime() % 100L == 0L) {
         this.updatePens(level, false);
      }

      VillageSellerDispatcher.checkSeller(level, this);
      if (level.getGameTime() % 100L == 50L) {
         boolean isDaytime = !TickConstants.isNight(level);

         for (BuildingInstance b : this.buildings) {
            if (b.isOperational()) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
               if (planSet != null && planSet.isMarket()) {
                  MarketManager.updateMarket(level, this, b, isDaytime);
               }
            }
         }
      }

      if (level.getGameTime() % 20L == 0L) {
         VillageEnvironmentHelper.tickGroveSaplings(level, this);
      }

      this.refreshGoods(level);
      if (level.getGameTime() % 1000L == 0L) {
         this.regenerateScrollIfNeeded(level);
      }

      this.pathManager.tick(level, this);
      if (this.marvelManager == null && MarvelManager.isMarvelVillageType(this)) {
         this.marvelManager = new MarvelManager();
      }

      if (this.marvelManager != null) {
         this.marvelManager.tick(this, level);
      }

      long currentDay = level.getDayTime() / 24000L;
      long timeOfDay = level.getDayTime() % 24000L;
      if (timeOfDay >= 13000L && currentDay > this.lastNightActionDay) {
         this.lastNightActionDay = currentDay;
         this.performNightlyActions(level);
         this.markDirty();
      }

      this.panelTickCounter++;
      if (this.panelTickCounter >= 40) {
         this.panelTickCounter = 0;
         this.updatePanelDisplayLines(level);
      }

      if (level.getGameTime() % 200L == 0L) {
         this.checkExplorationAdvancements(level);
      }

      if (level.getGameTime() % 200L == 0L) {
         this.checkTravelBookDiscoveries(level);
      }
   }

   private void checkExplorationAdvancements(ServerLevel level) {
      PlayerDiscoveryHelper.checkExplorationAdvancements(level, this);
   }

   private void checkTravelBookDiscoveries(ServerLevel level) {
      if (this.dangerousMobsArea != null) {
         PlayerDiscoveryHelper.checkTravelBookDiscoveries(level, this, this.dangerousMobsArea);
      }
   }

   private void regenerateScrollIfNeeded(ServerLevel level) {
      PlayerDiscoveryHelper.regenerateScrollIfNeeded(level, this);
   }

   public boolean isPlayerControlled() {
      VillageType vType = ModCultures.getVillageType(this.villageTypeId);
      return vType != null && vType.playerControlled();
   }

   @Nullable
   public UUID getOwnerUUID() {
      return this.ownerUUID;
   }

   @Nullable
   public String getOwnerName() {
      return this.ownerName;
   }

   public void setOwner(@Nullable UUID uuid, @Nullable String name) {
      this.ownerUUID = uuid;
      this.ownerName = name;
      this.markDirty();
   }

   public long getLastNightActionDay() {
      return this.lastNightActionDay;
   }

   public void setLastNightActionDay(long day) {
      this.lastNightActionDay = day;
   }

   @Nullable
   public VillageId getParentVillageId() {
      return this.parentVillageId;
   }

   public void setParentVillageId(@Nullable VillageId parentId) {
      this.parentVillageId = parentId;
      this.markDirty();
   }

   @Nullable
   public MarvelManager getMarvelManager() {
      return this.marvelManager;
   }

   public void setMarvelManager(@Nullable MarvelManager manager) {
      this.marvelManager = manager;
   }

   public void updatePens(ServerLevel level, boolean completeRespawn) {
      VillageEnvironmentHelper.updatePens(level, this, completeRespawn);
   }

   private void despawnDangerousMobs(ServerLevel level) {
      if (this.dangerousMobsArea == null) {
         VillageType vType = ModCultures.getVillageType(this.villageTypeId);
         int radius = (vType != null ? vType.radius() : 90) + 20;
         this.dangerousMobsArea = new AABB(
            this.center.getX() - radius,
            this.center.getY() - 20,
            this.center.getZ() - radius,
            this.center.getX() + radius,
            this.center.getY() + 50,
            this.center.getZ() + radius
         );
      }

      VillageEnvironmentHelper.despawnDangerousMobs(level, this.dangerousMobsArea);
   }

   private void refreshGoods(ServerLevel level) {
      BuildingInstance centre = this.getCentre();
      if (centre != null) {
         BuildingPlanSet planSet = centre.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(centre.getPlanSetId()) : null;
         if (planSet != null && !planSet.startingGoods().isEmpty()) {
            if (level.isDay()) {
               this.restockNightActionDone = false;
            } else if (!this.restockNightActionDone) {
               long interval;
               if (this.areChestsLocked()) {
                  interval = 20L;
               } else {
                  interval = 100L;
               }

               if (this.areChestsLocked() && this.lastGoodsRefresh + interval * 24000L < level.getGameTime()) {
                  this.fillStartingGoods(level, centre, planSet, false);
                  this.lastGoodsRefresh = level.getGameTime();
                  this.markDirty();
               }

               this.restockNightActionDone = true;
            }
         }
      }
   }

   public void fillStartingGoods(ServerLevel level, BuildingInstance building, BuildingPlanSet planSet, boolean initialSpawn) {
      GoodsRestockHelper.fillStartingGoods(level, building, planSet, initialSpawn);
   }

   @Nullable
   private BuildingInstance getCentre() {
      return this.buildings.isEmpty() ? null : this.buildings.get(0);
   }

   private void updatePanelDisplayLines(ServerLevel level) {
      VillagePanelHelper.updatePanelDisplayLines(level, this);
   }

   public ResidentSlotManager getResidentSlotManager() {
      return this.residentSlotManager;
   }

   public int countAdultsInBuilding(BuildingId buildingId, Gender gender) {
      return this.residentSlotManager.countAdultsInBuilding(buildingId, gender);
   }

   public List<String> getFreeSlots(BuildingId buildingId, Gender gender) {
      return this.residentSlotManager.getFreeSlots(buildingId, gender);
   }

   public boolean hasFreeSlot(BuildingId buildingId, Gender gender) {
      return this.residentSlotManager.hasFreeSlot(buildingId, gender);
   }

   @Nullable
   public String reserveSlot(BuildingId buildingId, Gender gender, UUID teenagerId) {
      return this.residentSlotManager.reserveSlot(buildingId, gender, teenagerId);
   }

   public void releaseSlot(ResidentSlotManager.SlotKey key) {
      this.residentSlotManager.releaseSlot(key);
   }

   public void releaseAllSlots(UUID teenagerId) {
      this.residentSlotManager.releaseAllSlots(teenagerId);
   }

   public void cleanupStaleReservations(ServerLevel level) {
      this.residentSlotManager.cleanupStaleReservations(level);
   }

   public int countChildren() {
      return this.residentSlotManager.countChildren();
   }

   public int countChildrenInBuilding(BuildingId buildingId) {
      return this.residentSlotManager.countChildrenInBuilding(buildingId);
   }

   public boolean hasAnyFreeSlot(Gender gender) {
      return this.residentSlotManager.hasAnyFreeSlot(gender);
   }

   public boolean hasRelativeOfOppositeGenderInRecords(BuildingId buildingId, Gender teenagerGender, String familyName) {
      if (familyName != null && !familyName.isEmpty()) {
         Gender oppositeGender = teenagerGender == Gender.MALE ? Gender.FEMALE : Gender.MALE;

         for (VillagerRecord record : this.villagerRecords.values()) {
            if (record.getHomeBuilding() != null && record.getHomeBuilding().equals(buildingId)) {
               VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
               if (vType != null && !vType.isChild() && vType.gender() == oppositeGender && familyName.equals(record.getFamilyName())) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public int countResidentsInBuilding(BuildingId buildingId) {
      int count = 0;

      for (VillagerRecord record : this.villagerRecords.values()) {
         if (buildingId.equals(record.getHomeBuilding())) {
            count++;
         }
      }

      return count;
   }

   public void updateVillagerType(UUID uuid, ResourceLocation newTypeId) {
      VillagerRecord record = this.villagerRecords.get(uuid);
      if (record != null) {
         record.setVillagerTypeId(newTypeId);
         this.villagerTypesCache = null;
         this.invalidateDefenderCount();
      }

      this.dirty = true;
   }

   @Nullable
   Map<Item, Integer> getImportsNeededCache() {
      return this.importsNeededCache;
   }

   long getImportsNeededCacheExpiry() {
      return this.importsNeededCacheExpiry;
   }

   void setImportsNeededCache(Map<Item, Integer> cache, long expiryMs) {
      this.importsNeededCache = cache;
      this.importsNeededCacheExpiry = expiryMs;
   }

   public int getVillageItemCount(ServerLevel level, Item item) {
      String key = BuiltInRegistries.ITEM.getKey(item).toString();
      return this.getOrComputeVillageStock(level, key, k -> {
         int total = 0;

         for (BuildingInstance b : this.buildings) {
            BuildingInventory inv = b.getInventory();
            if (inv != null) {
               total += inv.getCount(level, item);
            }
         }

         return total;
      });
   }

   public int getVillageTagCount(ServerLevel level, TagKey<Item> tag) {
      String key = "#" + tag.location().toString();
      return this.getOrComputeVillageStock(level, key, k -> {
         int total = 0;

         for (BuildingInstance b : this.buildings) {
            BuildingInventory inv = b.getInventory();
            if (inv != null) {
               total += inv.getCountByTag(level, tag);
            }
         }

         return total;
      });
   }

   private int getOrComputeVillageStock(ServerLevel level, String key, Function<String, Integer> compute) {
      long now = level.getGameTime();
      if (this.villageStockCache == null || now - this.villageStockCacheTick > 60L) {
         this.villageStockCache = new HashMap<>();
         this.villageStockCacheTick = now;
      }

      Integer cached = this.villageStockCache.get(key);
      if (cached != null) {
         return cached;
      }

      int total = compute.apply(key);
      this.villageStockCache.put(key, total);
      return total;
   }

   public record PendingProject(
      ResourceLocation planSetId, String variant, int level, boolean isUpgrade, @Nullable BuildingId buildingId, @Nullable PlacedLocation plannedLocation
   ) {
      public PendingProject(ResourceLocation planSetId, String variant, int level, boolean isUpgrade, @Nullable BuildingId buildingId) {
         this(planSetId, variant, level, isUpgrade, buildingId, null);
      }
   }
}
