package org.millenaire.village;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.millenaire.building.BuildingInstance;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;

public final class HearthResidentResolver {
   private HearthResidentResolver() {
   }

   public static Optional<UUID> findResident(Village village, BuildingInstance building) {
      return village.getVillagerRecords()
         .values()
         .stream()
         .filter(HearthResidentResolver::isEligible)
         .filter(r -> building.getId().equals(r.getHomeBuilding()))
         .map(VillagerRecord::getUuid)
         .min(Comparator.naturalOrder());
   }

   public static boolean isDesignatedResident(Village village, BuildingInstance building, @Nullable UUID villagerUuid) {
      return villagerUuid == null ? false : findResident(village, building).map(uuid -> uuid.equals(villagerUuid)).orElse(false);
   }

   static boolean isEligible(VillagerRecord record) {
      if (record.isKilled()) {
         return false;
      }

      VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
      return vType == null ? false : !vType.isChild();
   }
}
