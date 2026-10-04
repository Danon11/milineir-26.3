package org.millenaire.diagnostics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageWaypointGraph;
import org.millenaire.village.Waypoint;

public final class WaypointVisualizationManager {
   private static final int EMIT_INTERVAL_TICKS = 10;
   private static final int DEFAULT_DURATION_TICKS = 1200;
   private static final double RENDER_RADIUS = 96.0;
   private static final int NODE_PILLAR_HEIGHT = 4;
   private static final double EDGE_SAMPLE_STEP = 2.0;
   private static final float NODE_SCALE = 1.6F;
   private static final float EDGE_SCALE = 1.1F;
   private static final Vector3f CENTER_COLOR = new Vector3f(1.0F, 1.0F, 1.0F);
   private static final float HUE_GOLDEN = 0.61803F;
   private static final Map<UUID, WaypointVisualizationManager.ActiveViz> ACTIVE = new HashMap<>();

   private WaypointVisualizationManager() {
   }

   public static void toggle(ServerPlayer player, ServerLevel level, Village village) {
      UUID id = player.getUUID();
      WaypointVisualizationManager.ActiveViz existing = ACTIVE.remove(id);
      if (existing != null && existing.villageId.equals(village.getId())) {
         player.sendSystemMessage(Component.literal("§7[Wand] Waypoint visualization OFF."));
      } else {
         long now = level.getGameTime();
         ACTIVE.put(id, new WaypointVisualizationManager.ActiveViz(village.getId(), now + 1200L, level));
         VillageWaypointGraph graph = village.getWaypointGraph();
         player.sendSystemMessage(
            Component.literal(String.format("§a[Wand] Waypoint visualization ON — §c%d nodes§a, will auto-expire in %ds.", graph.waypointCount(), 60))
         );
         player.sendSystemMessage(Component.literal("§7[Wand] Re-click 'Visualize Waypoints' on a villager of the same village to turn off."));
      }
   }

   public static void clear(ServerPlayer player) {
      ACTIVE.remove(player.getUUID());
   }

   public static void tick(MinecraftServer server) {
      if (!ACTIVE.isEmpty()) {
         Iterator<Entry<UUID, WaypointVisualizationManager.ActiveViz>> it = ACTIVE.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, WaypointVisualizationManager.ActiveViz> entry = it.next();
            WaypointVisualizationManager.ActiveViz viz = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
               it.remove();
            } else {
               ServerLevel level = player.serverLevel();
               if (level != viz.level) {
                  it.remove();
               } else if (level.getGameTime() >= viz.expirationTick) {
                  player.sendSystemMessage(Component.literal("§7[Wand] Waypoint visualization expired."));
                  it.remove();
               } else if (level.getGameTime() % 10L == 0L) {
                  Village village = VillageSavedData.get(level).getVillageManager().getVillage(viz.villageId);
                  if (village == null) {
                     it.remove();
                  } else {
                     emitParticles(player, level, village);
                  }
               }
            }
         }
      }
   }

   private static void emitParticles(ServerPlayer player, ServerLevel level, Village village) {
      VillageWaypointGraph graph = village.getWaypointGraph();
      List<Waypoint> waypoints = graph.getWaypoints();
      if (!waypoints.isEmpty()) {
         Vector3f[] colors = new Vector3f[waypoints.size()];
         int hueIdx = 0;

         for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            if (wp.buildingId() == null) {
               colors[i] = CENTER_COLOR;
            } else {
               float hue = hueIdx++ * 0.61803F % 1.0F;
               colors[i] = hsvToRgb(hue, 0.85F, 1.0F);
            }
         }

         BlockPos origin = player.blockPosition();
         double renderSq = 9216.0;

         for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            if (!(wp.pos().distSqr(origin) > renderSq)) {
               DustParticleOptions dust = new DustParticleOptions(colors[i], 1.6F);

               for (int dy = 0; dy < 4; dy++) {
                  level.sendParticles(player, dust, true, wp.pos().getX() + 0.5, wp.pos().getY() + 1.0 + dy, wp.pos().getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }

         Map<Waypoint, Integer> indexOf = new HashMap<>();

         for (int i = 0; i < waypoints.size(); i++) {
            indexOf.put(waypoints.get(i), i);
         }

         for (VillageWaypointGraph.DirectedEdge edge : graph.getEdges()) {
            BlockPos fromPos = edge.from().pos();
            BlockPos toPos = edge.to().pos();
            if (!(fromPos.distSqr(origin) > renderSq) || !(toPos.distSqr(origin) > renderSq)) {
               Vector3f colorFrom = colors[indexOf.get(edge.from())];
               Vector3f colorTo = colors[indexOf.get(edge.to())];
               if (edge.pathNodes().isEmpty()) {
                  emitStraightEdge(player, level, fromPos, toPos, edge.cost(), colorFrom, colorTo);
               } else {
                  emitPathEdge(player, level, edge.pathNodes(), colorFrom, colorTo, origin, renderSq);
               }
            }
         }
      }
   }

   private static void emitPathEdge(
      ServerPlayer player, ServerLevel level, List<BlockPos> nodes, Vector3f colorFrom, Vector3f colorTo, BlockPos origin, double renderSq
   ) {
      int total = nodes.size();
      if (total >= 2) {
         for (int k = 0; k < total - 1; k++) {
            BlockPos a = nodes.get(k);
            BlockPos b = nodes.get(k + 1);
            if (!(a.distSqr(origin) > renderSq) || !(b.distSqr(origin) > renderSq)) {
               double dist = Math.sqrt(a.distSqr(b));
               int steps = Math.max(1, (int)Math.round(dist / 2.0));
               float tStart = (float)k / (total - 1);
               float tEnd = (float)(k + 1) / (total - 1);

               for (int s = 0; s <= steps; s++) {
                  float local = (float)s / steps;
                  float t = tStart + (tEnd - tStart) * local;
                  Vector3f color = new Vector3f(
                     colorFrom.x + (colorTo.x - colorFrom.x) * t, colorFrom.y + (colorTo.y - colorFrom.y) * t, colorFrom.z + (colorTo.z - colorFrom.z) * t
                  );
                  DustParticleOptions dust = new DustParticleOptions(color, 1.1F);
                  double x = a.getX() + 0.5 + (b.getX() - a.getX()) * local;
                  double y = a.getY() + 1.0 + (b.getY() - a.getY()) * local;
                  double z = a.getZ() + 0.5 + (b.getZ() - a.getZ()) * local;
                  level.sendParticles(player, dust, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   private static void emitStraightEdge(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to, double dist, Vector3f colorFrom, Vector3f colorTo) {
      int steps = Math.max(1, (int)Math.round(dist / 2.0));
      double dx = to.getX() - from.getX();
      double dy = to.getY() - from.getY();
      double dz = to.getZ() - from.getZ();

      for (int i = 1; i < steps; i++) {
         float t = (float)i / steps;
         Vector3f color = new Vector3f(
            colorFrom.x + (colorTo.x - colorFrom.x) * t, colorFrom.y + (colorTo.y - colorFrom.y) * t, colorFrom.z + (colorTo.z - colorFrom.z) * t
         );
         DustParticleOptions dust = new DustParticleOptions(color, 1.1F);
         level.sendParticles(player, dust, true, from.getX() + 0.5 + dx * t, from.getY() + 1.5 + dy * t, from.getZ() + 0.5 + dz * t, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static Vector3f hsvToRgb(float h, float s, float v) {
      int i = (int)(h * 6.0F) % 6;
      float f = h * 6.0F - (int)(h * 6.0F);
      float p = v * (1.0F - s);
      float q = v * (1.0F - f * s);
      float t = v * (1.0F - (1.0F - f) * s);

      return switch (i) {
         case 0 -> new Vector3f(v, t, p);
         case 1 -> new Vector3f(q, v, p);
         case 2 -> new Vector3f(p, v, t);
         case 3 -> new Vector3f(p, q, v);
         case 4 -> new Vector3f(t, p, v);
         default -> new Vector3f(v, p, q);
      };
   }

   private record ActiveViz(VillageId villageId, long expirationTick, ServerLevel level) {
   }
}
