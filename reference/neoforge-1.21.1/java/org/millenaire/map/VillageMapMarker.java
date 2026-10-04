package org.millenaire.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.millenaire.village.VillageId;

public final class VillageMapMarker {
   private static final String KEY_PREFIX = "millenaire:village/";

   private VillageMapMarker() {
   }

   public static VillageMapMarker.Delta computeDelta(
      VillageMapMarker.MapView map, List<VillageMapMarker.VillageView> aliveVillages, Set<VillageId> discoveredByPlayer, Set<VillageId> trackedOnThisMap
   ) {
      Set<VillageId> aliveIds = new HashSet<>();

      for (VillageMapMarker.VillageView v : aliveVillages) {
         aliveIds.add(v.id());
      }

      List<VillageMapMarker.MarkerToAdd> toAdd = new ArrayList<>();

      for (VillageMapMarker.VillageView v : aliveVillages) {
         if (discoveredByPlayer.contains(v.id()) && v.dimension().equals(map.dimension()) && withinBounds(map, v.center())) {
            toAdd.add(
               new VillageMapMarker.MarkerToAdd(
                  "millenaire:village/" + v.id().uuid(), v.cultureId(), v.center().getX(), v.center().getZ(), Component.literal(v.displayName())
               )
            );
         }
      }

      List<String> toRemove = new ArrayList<>();

      for (VillageId tracked : trackedOnThisMap) {
         if (!aliveIds.contains(tracked)) {
            toRemove.add("millenaire:village/" + tracked.uuid());
         }
      }

      return new VillageMapMarker.Delta(toAdd, toRemove);
   }

   private static boolean withinBounds(VillageMapMarker.MapView map, BlockPos pos) {
      int halfWidth = 63 * (1 << map.scale());
      return Math.abs(pos.getX() - map.centerX()) <= halfWidth && Math.abs(pos.getZ() - map.centerZ()) <= halfWidth;
   }

   public static String keyPrefix() {
      return "millenaire:village/";
   }

   public record Delta(List<VillageMapMarker.MarkerToAdd> toAdd, List<String> toRemove) {
   }

   public record MapView(int centerX, int centerZ, int scale, ResourceKey<Level> dimension) {
   }

   public record MarkerToAdd(String key, ResourceLocation cultureId, int worldX, int worldZ, Component displayName) {
   }

   public record VillageView(VillageId id, BlockPos center, ResourceKey<Level> dimension, ResourceLocation cultureId, String displayName) {
      public VillageView {
         Objects.requireNonNull(displayName, "displayName");
      }
   }
}
