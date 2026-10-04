package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import org.millenaire.TickConstants;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.command.DebugCommand;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.diagnostics.NavigationEventLog;
import org.millenaire.discovery.DiscoveryTracker;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.impl.GatherGoodsGoal;
import org.millenaire.goal.impl.GetToolGoal;
import org.millenaire.goal.impl.LightHearthGoal;
import org.millenaire.goal.impl.RestGoal;
import org.millenaire.item.ClothItem;
import org.millenaire.item.SummoningWandItem;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.LocalMerchantHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

public class MillVillager extends PathfinderMob {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final float PATH_PREFERENCE_MALUS = 1.2F;
   private static final EntityDataAccessor<String> DATA_VILLAGER_TYPE = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Byte> DATA_MODEL_TYPE = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<String> DATA_TEXTURE = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_CLOTH_0 = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_CLOTH_1 = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<String> DATA_DISPLAY_NAME = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_ROLE_NAME = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_NATIVE_ROLE_NAME = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_GOAL_LABEL = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_SPEECH_TEXT = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> DATA_SLEEPING = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> DATA_FOREIGN_MERCHANT = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> DATA_IS_CHIEF = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> DATA_IS_SELLING = SynchedEntityData.defineId(MillVillager.class, EntityDataSerializers.BOOLEAN);
   private static final int HELD_ITEM_CYCLE_DURATION = 20;
   public static final int MAX_CHILD_SIZE = 20;
   private static final int WATER_DANGER_TICKS = 100;
   private static final int MAX_SLEEP_DEBT = 6000;
   private static final int SLEEP_DEBT_DECAY_PER_TICK = 2;
   private static final int SLEEP_DEBT_CARRY_GRACE_TICKS = 1200;
   private static final String FREE_CLOTHES = "free";
   private static final String NATURAL = "natural";
   private VillageId villageId;
   private ResourceLocation villagerTypeId;
   private final VillagerIdentity identity = new VillagerIdentity();
   @Nullable
   private BuildingId homeBuilding;
   @Nullable
   private BuildingId constructionBuildingId;
   private int foreignMerchantStallId = -1;
   private int visitorNbNights = 0;
   private final VillagerInventory inventory = new VillagerInventory();
   private final VillagerSpeech speech = new VillagerSpeech(this);
   @Nullable
   private GoalScheduler goalScheduler;
   private final VillagerNavigationManager navManager = new VillagerNavigationManager();
   private final NavigationEventLog navEventLog = new NavigationEventLog();
   private long lastLeafClearNotLeavesTick = Long.MIN_VALUE;
   private int heldItemTick;
   private int heldItemIndex;
   private int offHandItemIndex;
   private String lastGoalLabel = "";
   @Nullable
   private VillagerTask lastTrackedTask;
   private int suffocationGraceTicks;
   private int waterTicks;
   private int sleepDebtTicks;
   private boolean guiPreviewMode;
   @Nullable
   private BlockPos lastOutdoorPos;
   private static final Map<EquipmentSlot, String> ARMOR_SLOT_CATEGORIES = Map.of(
      EquipmentSlot.HEAD, "armourshelmet", EquipmentSlot.CHEST, "armourschestplate", EquipmentSlot.LEGS, "armoursleggings", EquipmentSlot.FEET, "armoursboots"
   );

   public VillagerNavigationManager getNavManager() {
      return this.navManager;
   }

   public NavigationEventLog getNavEventLog() {
      return this.navEventLog;
   }

   public int getSleepDebtTicks() {
      return this.sleepDebtTicks;
   }

   public void setSleepDebtTicks(int v) {
      this.sleepDebtTicks = Math.max(0, Math.min(6000, v));
   }

   public void setGuiPreviewMode(boolean preview) {
      this.guiPreviewMode = preview;
   }

   public boolean isGuiPreviewMode() {
      return this.guiPreviewMode;
   }

   public MillVillager(EntityType<? extends MillVillager> entityType, Level level) {
      super(entityType, level);
      this.setPersistenceRequired();
      this.setPathfindingMalus(PathType.WATER, -1.0F);
      this.setPathfindingMalus(PathType.WATER_BORDER, 8.0F);
      this.setPathfindingMalus(PathType.WALKABLE, 1.2F);
      this.setPathfindingMalus(PathType.COCOA, 0.0F);
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
      this.goalSelector.addGoal(1, new OpenFenceGateGoal(this, true));
      if (this.getNavigation() instanceof GroundPathNavigation groundNav) {
         groundNav.setCanOpenDoors(true);
      }
   }

   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   public int getMaxFallDistance() {
      return 1;
   }

   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (this.level().isClientSide()) {
         return player.getItemInHand(hand).getItem() instanceof SummoningWandItem ? InteractionResult.PASS : InteractionResult.SUCCESS;
      }

      if (!this.isSleeping() && !this.isVillagerSleeping()) {
         if (player instanceof ServerPlayer serverPlayer) {
            if (player.getItemInHand(hand).getItem() instanceof SummoningWandItem && (serverPlayer.hasPermissions(2) || serverPlayer.server.isSingleplayer())) {
               return InteractionResult.PASS;
            }

            if (this.goalScheduler != null) {
               VillagerNavigationManager nav = this.navManager;
               VillagerTask task = this.goalScheduler.getCurrentTask();
               boolean hasPath = this.getNavigation().getPath() != null && !this.getNavigation().isDone();
               LOGGER.info(
                  "[NavDebug] {} ({}) pos={} — goal={}, dest={}, hasPath={}, localStuck={}, longStuck={}, tp={}, abandoned={}, wpn={}",
                  new Object[]{
                     this.getVillagerDisplayName(),
                     this.getVillagerTypeId(),
                     this.blockPosition().toShortString(),
                     this.goalScheduler.getCurrentGoalId(),
                     nav.getDestination() != null ? nav.getDestination().toShortString() : "null",
                     hasPath,
                     nav.getLocalStuck(),
                     nav.getLongDistanceStuck(),
                     nav.getTeleportCount(),
                     nav.isAbandoned(),
                     nav.getWaypointNavigator() != null ? nav.getWaypointNavigator().getState() : "null"
                  }
               );
               if (task != null) {
                  Map<String, String> debugInfo = task.getNavDebugInfo();
                  if (debugInfo != null && !debugInfo.isEmpty()) {
                     LOGGER.info("[NavDebug]   task: {}", debugInfo);
                  }
               }
            }

            VillagerInteraction.openInfoScreen(serverPlayer, this);
         }

         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      boolean result = super.hurt(source, amount);
      if (result && source.getEntity() instanceof ServerPlayer player && this.villageId != null && this.level() instanceof ServerLevel serverLevel) {
         Village village = Village.resolve(serverLevel, this.villageId);
         if (village != null) {
            int repChange = -((int)(amount * 10.0F));
            village.adjustReputation(serverLevel, player.getUUID(), repChange);
            LOGGER.debug(
               "[Millenaire] {} hit {} : rep {} (village {})",
               new Object[]{player.getGameProfile().getName(), this.getVillagerTypeId(), repChange, village.getVillageName()}
            );
         }
      }

      return result;
   }

   public void die(DamageSource cause) {
      this.clearHeldItems();
      LOGGER.warn(
         "[Millénaire] Villager {} ({}) died: cause={}, pos={}, health={}",
         new Object[]{
            this.getUUID().toString().substring(0, 8), this.getVillagerTypeId(), cause.getMsgId(), this.blockPosition().toShortString(), this.getHealth()
         }
      );
      if (this.level() instanceof ServerLevel serverLevel && this.getVillageId() != null) {
         Village village = Village.resolve(serverLevel, this.getVillageId());
         if (village != null) {
            VillagerRecord record = village.getVillagerRecord(this.getUUID());
            if (record != null) {
               record.updateFromEntity(this);
               village.markVillagerKilled(this.getUUID());
               village.markDirty();
            }

            if (this.homeBuilding != null) {
               BuildingInstance home = village.getBuilding(this.homeBuilding);
               if (home != null && home.hasBedManager()) {
                  home.getBedManager().releaseBedByVillager(this.getUUID());
                  village.markDirty();
               }
            }

            ResourceLocation deathTypeId = this.getVillagerTypeId();
            village.recordEvent(
               serverLevel,
               "Villager died: "
                  + (deathTypeId != null ? deathTypeId.getPath() : "unknown")
                  + " ["
                  + this.getUUID().toString().substring(0, 8)
                  + "] — cause: "
                  + cause.getMsgId()
                  + " at "
                  + this.blockPosition().toShortString()
            );
            village.recordChronicleEvent(serverLevel, VillageEventType.DEATH, this.getFirstName() + " " + this.getFamilyName(), cause.getMsgId());
         }
      }

      if (cause.getEntity() instanceof ServerPlayer killer) {
         VillagerType vType = ModCultures.getVillagerType(this.getVillagerTypeId());
         if (vType != null && vType.hasTag("hostile")) {
            MillAdvancements.grant(killer, MillAdvancements.SELF_DEFENSE);
         } else {
            MillAdvancements.grant(killer, MillAdvancements.DARK_SIDE);
         }
      }

      super.die(cause);
   }

   public void grantSuffocationGrace(int ticks) {
      this.suffocationGraceTicks = ticks;
   }

   public boolean isInvulnerableTo(DamageSource source) {
      return this.suffocationGraceTicks > 0 && source.is(DamageTypes.IN_WALL) ? true : super.isInvulnerableTo(source);
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VILLAGER_TYPE, "");
      builder.define(DATA_MODEL_TYPE, (byte)0);
      builder.define(DATA_TEXTURE, "");
      builder.define(DATA_CLOTH_0, "");
      builder.define(DATA_CLOTH_1, "");
      builder.define(DATA_SCALE, 1.0F);
      builder.define(DATA_DISPLAY_NAME, "");
      builder.define(DATA_ROLE_NAME, "");
      builder.define(DATA_NATIVE_ROLE_NAME, "");
      builder.define(DATA_GOAL_LABEL, "");
      builder.define(DATA_SPEECH_TEXT, "");
      builder.define(DATA_SLEEPING, false);
      builder.define(DATA_FOREIGN_MERCHANT, false);
      builder.define(DATA_IS_CHIEF, false);
      builder.define(DATA_IS_SELLING, false);
   }

   public VillageId getVillageId() {
      return this.villageId;
   }

   public void setVillageId(VillageId villageId) {
      this.villageId = villageId;
   }

   @Nullable
   public ResourceLocation getVillagerTypeId() {
      if (this.level().isClientSide()) {
         String synced = (String)this.entityData.get(DATA_VILLAGER_TYPE);
         return synced.isEmpty() ? null : ResourceLocation.parse(synced);
      } else {
         return this.villagerTypeId;
      }
   }

   public void setVillagerTypeId(ResourceLocation villagerTypeId) {
      this.villagerTypeId = villagerTypeId;
      this.entityData.set(DATA_VILLAGER_TYPE, villagerTypeId != null ? villagerTypeId.toString() : "");
      if (villagerTypeId != null && !this.level().isClientSide()) {
         VillagerType vType = ModCultures.getVillagerType(villagerTypeId);
         this.entityData.set(DATA_IS_CHIEF, vType != null && vType.hasTag("chief"));
      }
   }

   public VillagerIdentity getIdentity() {
      return this.identity;
   }

   public ModelType getModelType() {
      return this.level().isClientSide() ? ModelType.fromByte((Byte)this.entityData.get(DATA_MODEL_TYPE)) : this.identity.getModelType();
   }

   public ResourceLocation getTexture() {
      if (this.level().isClientSide()) {
         String synced = (String)this.entityData.get(DATA_TEXTURE);
         return synced.isEmpty() ? null : ResourceLocation.parse(synced);
      } else {
         return this.identity.getTexture();
      }
   }

   public ResourceLocation getClothTexture0() {
      if (this.level().isClientSide()) {
         String synced = (String)this.entityData.get(DATA_CLOTH_0);
         return synced.isEmpty() ? null : ResourceLocation.parse(synced);
      } else {
         return this.identity.getClothTexture0();
      }
   }

   public ResourceLocation getClothTexture1() {
      if (this.level().isClientSide()) {
         String synced = (String)this.entityData.get(DATA_CLOTH_1);
         return synced.isEmpty() ? null : ResourceLocation.parse(synced);
      } else {
         return this.identity.getClothTexture1();
      }
   }

   public float getVillagerScale() {
      return this.level().isClientSide() ? (Float)this.entityData.get(DATA_SCALE) : this.identity.getVillagerScale();
   }

   public Component getDisplayName() {
      String name = this.getVillagerDisplayName();
      return (Component)(name != null && !name.isEmpty() && !name.equals("entity.millenaire.villager") ? Component.literal(name) : super.getDisplayName());
   }

   public String getVillagerDisplayName() {
      if (this.level().isClientSide()) {
         return (String)this.entityData.get(DATA_DISPLAY_NAME);
      } else {
         String fn = this.identity.getFamilyName();
         String gn = this.identity.getFirstName();
         if (fn.isEmpty()) {
            return gn;
         } else {
            return gn.isEmpty() ? fn : gn + " " + fn;
         }
      }
   }

   public String getFirstName() {
      return this.identity.getFirstName();
   }

   public String getFamilyName() {
      return this.identity.getFamilyName();
   }

   public void setFirstName(String firstName) {
      this.identity.setFirstName(firstName);
      this.syncDisplayName();
   }

   public void setFamilyName(String familyName) {
      this.identity.setFamilyName(familyName);
      this.syncDisplayName();
   }

   private void syncDisplayName() {
      String display = this.getVillagerDisplayName();
      this.entityData.set(DATA_DISPLAY_NAME, display);
   }

   public String getRoleName() {
      return this.level().isClientSide() ? (String)this.entityData.get(DATA_ROLE_NAME) : this.identity.getRoleName();
   }

   public String getNativeRoleName() {
      return (String)this.entityData.get(DATA_NATIVE_ROLE_NAME);
   }

   @Nullable
   public ResourceLocation getCultureId() {
      ResourceLocation vtId = this.getVillagerTypeId();
      if (vtId == null) {
         return null;
      }

      String path = vtId.getPath();
      int slash = path.indexOf(47);
      return slash < 0 ? null : ResourceLocation.fromNamespaceAndPath(vtId.getNamespace(), path.substring(0, slash));
   }

   private void syncNativeRoleName() {
      if (this.villagerTypeId != null) {
         VillagerType vt = ModCultures.getVillagerType(this.villagerTypeId);
         if (vt != null) {
            if (vt.isChild() && this.getChildSize() >= 20 && vt.altNativeName() != null) {
               this.entityData.set(DATA_NATIVE_ROLE_NAME, vt.altNativeName());
               return;
            }

            if (vt.nativeName() != null) {
               this.entityData.set(DATA_NATIVE_ROLE_NAME, vt.nativeName());
               return;
            }
         }
      }

      this.entityData.set(DATA_NATIVE_ROLE_NAME, "");
   }

   public int getChildSize() {
      return this.identity.getChildSize();
   }

   public void setChildSize(int childSize) {
      this.identity.setChildSize(childSize);
      if (childSize >= 0) {
         VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
         if (vType != null) {
            float scale = VillagerAppearanceFactory.computeChildScale(childSize, vType.gender());
            this.setVillagerScale(scale);
         }

         double childHealth = 10.0 + childSize;
         AttributeInstance attr = this.getAttribute(Attributes.MAX_HEALTH);
         if (attr != null) {
            attr.setBaseValue(childHealth);
            if (this.getHealth() > (float)childHealth) {
               this.setHealth((float)childHealth);
            }
         }

         if (childSize >= 20) {
            this.syncNativeRoleName();
         }
      }
   }

   public boolean isChild() {
      return this.identity.getChildSize() >= 0;
   }

   public String getFathersName() {
      return this.identity.getFathersName();
   }

   public void setFathersName(String fathersName) {
      this.identity.setFathersName(fathersName);
   }

   public String getMothersName() {
      return this.identity.getMothersName();
   }

   public void setMothersName(String mothersName) {
      this.identity.setMothersName(mothersName);
   }

   public String getSpousesName() {
      return this.identity.getSpousesName();
   }

   public void setSpousesName(String spousesName) {
      this.identity.setSpousesName(spousesName);
   }

   public String getMaidenName() {
      return this.identity.getMaidenName();
   }

   public void setMaidenName(String maidenName) {
      this.identity.setMaidenName(maidenName);
   }

   public void setVillagerScale(float scale) {
      this.identity.setVillagerScale(scale);
      this.entityData.set(DATA_SCALE, scale);
   }

   @Nullable
   public BuildingId getHomeBuilding() {
      return this.homeBuilding;
   }

   public void setHomeBuilding(@Nullable BuildingId newHome) {
      if (this.homeBuilding != null && !this.homeBuilding.equals(newHome) && this.villageId != null && this.level() instanceof ServerLevel sl) {
         Village village = Village.resolve(sl, this.villageId);
         if (village != null) {
            BuildingInstance oldHome = village.getBuilding(this.homeBuilding);
            if (oldHome != null && oldHome.hasBedManager()) {
               oldHome.getBedManager().releaseBedByVillager(this.getUUID());
               village.markDirty();
            }
         }
      }

      this.homeBuilding = newHome;
      if (this.villageId != null && this.level() instanceof ServerLevel sl) {
         Village village = Village.resolve(sl, this.villageId);
         if (village != null) {
            village.setVillagerHome(this.getUUID(), newHome);
         }
      }
   }

   @Nullable
   public BuildingId getConstructionBuildingId() {
      return this.constructionBuildingId;
   }

   public void setConstructionBuildingId(@Nullable BuildingId id) {
      this.constructionBuildingId = id;
   }

   public int getForeignMerchantStallId() {
      return this.foreignMerchantStallId;
   }

   public void setForeignMerchantStallId(int stallId) {
      this.foreignMerchantStallId = stallId;
      if (stallId >= 0) {
         this.entityData.set(DATA_FOREIGN_MERCHANT, true);
      }
   }

   public int getVisitorNbNights() {
      return this.visitorNbNights;
   }

   public void setVisitorNbNights(int nights) {
      this.visitorNbNights = nights;
   }

   public boolean isForeignMerchant() {
      if (this.level().isClientSide()) {
         return (Boolean)this.entityData.get(DATA_FOREIGN_MERCHANT);
      }

      VillagerType vType = ModCultures.getVillagerType(this.getVillagerTypeId());
      return vType != null && vType.hasTag("foreignmerchant");
   }

   public boolean isLocalMerchant() {
      VillagerType vType = ModCultures.getVillagerType(this.getVillagerTypeId());
      return vType != null && vType.hasTag("localmerchant");
   }

   private void localMerchantRescue() {
      if (this.level() instanceof ServerLevel sl) {
         if (this.homeBuilding != null && this.villageId != null) {
            Village village = Village.resolve(sl, this.villageId);
            if (village != null) {
               BuildingInstance townhall = village.getTownhall();
               if (townhall != null) {
                  if (this.homeBuilding.equals(townhall.getId())) {
                     List<BuildingInstance> inns = village.getOperationalBuildingsWithTag("inn");
                     BuildingInstance freeInn = null;

                     for (BuildingInstance inn : inns) {
                        if (LocalMerchantHelper.getMerchantRecord(village, inn) == null) {
                           freeInn = inn;
                           break;
                        }
                     }

                     if (freeInn != null) {
                        this.setHomeBuilding(freeInn.getId());
                        LOGGER.warn("[Millenaire] Merchant {} had Town Hall as home. Moved to inn.", this.getVillagerDisplayName());
                     } else {
                        village.removeVillagerRecord(this.getUUID());
                        village.markDirty();
                        this.discard();
                        LOGGER.warn("[Millenaire] Merchant {} had Town Hall as home and no free inn. Despawned.", this.getVillagerDisplayName());
                     }
                  }
               }
            }
         }
      }
   }

   public boolean isChief() {
      return (Boolean)this.entityData.get(DATA_IS_CHIEF);
   }

   public void syncChiefFlag(VillagerType vType) {
      this.entityData.set(DATA_IS_CHIEF, vType.hasTag("chief"));
   }

   public boolean isSelling() {
      return (Boolean)this.entityData.get(DATA_IS_SELLING);
   }

   public void setSelling(boolean selling) {
      this.entityData.set(DATA_IS_SELLING, selling);
   }

   public VillagerInventory getInventory() {
      return this.inventory;
   }

   @Nullable
   public GoalScheduler getGoalScheduler() {
      return this.goalScheduler;
   }

   @Nullable
   public BlockPos getLastOutdoorPos() {
      return this.lastOutdoorPos;
   }

   public String getGoalLabel() {
      return (String)this.entityData.get(DATA_GOAL_LABEL);
   }

   public boolean isVillagerSleeping() {
      return (Boolean)this.entityData.get(DATA_SLEEPING);
   }

   public void setVillagerSleeping(boolean sleeping) {
      this.entityData.set(DATA_SLEEPING, sleeping);
   }

   public void initGoals(GoalRegistry registry, VillagerType villagerType) {
      List<VillagerGoal> goals = new ArrayList<>(registry.resolve(villagerType.goals()));
      if (!villagerType.toolNeededClasses().isEmpty()) {
         VillagerGoal getToolGoal = registry.get(GetToolGoal.ID);
         if (getToolGoal != null && !goals.contains(getToolGoal)) {
            goals.add(getToolGoal);
         }
      }

      if (!villagerType.collectGoods().isEmpty()) {
         VillagerGoal gatherGoodsGoal = registry.get(GatherGoodsGoal.ID);
         if (gatherGoodsGoal != null && !goals.contains(gatherGoodsGoal)) {
            goals.add(gatherGoodsGoal);
         }
      }

      if (!villagerType.isChild()) {
         VillagerGoal lightHearthGoal = registry.get(LightHearthGoal.ID);
         if (lightHearthGoal != null && !goals.contains(lightHearthGoal)) {
            goals.add(lightHearthGoal);
         }
      }

      this.goalScheduler = new GoalScheduler(goals);
   }

   public void initAppearance(
      ModelType modelType,
      ResourceLocation texture,
      ResourceLocation cloth0,
      ResourceLocation cloth1,
      float scale,
      String firstName,
      String familyName,
      String roleName
   ) {
      this.identity.setModelType(modelType);
      this.identity.setTexture(texture);
      this.identity.setClothTexture0(cloth0);
      this.identity.setClothTexture1(cloth1);
      this.identity.setVillagerScale(scale);
      this.identity.setFirstName(firstName);
      this.identity.setFamilyName(familyName);
      this.identity.setRoleName(roleName);
      this.entityData.set(DATA_MODEL_TYPE, modelType.toByte());
      this.entityData.set(DATA_TEXTURE, texture != null ? texture.toString() : "");
      this.entityData.set(DATA_CLOTH_0, cloth0 != null ? cloth0.toString() : "");
      this.entityData.set(DATA_CLOTH_1, cloth1 != null ? cloth1.toString() : "");
      this.entityData.set(DATA_SCALE, scale);
      this.entityData.set(DATA_DISPLAY_NAME, this.getVillagerDisplayName());
      this.entityData.set(DATA_ROLE_NAME, roleName != null ? roleName : "");
      this.syncNativeRoleName();
   }

   public void updateClothTextures(VillagerType vType) {
      if (vType != null) {
         String bestClothName = null;
         int clothLevel = -1;
         if (vType.hasClothSet("free")) {
            bestClothName = "free";
            clothLevel = 0;
         }

         for (Entry<Item, Integer> entry : this.inventory.getAll().entrySet()) {
            if (entry.getValue() > 0
               && entry.getKey() instanceof ClothItem clothItem
               && clothItem.getPriority() > clothLevel
               && vType.hasClothSet(clothItem.getClothName())) {
               bestClothName = clothItem.getClothName();
               clothLevel = clothItem.getPriority();
            }
         }

         if (bestClothName != null) {
            if (!bestClothName.equals(this.identity.getClothName())) {
               this.identity.setClothName(bestClothName);
               ThreadLocalRandom random = ThreadLocalRandom.current();

               for (int layer = 0; layer < 2; layer++) {
                  ResourceLocation texture;
                  if (vType.hasNaturalLayer(layer)) {
                     texture = randomClothTexture(vType, "natural", layer, random);
                  } else {
                     texture = randomClothTexture(vType, bestClothName, layer, random);
                  }

                  if (layer == 0) {
                     this.identity.setClothTexture0(texture);
                  } else {
                     this.identity.setClothTexture1(texture);
                  }
               }

               this.entityData.set(DATA_CLOTH_0, this.identity.getClothTexture0() != null ? this.identity.getClothTexture0().toString() : "");
               this.entityData.set(DATA_CLOTH_1, this.identity.getClothTexture1() != null ? this.identity.getClothTexture1().toString() : "");
            }
         } else {
            this.identity.setClothName(null);
            this.identity.setClothTexture0(null);
            this.identity.setClothTexture1(null);
            this.entityData.set(DATA_CLOTH_0, "");
            this.entityData.set(DATA_CLOTH_1, "");
         }
      }
   }

   @Nullable
   private static ResourceLocation randomClothTexture(VillagerType vType, String clothSetName, int layer, ThreadLocalRandom random) {
      VillagerType.ClothSet clothSet = vType.clothes().get(clothSetName);
      if (clothSet == null) {
         return null;
      }

      List<ResourceLocation> textures = layer == 0 ? clothSet.layer0() : clothSet.layer1();
      return textures != null && !textures.isEmpty() ? textures.get(random.nextInt(textures.size())) : null;
   }

   public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 20.0)
         .add(Attributes.MOVEMENT_SPEED, 0.55)
         .add(Attributes.FOLLOW_RANGE, 120.0)
         .add(Attributes.ATTACK_DAMAGE, 1.0)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   protected PathNavigation createNavigation(Level level) {
      return new MillPathNavigation(this, level);
   }

   @Nullable
   protected SoundEvent getAmbientSound() {
      return null;
   }

   @Nullable
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   @Nullable
   protected SoundEvent getDeathSound() {
      return null;
   }

   public void aiStep() {
      super.aiStep();
      this.updateSwingTime();
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide() && this.suffocationGraceTicks > 0) {
         this.suffocationGraceTicks--;
      }

      if (!this.level().isClientSide() && this.goalScheduler != null) {
         if (this.isInWater()) {
            this.waterTicks++;
            if (this.waterTicks > 100) {
               int safeY = this.level().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, this.blockPosition().getX(), this.blockPosition().getZ());
               this.teleportTo(this.blockPosition().getX() + 0.5, safeY, this.blockPosition().getZ() + 0.5);
               LOGGER.debug("[Millénaire] Anti-drowning: {} TP surface (in water for {} ticks)", this.getVillagerTypeId(), this.waterTicks);
               this.waterTicks = 0;
            }
         } else {
            this.waterTicks = 0;
         }
      }

      if (!this.level().isClientSide() && this.suffocationGraceTicks <= 0) {
         BlockPos feet = this.blockPosition();
         if (this.level().getBlockState(feet).isSuffocating(this.level(), feet)) {
            int safeY = this.level().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
            this.teleportTo(feet.getX() + 0.5, safeY, feet.getZ() + 0.5);
            if (this.tickCount % 100 == 0) {
               LOGGER.warn(
                  "[Millenaire] Anti-suffocation safety net: {} TP to surface Y={} from {}",
                  new Object[]{this.getVillagerTypeId(), safeY, feet.toShortString()}
               );
            }
         }
      }

      if (!this.level().isClientSide() && this.level().getGameTime() % 40L == 7L && this.level().canSeeSky(this.blockPosition())) {
         this.lastOutdoorPos = this.blockPosition().immutable();
      }

      if (!this.level().isClientSide() && this.tickCount % 20 == 0 && this.villagerTypeId != null) {
         VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
         if (vType != null) {
            this.updateClothTextures(vType);
         }
      }

      if (!this.level().isClientSide() && this.tickCount % 100 == 0 && this.villagerTypeId != null) {
         this.unlockForNearbyPlayers();
      }

      if (!this.level().isClientSide() && this.tickCount % 100 == 0 && this.isLocalMerchant()) {
         this.localMerchantRescue();
      }

      if (!this.level().isClientSide() && Math.abs(this.level().getGameTime() + this.hashCode()) % 10L == 6L) {
         this.handleLeafClearing();
      }

      if (!this.level().isClientSide()) {
         this.tickSleepDebt();
      }

      if (!this.level().isClientSide() && this.tickCount % 40 == 13) {
         this.enforceSleepInvariants();
      }

      if (!this.level().isClientSide() && this.goalScheduler != null) {
         GoalContext ctx = this.buildGoalContext();
         if (ctx != null) {
            this.goalScheduler.tick(ctx);
         }

         Village navVillage = ctx != null ? ctx.village() : null;
         this.navManager.tick(this, navVillage);
         VillagerTask task = this.goalScheduler.getCurrentTask();
         if (task != null) {
            if (task != this.lastTrackedTask) {
               this.heldItemTick = 0;
               this.heldItemIndex = 0;
               this.offHandItemIndex = 0;
               this.lastTrackedTask = task;
               this.applyHeldItems(task);
               if (!this.isSleeping() && !this.isVillagerSleeping()) {
                  this.speech.speakGoalChosen(task);
               }

               if (DebugCommand.isVerbose(this.getUUID())) {
                  LOGGER.info("[V-DEBUG] {} : new task → {}", this.getVillagerDisplayName(), task.goalId());
               }
            }

            this.heldItemTick++;
            if (this.heldItemTick >= 20) {
               this.heldItemTick = 0;
               this.cycleHeldItems(task);
            }

            this.updateGoalLabel(task);
            if (!this.isSleeping() && !this.isVillagerSleeping()) {
               this.speech.tick(task);
            }
         } else {
            if (this.lastTrackedTask != null) {
               this.clearHeldItems();
               this.clearGoalLabel();
               this.lastTrackedTask = null;
            }

            if (!this.isSleeping() && !this.isVillagerSleeping()) {
               this.speech.tick(null);
            }
         }

         this.tickPassivePickup();
      }
   }

   private void unlockForNearbyPlayers() {
      if (this.level() instanceof ServerLevel serverLevel) {
         if ((Boolean)MillenaireServerConfig.SERVER.travelBookLearning.get()) {
            AABB area = this.getBoundingBox().inflate(5.0);
            VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
            if (vType != null) {
               String cultureKey = vType.culture().getPath();
               String villagerKey = this.villagerTypeId.getPath();

               for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area)) {
                  DiscoveryTracker tracker = DiscoveryTracker.get(serverLevel);
                  if (tracker.unlockVillager(player.getUUID(), cultureKey, villagerKey)) {
                     player.sendSystemMessage(Component.translatable("travelbook.discovered.villager", new Object[]{vType.nativeName()}));
                  }
               }
            }
         }
      }
   }

   private void tickSleepDebt() {
      boolean isNight = TickConstants.isNight(this.level());
      long dayTime = this.level().getDayTime() % 24000L;
      boolean asleep = this.isSleeping() || this.isVillagerSleeping();
      this.sleepDebtTicks = nextSleepDebt(this.sleepDebtTicks, asleep, isNight, dayTime);
   }

   static int nextSleepDebt(int current, boolean asleep, boolean isNight, long dayTimeInCycle) {
      if (asleep) {
         return current > 0 ? Math.max(0, current - 2) : 0;
      } else if (isNight) {
         return current < 6000 ? current + 1 : 6000;
      } else {
         return current > 0 && dayTimeInCycle >= 1200L ? 0 : current;
      }
   }

   private void handleLeafClearing() {
      if (this.getNavigation().getPath() != null) {
         Path path = this.getNavigation().getPath();
         if (path.getNodeCount() != 0) {
            VillagerType vtype = ModCultures.getVillagerType(this.getVillagerTypeId());
            if (vtype != null && vtype.hasTag("noleafclearing")) {
               this.recordLeafClearSkipped(null, "noleafclearing_tag");
            } else {
               Village village = null;
               if (this.level() instanceof ServerLevel sl && this.villageId != null) {
                  village = Village.resolve(sl, this.villageId);
               }

               MutableBlockPos mutable = new MutableBlockPos();
               int nextIdx = path.getNextNodeIndex();
               if (nextIdx < path.getNodeCount()) {
                  Node node = path.getNode(nextIdx);
                  if (node != null) {
                     this.clearLeafAt(mutable.set(node.x, node.y, node.z), village);
                     this.clearLeafAt(mutable.set(node.x, node.y + 1, node.z), village);
                  }
               }

               int nextNext = nextIdx + 1;
               if (nextNext < path.getNodeCount()) {
                  Node node = path.getNode(nextNext);
                  if (node != null) {
                     for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                           this.clearLeafAt(mutable.set(node.x + dx, node.y, node.z + dz), village);
                           this.clearLeafAt(mutable.set(node.x + dx, node.y + 1, node.z + dz), village);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void clearLeafAt(BlockPos pos, @Nullable Village village) {
      BlockState state = this.level().getBlockState(pos);
      if (!(state.getBlock() instanceof LeavesBlock)) {
         this.recordLeafClearSkipped(pos, "not_leaves");
      } else if (village != null && village.getBuildingAt(pos) != null) {
         NavigationCounters.incLeafClearSkippedInBuilding();
         this.recordLeafClearSkipped(pos, "in_building_footprint");
      } else {
         this.level().destroyBlock(pos, true);
      }
   }

   private void recordLeafClearSkipped(@Nullable BlockPos pos, String reason) {
      long now = this.level().getGameTime();
      if ("not_leaves".equals(reason)) {
         if (this.lastLeafClearNotLeavesTick == now) {
            return;
         }

         this.lastLeafClearNotLeavesTick = now;
      }

      String detail = pos == null ? "reason=" + reason : "pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " reason=" + reason;
      this.navEventLog.record(now, NavEvent.Layer.VNM, NavEvent.Type.LEAF_CLEAR_SKIPPED, detail);
   }

   private void tickPassivePickup() {
      if (this.tickCount % 20 == Math.floorMod(this.getUUID().hashCode(), 20)) {
         if (!TickConstants.isNight(this.level())) {
            VillagerType vtype = ModCultures.getVillagerType(this.getVillagerTypeId());
            if (vtype != null && !vtype.resolvedCollectGoods().isEmpty()) {
               AABB scanBox = this.getBoundingBox().inflate(5.0, 30.0, 5.0);

               for (ItemEntity itemEntity : this.level().getEntitiesOfClass(ItemEntity.class, scanBox)) {
                  ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                  if (vtype.resolvedCollectGoods().contains(itemId)) {
                     this.getInventory().add(itemEntity.getItem().getItem(), 1);
                     itemEntity.getItem().shrink(1);
                     if (itemEntity.getItem().isEmpty()) {
                        itemEntity.discard();
                     }
                     break;
                  }
               }
            }
         }
      }
   }

   private void applyHeldItems(VillagerTask task) {
      TravelPhase phase = task.getTravelPhase();
      List<ItemStack> mainItems = task.getHeldItems(phase);
      List<ItemStack> offItems = task.getOffHandItems(phase);
      if (!mainItems.isEmpty()) {
         this.heldItemIndex = 0;
         this.setItemSlot(EquipmentSlot.MAINHAND, mainItems.get(0));
      } else {
         this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      }

      if (!offItems.isEmpty()) {
         this.offHandItemIndex = 0;
         this.setItemSlot(EquipmentSlot.OFFHAND, offItems.get(0));
      } else {
         this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      }
   }

   private void cycleHeldItems(VillagerTask task) {
      TravelPhase phase = task.getTravelPhase();
      List<ItemStack> mainItems = task.getHeldItems(phase);
      List<ItemStack> offItems = task.getOffHandItems(phase);
      if (!mainItems.isEmpty()) {
         this.heldItemIndex = (this.heldItemIndex + 1) % mainItems.size();
         this.setItemSlot(EquipmentSlot.MAINHAND, mainItems.get(this.heldItemIndex));
      } else {
         this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      }

      if (!offItems.isEmpty()) {
         this.offHandItemIndex = (this.offHandItemIndex + 1) % offItems.size();
         this.setItemSlot(EquipmentSlot.OFFHAND, offItems.get(this.offHandItemIndex));
      } else {
         this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      }
   }

   private void updateGoalLabel(VillagerTask task) {
      Component label = task.getGoalLabel();
      String key = "";
      if (label != null) {
         if (label.getContents() instanceof TranslatableContents tc) {
            key = tc.getKey();
         } else {
            key = label.getString();
         }
      }

      if (!key.equals(this.lastGoalLabel)) {
         this.lastGoalLabel = key;
         this.entityData.set(DATA_GOAL_LABEL, key);
      }
   }

   public ItemStack getItemBySlot(EquipmentSlot slot) {
      String categoryId = ARMOR_SLOT_CATEGORIES.get(slot);
      if (categoryId == null) {
         return super.getItemBySlot(slot);
      }

      ToolCategory category = ToolCategoryRegistry.get(categoryId);
      if (category == null) {
         return ItemStack.EMPTY;
      }

      ToolCategory.ToolEntry best = category.getBestOwned(item -> this.getInventory().getCount(item) > 0);
      return best != null && best.item() != null ? new ItemStack(best.item()) : ItemStack.EMPTY;
   }

   private void clearHeldItems() {
      this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
      this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
      this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
      this.heldItemTick = 0;
      this.heldItemIndex = 0;
      this.offHandItemIndex = 0;
   }

   private void clearGoalLabel() {
      if (!this.lastGoalLabel.isEmpty()) {
         this.lastGoalLabel = "";
         this.entityData.set(DATA_GOAL_LABEL, "");
      }
   }

   public VillagerSpeech getSpeech() {
      return this.speech;
   }

   public String getSpeechText() {
      return this.speech.getSpeechText();
   }

   public void setSpeechText(String text) {
      this.speech.setSpeechText(text);
   }

   void setSpeechData(String text) {
      this.entityData.set(DATA_SPEECH_TEXT, text);
   }

   String getSpeechData() {
      return (String)this.entityData.get(DATA_SPEECH_TEXT);
   }

   @Nullable
   public GoalContext buildGoalContext() {
      if (this.level() instanceof ServerLevel serverLevel) {
         if (this.villageId == null) {
            return null;
         }

         Village village = Village.resolve(serverLevel, this.villageId);
         if (village == null) {
            return null;
         }

         long dayTime = serverLevel.getDayTime() % 24000L;
         long gameTime = serverLevel.getGameTime();
         return new GoalContext(this, village, serverLevel, dayTime, gameTime);
      } else {
         return null;
      }
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.villageId != null) {
         tag.putUUID("villageId", this.villageId.uuid());
      }

      if (this.villagerTypeId != null) {
         tag.putString("villagerType", this.villagerTypeId.toString());
      }

      if (this.homeBuilding != null) {
         tag.putUUID("homeBuilding", this.homeBuilding.uuid());
      }

      if (this.constructionBuildingId != null) {
         tag.putUUID("constructionBuilding", this.constructionBuildingId.uuid());
      }

      if (this.foreignMerchantStallId >= 0) {
         tag.putInt("foreignMerchantStallId", this.foreignMerchantStallId);
      }

      if (this.visitorNbNights > 0) {
         tag.putInt("visitorNbNights", this.visitorNbNights);
      }

      this.identity.save(tag);
      this.inventory.save(tag);
      if (this.isVillagerSleeping()) {
         tag.putBoolean("customSleeping", true);
      }

      if (this.sleepDebtTicks > 0) {
         tag.putInt("sleepDebtTicks", this.sleepDebtTicks);
      }
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("villageId")) {
         this.villageId = new VillageId(tag.getUUID("villageId"));
      }

      if (tag.contains("villagerType")) {
         this.villagerTypeId = ResourceLocation.parse(tag.getString("villagerType"));
      }

      if (tag.hasUUID("homeBuilding")) {
         this.homeBuilding = new BuildingId(tag.getUUID("homeBuilding"));
      }

      if (tag.hasUUID("constructionBuilding")) {
         this.constructionBuildingId = new BuildingId(tag.getUUID("constructionBuilding"));
      }

      if (tag.contains("foreignMerchantStallId")) {
         this.foreignMerchantStallId = tag.getInt("foreignMerchantStallId");
         if (this.foreignMerchantStallId >= 0) {
            this.entityData.set(DATA_FOREIGN_MERCHANT, true);
         }
      }

      if (tag.contains("visitorNbNights")) {
         this.visitorNbNights = tag.getInt("visitorNbNights");
      }

      if (tag.contains("sleepDebtTicks")) {
         this.sleepDebtTicks = Math.max(0, Math.min(6000, tag.getInt("sleepDebtTicks")));
      }

      VillagerIdentity loaded = VillagerIdentity.load(tag);
      this.identity.setModelType(loaded.getModelType());
      this.identity.setTexture(loaded.getTexture());
      this.identity.setClothTexture0(loaded.getClothTexture0());
      this.identity.setClothTexture1(loaded.getClothTexture1());
      this.identity.setVillagerScale(loaded.getVillagerScale());
      this.identity.setFirstName(loaded.getFirstName());
      this.identity.setFamilyName(loaded.getFamilyName());
      this.identity.setRoleName(loaded.getRoleName());
      this.identity.setFathersName(loaded.getFathersName());
      this.identity.setMothersName(loaded.getMothersName());
      this.identity.setSpousesName(loaded.getSpousesName());
      this.identity.setMaidenName(loaded.getMaidenName());
      this.identity.setClothName(loaded.getClothName());
      if (loaded.getChildSize() >= 0) {
         this.setChildSize(loaded.getChildSize());
      }

      if (VillagerAppearanceFactory.isCorruptedName(this.identity.getFirstName()) && this.villagerTypeId != null) {
         VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
         if (vType != null) {
            String[] names = VillagerAppearanceFactory.generateName(vType);
            if (!VillagerAppearanceFactory.isCorruptedName(names[0])) {
               this.identity.setFirstName(names[0]);
               this.identity.setFamilyName(names[1]);
               LOGGER.info("[Millénaire] Regenerated corrupted name for {} → {}", this.villagerTypeId, names[0]);
            }
         }
      }

      this.entityData.set(DATA_VILLAGER_TYPE, this.villagerTypeId != null ? this.villagerTypeId.toString() : "");
      this.entityData.set(DATA_MODEL_TYPE, this.identity.getModelType().toByte());
      this.entityData.set(DATA_TEXTURE, this.identity.getTexture() != null ? this.identity.getTexture().toString() : "");
      this.entityData.set(DATA_CLOTH_0, this.identity.getClothTexture0() != null ? this.identity.getClothTexture0().toString() : "");
      this.entityData.set(DATA_CLOTH_1, this.identity.getClothTexture1() != null ? this.identity.getClothTexture1().toString() : "");
      this.entityData.set(DATA_SCALE, this.identity.getVillagerScale());
      this.entityData.set(DATA_DISPLAY_NAME, this.getVillagerDisplayName());
      this.entityData.set(DATA_ROLE_NAME, this.identity.getRoleName());
      this.syncNativeRoleName();
      if (this.villagerTypeId != null) {
         VillagerType vType2 = ModCultures.getVillagerType(this.villagerTypeId);
         this.entityData.set(DATA_IS_CHIEF, vType2 != null && vType2.hasTag("chief"));
      }

      this.inventory.load(tag);
      if (tag.getBoolean("customSleeping")) {
         this.entityData.set(DATA_SLEEPING, true);
      }

      this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      VillagerType vType = this.villagerTypeId != null ? ModCultures.getVillagerType(this.villagerTypeId) : null;
      if (vType != null) {
         this.updateClothTextures(vType);
      }
   }

   private void enforceSleepInvariants() {
      boolean vanillaSleeping = this.isSleeping();
      boolean customSleeping = this.isVillagerSleeping();
      if (vanillaSleeping || customSleeping) {
         if (!TickConstants.isNight(this.level())) {
            if (this.goalScheduler == null || !RestGoal.ID.equals(this.goalScheduler.getCurrentGoalId())) {
               if (vanillaSleeping) {
                  this.stopSleeping();
               }

               if (customSleeping) {
                  this.setVillagerSleeping(false);
               }

               if (this.getPose() == Pose.SLEEPING) {
                  this.setPose(Pose.STANDING);
               }

               this.navEventLog.record(this.level().getGameTime(), NavEvent.Layer.SCHEDULER, NavEvent.Type.POSE_SLEEPING_CLEARED, "reason=daytime-safeguard");
               NavigationCounters.incPoseSleepingCleared();
               LOGGER.debug("[Millénaire] BUG-164 safeguard: cleared stuck sleep state on {} (daytime, no RestGoal active)", this.getVillagerDisplayName());
            }
         }
      }
   }

   public void onAddedToLevel() {
      super.onAddedToLevel();
      if (this.level() instanceof ServerLevel) {
         boolean vanillaSleeping = this.getSleepingPos().isPresent();
         boolean customSleeping = this.isVillagerSleeping();
         if (vanillaSleeping || customSleeping) {
            long gameTime = this.level().getGameTime();
            boolean isNight = TickConstants.isNight(this.level());
            long dayTime = this.level().getDayTime() % 24000L;
            boolean bedValid = false;
            if (vanillaSleeping) {
               BlockPos bedPos = (BlockPos)this.getSleepingPos().get();
               bedValid = this.level().getBlockState(bedPos).getBlock() instanceof BedBlock;
            }

            boolean shouldPreserve = isNight && (customSleeping || bedValid);
            if (shouldPreserve) {
               this.navEventLog.record(gameTime, NavEvent.Layer.RELOAD, NavEvent.Type.POSE_SLEEPING_RESTORED, bedValid ? "via=bed" : "via=custom");
               NavigationCounters.incPoseSleepingRestored();
            } else {
               String reason = !isNight ? "daytime" : "bed-gone";
               if (vanillaSleeping) {
                  this.stopSleeping();
               }

               if (customSleeping) {
                  this.setVillagerSleeping(false);
               }

               if (this.getPose() == Pose.SLEEPING) {
                  this.setPose(Pose.STANDING);
               }

               this.navEventLog.record(gameTime, NavEvent.Layer.RELOAD, NavEvent.Type.POSE_SLEEPING_CLEARED, "reason=" + reason);
               NavigationCounters.incPoseSleepingCleared();
            }
         }
      }
   }
}
