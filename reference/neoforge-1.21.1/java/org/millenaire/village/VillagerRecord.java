package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModelType;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.entity.VillagerIdentity;
import org.millenaire.entity.VillagerInventory;
import org.slf4j.Logger;

public class VillagerRecord {
   private static final Logger LOGGER = LogUtils.getLogger();
   private UUID uuid;
   private ResourceLocation villagerTypeId;
   @Nullable
   private BuildingId homeBuilding;
   private VillagerIdentity identity = new VillagerIdentity();
   private final VillagerInventory inventory = new VillagerInventory();
   private boolean killed;
   private long lastRespawnTick;
   @Nullable
   private BlockPos lastKnownPos;
   private int visitorNbNights;
   private final List<String> questTags = new ArrayList<>();

   public VillagerRecord(UUID uuid, ResourceLocation villagerTypeId, @Nullable BuildingId homeBuilding) {
      this.uuid = uuid;
      this.villagerTypeId = villagerTypeId;
      this.homeBuilding = homeBuilding;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public void setUuid(UUID uuid) {
      this.uuid = uuid;
   }

   @Nullable
   public ResourceLocation getVillagerTypeId() {
      return this.villagerTypeId;
   }

   public void setVillagerTypeId(ResourceLocation villagerTypeId) {
      this.villagerTypeId = villagerTypeId;
   }

   @Nullable
   public BuildingId getHomeBuilding() {
      return this.homeBuilding;
   }

   public void setHomeBuilding(@Nullable BuildingId homeBuilding) {
      this.homeBuilding = homeBuilding;
   }

   public VillagerIdentity getIdentity() {
      return this.identity;
   }

   public String getFirstName() {
      return this.identity.getFirstName();
   }

   public String getFamilyName() {
      return this.identity.getFamilyName();
   }

   public String getRoleName() {
      return this.identity.getRoleName();
   }

   public String getFathersName() {
      return this.identity.getFathersName();
   }

   public String getMothersName() {
      return this.identity.getMothersName();
   }

   public String getSpousesName() {
      return this.identity.getSpousesName();
   }

   public String getMaidenName() {
      return this.identity.getMaidenName();
   }

   @Nullable
   public ResourceLocation getTexture() {
      return this.identity.getTexture();
   }

   @Nullable
   public ResourceLocation getClothTexture0() {
      return this.identity.getClothTexture0();
   }

   @Nullable
   public ResourceLocation getClothTexture1() {
      return this.identity.getClothTexture1();
   }

   public float getVillagerScale() {
      return this.identity.getVillagerScale();
   }

   public ModelType getModelType() {
      return this.identity.getModelType();
   }

   public int getChildSize() {
      return this.identity.getChildSize();
   }

   public VillagerInventory getInventory() {
      return this.inventory;
   }

   public boolean isKilled() {
      return this.killed;
   }

   public void setKilled(boolean killed) {
      this.killed = killed;
   }

   public long getLastRespawnTick() {
      return this.lastRespawnTick;
   }

   public void setLastRespawnTick(long lastRespawnTick) {
      this.lastRespawnTick = lastRespawnTick;
   }

   @Nullable
   public BlockPos getLastKnownPos() {
      return this.lastKnownPos;
   }

   public int getVisitorNbNights() {
      return this.visitorNbNights;
   }

   public void setVisitorNbNights(int visitorNbNights) {
      this.visitorNbNights = visitorNbNights;
   }

   public List<String> getQuestTags() {
      return Collections.unmodifiableList(this.questTags);
   }

   public void addQuestTag(String tag) {
      if (!this.questTags.contains(tag)) {
         this.questTags.add(tag);
      }
   }

   public void removeQuestTag(String tag) {
      this.questTags.remove(tag);
   }

   public boolean hasQuestTag(String tag) {
      return this.questTags.contains(tag);
   }

   public void updateFromEntity(MillVillager villager) {
      this.villagerTypeId = villager.getVillagerTypeId();
      this.homeBuilding = villager.getHomeBuilding();
      this.identity = new VillagerIdentity(villager.getIdentity());
      this.lastKnownPos = villager.blockPosition();
      this.visitorNbNights = villager.getVisitorNbNights();
      this.inventory.clear();

      for (Entry<Item, Integer> entry : villager.getInventory().getAll().entrySet()) {
         this.inventory.add(entry.getKey(), entry.getValue());
      }
   }

   public void applyToEntity(MillVillager villager) {
      villager.setVillagerTypeId(this.villagerTypeId);
      villager.setHomeBuilding(this.homeBuilding);
      if (VillagerAppearanceFactory.isCorruptedName(this.identity.getFirstName()) && this.villagerTypeId != null) {
         VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
         if (vType != null) {
            String[] names = VillagerAppearanceFactory.generateName(vType);
            if (!VillagerAppearanceFactory.isCorruptedName(names[0])) {
               this.identity.setFirstName(names[0]);
               this.identity.setFamilyName(names[1]);
            }
         }
      }

      villager.initAppearance(
         this.identity.getModelType(),
         this.identity.getTexture(),
         this.identity.getClothTexture0(),
         this.identity.getClothTexture1(),
         this.identity.getVillagerScale(),
         this.identity.getFirstName(),
         this.identity.getFamilyName(),
         this.identity.getRoleName()
      );
      villager.setFathersName(this.identity.getFathersName());
      villager.setMothersName(this.identity.getMothersName());
      villager.setSpousesName(this.identity.getSpousesName());
      villager.setMaidenName(this.identity.getMaidenName());
      if (this.identity.getChildSize() >= 0) {
         villager.setChildSize(this.identity.getChildSize());
      }

      villager.getInventory().clear();

      for (Entry<Item, Integer> entry : this.inventory.getAll().entrySet()) {
         villager.getInventory().add(entry.getKey(), entry.getValue());
      }

      villager.getIdentity().setClothName(this.identity.getClothName());
      VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
      if (vType != null) {
         villager.updateClothTextures(vType);
      }

      villager.setVisitorNbNights(this.visitorNbNights);
   }

   public void save(CompoundTag tag) {
      tag.putUUID("uuid", this.uuid);
      tag.putString("type", this.villagerTypeId.toString());
      if (this.homeBuilding != null) {
         tag.putUUID("home", this.homeBuilding.uuid());
      }

      this.identity.save(tag);
      tag.putBoolean("killed", this.killed);
      tag.putLong("lastRespawnTick", this.lastRespawnTick);
      if (this.lastKnownPos != null) {
         tag.putIntArray("last_known_pos", new int[]{this.lastKnownPos.getX(), this.lastKnownPos.getY(), this.lastKnownPos.getZ()});
      }

      tag.putInt("visitor_nb_nights", this.visitorNbNights);
      CompoundTag invTag = new CompoundTag();
      this.inventory.save(invTag);
      tag.put("inventory", invTag);
      if (!this.questTags.isEmpty()) {
         ListTag tagList = new ListTag();

         for (String qt : this.questTags) {
            tagList.add(StringTag.valueOf(qt));
         }

         tag.put("questTags", tagList);
      }
   }

   @Nullable
   public static VillagerRecord load(CompoundTag tag) {
      try {
         UUID uuid = tag.getUUID("uuid");
         ResourceLocation typeId = ResourceLocation.parse(tag.getString("type"));
         BuildingId home = tag.hasUUID("home") ? new BuildingId(tag.getUUID("home")) : null;
         VillagerRecord record = new VillagerRecord(uuid, typeId, home);
         record.identity = VillagerIdentity.load(tag);
         record.killed = tag.getBoolean("killed");
         record.lastRespawnTick = tag.getLong("lastRespawnTick");
         if (tag.contains("last_known_pos")) {
            int[] pos = tag.getIntArray("last_known_pos");
            if (pos.length == 3) {
               record.lastKnownPos = new BlockPos(pos[0], pos[1], pos[2]);
            }
         }

         record.visitorNbNights = tag.getInt("visitor_nb_nights");
         if (tag.contains("inventory")) {
            record.inventory.load(tag.getCompound("inventory"));
         }

         if (tag.contains("questTags")) {
            ListTag tagList = tag.getList("questTags", 8);

            for (int i = 0; i < tagList.size(); i++) {
               record.questTags.add(tagList.getString(i));
            }
         }

         return record;
      } catch (Exception e) {
         LOGGER.error("Unable to load VillagerRecord from NBT: {}", e.getMessage());
         return null;
      }
   }
}
