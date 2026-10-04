package org.millenaire.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;

public class BedManager {
   public static final long SUFFOCATION_TTL_TICKS = 24000L;
   private final List<BlockPos> bedPositions = new ArrayList<>();
   private final Map<BlockPos, UUID> claimedBeds = new HashMap<>();
   private final Map<BlockPos, Long> suffocatingExpiry = new HashMap<>();
   private boolean dirty;
   private static final String KEY_POS = "pos";
   private static final String KEY_UUID = "uuid";
   private static final String KEY_SUFFOCATION_EXPIRY = "suffocExpiry";

   public void add(BlockPos footPos) {
      if (!this.bedPositions.contains(footPos)) {
         this.bedPositions.add(footPos);
         this.dirty = true;
      }
   }

   public void remove(BlockPos footPos) {
      if (this.bedPositions.remove(footPos)) {
         this.claimedBeds.remove(footPos);
         this.suffocatingExpiry.remove(footPos);
         this.dirty = true;
      }
   }

   public boolean hasBeds() {
      return !this.bedPositions.isEmpty();
   }

   public int getBedCount() {
      return this.bedPositions.size();
   }

   public boolean claimBed(BlockPos footPos, UUID villagerUuid) {
      if (!this.bedPositions.contains(footPos)) {
         return false;
      }

      if (this.claimedBeds.containsKey(footPos)) {
         return false;
      }

      this.claimedBeds.put(footPos, villagerUuid);
      this.dirty = true;
      return true;
   }

   public void releaseBed(BlockPos footPos) {
      if (this.claimedBeds.remove(footPos) != null) {
         this.dirty = true;
      }
   }

   public void releaseBedByVillager(UUID villagerUuid) {
      Iterator<Entry<BlockPos, UUID>> it = this.claimedBeds.entrySet().iterator();

      while (it.hasNext()) {
         if (it.next().getValue().equals(villagerUuid)) {
            it.remove();
            this.dirty = true;
         }
      }
   }

   public Optional<BlockPos> getClaimedBed(UUID villagerUuid) {
      for (Entry<BlockPos, UUID> entry : this.claimedBeds.entrySet()) {
         if (entry.getValue().equals(villagerUuid)) {
            return Optional.of(entry.getKey());
         }
      }

      return Optional.empty();
   }

   public Optional<BlockPos> findNearestUnclaimedBed(BlockPos origin, double maxDistance) {
      return this.findNearestUnclaimedBed(origin, maxDistance, 0L);
   }

   public Optional<BlockPos> findNearestUnclaimedBed(BlockPos origin, double maxDistance, long nowGameTime) {
      double maxDistSq = maxDistance * maxDistance;
      BlockPos best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (BlockPos pos : this.bedPositions) {
         if (!this.claimedBeds.containsKey(pos) && (nowGameTime <= 0L || !this.isSuffocatingMarked(pos, nowGameTime))) {
            double distSq = origin.distSqr(pos);
            if (!(distSq > maxDistSq) && distSq < bestDistSq) {
               bestDistSq = distSq;
               best = pos;
            }
         }
      }

      return Optional.ofNullable(best);
   }

   public void markSuffocating(BlockPos footPos, long nowGameTime) {
      this.suffocatingExpiry.put(footPos, nowGameTime + 24000L);
      this.dirty = true;
   }

   public boolean isSuffocatingMarked(BlockPos footPos, long nowGameTime) {
      Long expiry = this.suffocatingExpiry.get(footPos);
      if (expiry == null) {
         return false;
      } else if (expiry <= nowGameTime) {
         this.suffocatingExpiry.remove(footPos);
         this.dirty = true;
         return false;
      } else {
         return true;
      }
   }

   public void clearSuffocating(BlockPos footPos) {
      if (this.suffocatingExpiry.remove(footPos) != null) {
         this.dirty = true;
      }
   }

   public void clearAllSuffocating() {
      if (!this.suffocatingExpiry.isEmpty()) {
         this.suffocatingExpiry.clear();
         this.dirty = true;
      }
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public void clearDirty() {
      this.dirty = false;
   }

   public boolean validate(ServerLevel level, Set<UUID> knownVillagerUuids) {
      boolean changed = false;
      Iterator<BlockPos> posIt = this.bedPositions.iterator();

      while (posIt.hasNext()) {
         BlockPos pos = posIt.next();
         if (!(level.getBlockState(pos).getBlock() instanceof BedBlock)) {
            posIt.remove();
            this.claimedBeds.remove(pos);
            this.suffocatingExpiry.remove(pos);
            changed = true;
         }
      }

      Iterator<Entry<BlockPos, UUID>> claimIt = this.claimedBeds.entrySet().iterator();

      while (claimIt.hasNext()) {
         if (!knownVillagerUuids.contains(claimIt.next().getValue())) {
            claimIt.remove();
            changed = true;
         }
      }

      if (changed) {
         this.dirty = true;
      }

      return changed;
   }

   public ListTag save() {
      ListTag list = new ListTag();

      for (BlockPos pos : this.bedPositions) {
         CompoundTag entry = new CompoundTag();
         entry.putLong("pos", pos.asLong());
         UUID owner = this.claimedBeds.get(pos);
         if (owner != null) {
            entry.putUUID("uuid", owner);
         }

         Long expiry = this.suffocatingExpiry.get(pos);
         if (expiry != null) {
            entry.putLong("suffocExpiry", expiry);
         }

         list.add(entry);
      }

      return list;
   }

   public static BedManager load(ListTag list) {
      BedManager manager = new BedManager();

      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         BlockPos pos = BlockPos.of(entry.getLong("pos"));
         manager.bedPositions.add(pos);
         if (entry.hasUUID("uuid")) {
            manager.claimedBeds.put(pos, entry.getUUID("uuid"));
         }

         if (entry.contains("suffocExpiry")) {
            manager.suffocatingExpiry.put(pos, entry.getLong("suffocExpiry"));
         }
      }

      return manager;
   }
}
