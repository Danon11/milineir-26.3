package org.millenaire.village;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.millenaire.culture.ReputationLabel;

public class VillageReputation {
   private final Map<UUID, Integer> reputations = new HashMap<>();

   public int get(UUID player) {
      return this.reputations.getOrDefault(player, 0);
   }

   public int add(UUID player, int amount) {
      int newValue = this.get(player) + amount;
      this.reputations.put(player, newValue);
      return newValue;
   }

   public Map<UUID, Integer> getAll() {
      return Collections.unmodifiableMap(this.reputations);
   }

   @Nullable
   public static String getLabel(int value, List<ReputationLabel> labels) {
      if (labels != null && !labels.isEmpty()) {
         String result = null;

         for (ReputationLabel label : labels) {
            if (label.threshold() > value) {
               break;
            }

            result = label.key();
         }

         return result;
      } else {
         return null;
      }
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag list = new ListTag();

      for (Entry<UUID, Integer> entry : this.reputations.entrySet()) {
         CompoundTag entryTag = new CompoundTag();
         entryTag.putUUID("player", entry.getKey());
         entryTag.putInt("value", entry.getValue());
         list.add(entryTag);
      }

      tag.put("player_reputations", list);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.reputations.clear();
      if (tag.contains("player_reputations")) {
         ListTag list = tag.getList("player_reputations", 10);

         for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID player = entryTag.getUUID("player");
            int value = entryTag.getInt("value");
            this.reputations.put(player, value);
         }
      }
   }
}
