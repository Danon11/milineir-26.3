package org.millenaire.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.millenaire.building.BuildingInstance;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public final class VillageMapMarkerService {
   private VillageMapMarkerService() {
   }

   public static void processAllPlayers(MinecraftServer server) {
      ServerLevel overworld = server.overworld();
      if (overworld != null) {
         VillageManager villageManager = VillageSavedData.get(overworld).getVillageManager();
         Collection<Village> villages = villageManager.getAllVillages();
         Map<VillageId, VillageDiscoveryHelper.Footprint> townHallFootprints = new HashMap<>(villages.size());
         List<VillageMapMarker.VillageView> aliveViews = new ArrayList<>(villages.size());
         Set<VillageId> aliveIds = new HashSet<>(villages.size());

         for (Village v : villages) {
            if (!v.isLoneBuilding()) {
               aliveIds.add(v.getId());
               aliveViews.add(
                  new VillageMapMarker.VillageView(
                     v.getId(), v.getCenter(), overworld.dimension(), v.getCultureId(), v.getVillageName() == null ? "" : v.getVillageName()
                  )
               );
               BuildingInstance townhall = v.getTownhall();
               if (townhall != null && townhall.getEffectiveWidth() > 0) {
                  int ox = townhall.getOrigin().getX();
                  int oz = townhall.getOrigin().getZ();
                  townHallFootprints.put(
                     v.getId(),
                     new VillageDiscoveryHelper.Footprint(
                        ox + townhall.getCachedMinX(), ox + townhall.getCachedMaxX(), oz + townhall.getCachedMinZ(), oz + townhall.getCachedMaxZ()
                     )
                  );
               }
            }
         }

         PlayerCultureReputation rep = PlayerCultureReputation.get(overworld);
         MillenaireMapMarkersData tracking = MillenaireMapMarkersData.get(overworld);

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(overworld.dimension())) {
               UUID uuid = player.getUUID();
               Set<VillageId> discoveredSnapshot = new HashSet<>(rep.getDiscoveredVillages(uuid));
               if (isHoldingFilledMap(player)) {
                  BlockPos pos = player.blockPosition();

                  for (VillageId vid : VillageDiscoveryHelper.findNewlyDiscovered(pos.getX(), pos.getZ(), townHallFootprints, discoveredSnapshot)) {
                     rep.markVillageDiscovered(uuid, vid);
                     discoveredSnapshot.add(vid);
                  }
               }

               refreshHeldMaps(player, overworld, aliveViews, aliveIds, discoveredSnapshot, tracking);
            }
         }
      }
   }

   private static boolean isHoldingFilledMap(ServerPlayer player) {
      return player.getMainHandItem().is(Items.FILLED_MAP) || player.getOffhandItem().is(Items.FILLED_MAP);
   }

   private static void refreshHeldMaps(
      ServerPlayer player,
      ServerLevel level,
      List<VillageMapMarker.VillageView> aliveViews,
      Set<VillageId> aliveIds,
      Set<VillageId> discovered,
      MillenaireMapMarkersData tracking
   ) {
      ItemStack[] hands = new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()};

      for (ItemStack stack : hands) {
         if (stack.is(Items.FILLED_MAP)) {
            MapId mapId = (MapId)stack.get(DataComponents.MAP_ID);
            if (mapId != null) {
               MapItemSavedData data = MapItem.getSavedData(stack, level);
               if (data != null) {
                  applyDelta(level, data, mapId.id(), aliveViews, aliveIds, discovered, tracking);
               }
            }
         }
      }
   }

   private static void applyDelta(
      ServerLevel level,
      MapItemSavedData data,
      int mapIdInt,
      List<VillageMapMarker.VillageView> aliveViews,
      Set<VillageId> aliveIds,
      Set<VillageId> discovered,
      MillenaireMapMarkersData tracking
   ) {
      Set<VillageId> trackedOnThisMap = new HashSet<>(tracking.tracked(mapIdInt));
      VillageMapMarker.MapView mv = new VillageMapMarker.MapView(data.centerX, data.centerZ, data.scale, data.dimension);
      VillageMapMarker.Delta delta = VillageMapMarker.computeDelta(mv, aliveViews, discovered, trackedOnThisMap);
      if (!delta.toAdd().isEmpty() || !delta.toRemove().isEmpty()) {
         String prefix = VillageMapMarker.keyPrefix();

         for (VillageMapMarker.MarkerToAdd m : delta.toAdd()) {
            Culture culture = ModCultures.getCulture(m.cultureId());
            Holder<MapDecorationType> type = (Holder<MapDecorationType>)(culture != null
               ? VillageMapDecorationTypes.resolveHolder(culture)
               : VillageMapDecorationTypes.GENERIC);
            data.addDecoration(type, level, m.key(), m.worldX(), m.worldZ(), 0.0, m.displayName());
            tracking.addTracked(mapIdInt, new VillageId(UUID.fromString(m.key().substring(prefix.length()))));
         }

         for (String key : delta.toRemove()) {
            data.removeDecoration(key);
            tracking.removeTracked(mapIdInt, new VillageId(UUID.fromString(key.substring(prefix.length()))));
         }
      }
   }
}
