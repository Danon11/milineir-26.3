package org.millenaire.command.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.ConstructionTask;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public final class MapExporter {
   private static final int MAX_WIDTH = 160;
   private static final int MAX_HEIGHT = 120;

   private MapExporter() {
   }

   public static Path export(ServerLevel level, Village village, Path dir) throws IOException {
      Path file = dir.resolve("village-map.txt");
      BlockPos center = village.getCenter();
      List<BuildingInstance> buildings = village.getBuildings();
      Map<UUID, ResourceLocation> villagerTypes = village.getVillagerTypes();
      List<MapExporter.VillagerInfo> villagers = new ArrayList<>();
      int idx = 0;

      for (Entry<UUID, ResourceLocation> entry : villagerTypes.entrySet()) {
         Entity entity = level.getEntity(entry.getKey());
         BlockPos pos = null;
         if (entity instanceof MillVillager mv) {
            pos = mv.blockPosition();
         }

         villagers.add(new MapExporter.VillagerInfo(entry.getKey(), entry.getValue(), pos, idx % 10));
         idx++;
      }

      List<MapExporter.BuildingFootprint> footprints = new ArrayList<>();

      for (BuildingInstance bi : buildings) {
         BuildingPlan plan = ModCultures.getBuildingPlan(bi.getPlanId());
         int d;
         int w;
         if (plan != null) {
            w = plan.width();
            d = plan.depth();
         } else {
            w = 5;
            d = 5;
         }

         Rotation rot = bi.getRotation();
         if (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) {
            int tmp = w;
            w = d;
            d = tmp;
         }

         String label = abbreviate(bi.getPlanId());
         footprints.add(new MapExporter.BuildingFootprint(bi, bi.getOrigin(), w, d, label, plan));
      }

      int minX = center.getX();
      int maxX = center.getX();
      int minZ = center.getZ();
      int maxZ = center.getZ();

      for (MapExporter.BuildingFootprint fp : footprints) {
         minX = Math.min(minX, fp.origin.getX());
         maxX = Math.max(maxX, fp.origin.getX() + fp.width - 1);
         minZ = Math.min(minZ, fp.origin.getZ());
         maxZ = Math.max(maxZ, fp.origin.getZ() + fp.depth - 1);
      }

      for (MapExporter.VillagerInfo vi : villagers) {
         if (vi.pos != null) {
            minX = Math.min(minX, vi.pos.getX());
            maxX = Math.max(maxX, vi.pos.getX());
            minZ = Math.min(minZ, vi.pos.getZ());
            maxZ = Math.max(maxZ, vi.pos.getZ());
         }
      }

      minX -= 2;
      minZ -= 2;
      maxX += 2;
      maxZ += 2;
      int worldW = maxX - minX + 1;
      int worldD = maxZ - minZ + 1;
      int scale = 1;

      while ((worldW + scale - 1) / scale > 160 || (worldD + scale - 1) / scale > 120) {
         scale++;
      }

      int gridW = (worldW + scale - 1) / scale;
      int gridH = (worldD + scale - 1) / scale;
      char[][] grid = new char[gridH][gridW];
      boolean[][] isBuilding = new boolean[gridH][gridW];

      for (int r = 0; r < gridH; r++) {
         for (int c = 0; c < gridW; c++) {
            grid[r][c] = '.';
         }
      }

      for (int bIdx = 0; bIdx < footprints.size(); bIdx++) {
         MapExporter.BuildingFootprint fp = footprints.get(bIdx);
         int ox = (fp.origin.getX() - minX) / scale;
         int oz = (fp.origin.getZ() - minZ) / scale;
         int fw = Math.max(1, fp.width / scale);
         int fd = Math.max(1, fp.depth / scale);

         for (int dz = 0; dz < fd; dz++) {
            for (int dx = 0; dx < fw; dx++) {
               int gx = ox + dx;
               int gz = oz + dz;
               if (gx >= 0 && gx < gridW && gz >= 0 && gz < gridH) {
                  grid[gz][gx] = '#';
                  isBuilding[gz][gx] = true;
               }
            }
         }

         int cx = ox + fw / 2;
         int cz = oz + fd / 2;
         String lbl = fp.label;
         int startX = cx - lbl.length() / 2;

         for (int i = 0; i < lbl.length(); i++) {
            int gx = startX + i;
            if (gx >= 0 && gx < gridW && cz >= 0 && cz < gridH) {
               grid[cz][gx] = lbl.charAt(i);
               isBuilding[cz][gx] = true;
            }
         }
      }

      for (MapExporter.VillagerInfo vi : villagers) {
         if (vi.pos != null) {
            int gx = (vi.pos.getX() - minX) / scale;
            int gz = (vi.pos.getZ() - minZ) / scale;
            if (gx >= 0 && gx < gridW && gz >= 0 && gz < gridH && !isBuilding[gz][gx]) {
               grid[gz][gx] = (char)(48 + vi.digit);
            }
         }
      }

      int centerGx = (center.getX() - minX) / scale;
      int centerGz = (center.getZ() - minZ) / scale;
      if (centerGx >= 0 && centerGx < gridW && centerGz >= 0 && centerGz < gridH) {
         grid[centerGz][centerGx] = '*';
      }

      StringBuilder sb = new StringBuilder();
      sb.append("=== Village Map ===\n");
      sb.append("Type:       ").append(village.getVillageTypeId()).append('\n');
      sb.append("Center:     ").append(formatPos(center)).append('\n');
      sb.append("UUID:       ").append(village.getId()).append('\n');
      sb.append("Buildings:  ").append(buildings.size()).append('\n');
      sb.append("Villagers:  ").append(villagerTypes.size()).append('\n');
      sb.append("Scale:      1 char = ").append(scale).append(" block(s)\n");
      sb.append('\n');
      sb.append("--- Grid (X horizontal, Z vertical) ---\n");

      for (int r = 0; r < gridH; r++) {
         sb.append(new String(grid[r]));
         sb.append('\n');
      }

      sb.append('\n');
      sb.append("--- Buildings ---\n");

      for (int i = 0; i < footprints.size(); i++) {
         MapExporter.BuildingFootprint fp = footprints.get(i);
         BuildingInstance bi = fp.instance;
         sb.append(String.format("[%s] %s\n", fp.label, bi.getPlanId()));
         sb.append(String.format("  Status: %s", bi.getStatus()));
         ConstructionTask ct = bi.getConstructionTask();
         if (ct != null) {
            sb.append(String.format("  Progress: %d/%d (%.0f%%)", ct.getNextStepIndex(), ct.totalSteps(), ct.progress() * 100.0F));
         }

         sb.append('\n');
         sb.append(String.format("  Level: %d  Position: %s  Size: %dx%d", bi.getLevel(), formatPos(bi.getOrigin()), fp.width, fp.depth));
         sb.append('\n');
         if (fp.plan != null && !fp.plan.tags().isEmpty()) {
            sb.append("  Tags: ").append(String.join(", ", fp.plan.tags())).append('\n');
         }
      }

      sb.append('\n');
      sb.append("--- Villagers ---\n");

      for (MapExporter.VillagerInfo vi : villagers) {
         String posStr = vi.pos != null ? formatPos(vi.pos) : "not loaded";
         sb.append(String.format("[%d] %s  at %s\n", vi.digit, vi.typeId, posStr));
      }

      Files.writeString(file, sb.toString());
      return file;
   }

   static String abbreviate(ResourceLocation planId) {
      String path = planId.getPath();
      int slashIdx = path.lastIndexOf(47);
      String name = slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
      name = name.replaceAll("_[a-z]_\\d+$", "");
      if (name.isEmpty()) {
         return "???";
      }

      String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
      if (cap.length() > 3) {
         cap = cap.substring(0, 3);
      }

      return cap;
   }

   private static String formatPos(BlockPos pos) {
      return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
   }

   private record BuildingFootprint(BuildingInstance instance, BlockPos origin, int width, int depth, String label, @Nullable BuildingPlan plan) {
   }

   private record VillagerInfo(UUID uuid, ResourceLocation typeId, @Nullable BlockPos pos, int digit) {
   }
}
