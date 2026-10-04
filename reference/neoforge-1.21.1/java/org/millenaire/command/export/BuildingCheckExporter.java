package org.millenaire.command.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.Village;

public final class BuildingCheckExporter {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private BuildingCheckExporter() {
   }

   public static Path export(ServerLevel level, Village village, Path dir) throws IOException {
      Path file = dir.resolve("building-check.json");
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("village", village.getVillageTypeId().toString());
      root.put("center", blockPosStr(village.getCenter()));
      List<Map<String, Object>> buildings = new ArrayList<>();

      for (BuildingInstance b : village.getBuildings()) {
         buildings.add(checkBuilding(level, b));
      }

      root.put("buildings", buildings);
      Files.writeString(file, GSON.toJson(root));
      return file;
   }

   private static Map<String, Object> checkBuilding(ServerLevel level, BuildingInstance b) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("plan", b.getPlanId().toString());
      map.put("origin", blockPosStr(b.getOrigin()));
      map.put("rotation", b.getRotation().name());
      BuildingPlan plan = ModCultures.getBuildingPlan(b.getPlanId());
      if (plan != null) {
         map.put("groundLevel", plan.groundLevel());
         map.put("size", plan.width() + "x" + plan.height() + "x" + plan.depth());
      }

      int cx = b.getOrigin().getX();
      int cz = b.getOrigin().getZ();
      if (plan != null) {
         cx += plan.width() / 2;
         cz += plan.depth() / 2;
      }

      int surfaceY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
      map.put("surfaceYAtCenter", surfaceY);
      map.put("originY", b.getOrigin().getY());
      List<String> column = new ArrayList<>();
      int scanBottom = b.getOrigin().getY() - 5;
      int scanTop = b.getOrigin().getY() + (plan != null ? plan.height() + 3 : 15);

      for (int y = scanBottom; y <= scanTop; y++) {
         BlockState state = level.getBlockState(new BlockPos(cx, y, cz));
         String label = "";
         if (y == b.getOrigin().getY()) {
            label = " <-- ORIGIN_Y";
         }

         if (plan != null && y == b.getOrigin().getY() - plan.groundLevel()) {
            label = " <-- SURFACE (origin - groundLevel)";
         }

         column.add("Y=" + y + ": " + state.getBlock().getName().getString() + label);
      }

      map.put("centerColumn", column);
      List<SpecialPoint> points = b.getResolvedPoints();
      List<Map<String, Object>> checks = new ArrayList<>();

      for (SpecialPoint sp : points) {
         if (sp.pos() != null) {
            String type = sp.type();
            if (!type.equals("preserve_ground") && !type.equals("sign_pos")) {
               Map<String, Object> check = new LinkedHashMap<>();
               check.put("type", type);
               check.put("pos", blockPosStr(sp.pos()));
               BlockPos pos = sp.pos();
               BlockState atPos = level.getBlockState(pos);
               BlockState below = level.getBlockState(pos.below());
               BlockState above = level.getBlockState(pos.above());
               BlockState above2 = level.getBlockState(pos.above(2));
               check.put("blockAtPos", atPos.getBlock().getName().getString());
               check.put("blockBelow", below.getBlock().getName().getString());
               check.put("blockAbove", above.getBlock().getName().getString());
               check.put("blockAbove2", above2.getBlock().getName().getString());
               List<String> issues = new ArrayList<>();
               boolean isAccessPoint = type.equals("path_start_pos") || type.equals("selling_pos") || type.equals("gathering_pos") || type.equals("source_pos");
               if (isAccessPoint) {
                  if (!atPos.isAir()) {
                     issues.add("BLOCKED: block " + atPos.getBlock().getName().getString() + " instead of air");
                  }

                  if (!above.isAir()) {
                     issues.add("BLOCKED ABOVE: " + above.getBlock().getName().getString());
                  }

                  if (below.isAir()) {
                     issues.add("NO GROUND below");
                  }
               }

               if (type.equals("sleep_pos") && !above2.isAir()) {
                  issues.add("LOW ROOF: " + above2.getBlock().getName().getString() + " at +2");
               }

               check.put("issues", issues);
               checks.add(check);
            }
         }
      }

      map.put("specialPointChecks", checks);
      long issueCount = checks.stream().mapToLong(c -> ((List)c.get("issues")).size()).sum();
      map.put("totalIssues", issueCount);
      return map;
   }

   private static String blockPosStr(BlockPos pos) {
      return pos.getX() + "," + pos.getY() + "," + pos.getZ();
   }
}
