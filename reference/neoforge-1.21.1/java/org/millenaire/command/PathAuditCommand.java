package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class PathAuditCommand {
   private PathAuditCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("path-audit").executes(PathAuditCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("No villages outside the Overworld."));
         return 0;
      }

      ServerPlayer player = source.getPlayer();
      BlockPos searchPos;
      if (player != null) {
         searchPos = player.blockPosition();
      } else {
         searchPos = BlockPos.ZERO;
      }

      Village village = VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 5000.0);
      if (village == null) {
         source.sendFailure(Component.literal("No village within 5000 blocks."));
         return 0;
      }

      int radius = 80;
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      if (vt != null) {
         radius = vt.radius();
      }

      int finalRadius = radius;
      PathAuditCommand.AuditReport report = scan(level, village.getCenter(), radius);
      source.sendSuccess(
         () -> Component.literal(
            "=== path-audit for village "
               + village.getId().uuid().toString().substring(0, 8)
               + " @"
               + village.getCenter().toShortString()
               + " r="
               + finalRadius
               + " ==="
         ),
         false
      );
      source.sendSuccess(() -> Component.literal("blocks.total=" + report.total), false);
      source.sendSuccess(() -> Component.literal("blocks.full=" + report.full), false);
      source.sendSuccess(() -> Component.literal("blocks.slab=" + report.slab), false);
      source.sendSuccess(() -> Component.literal("edges.total=" + report.edgesTotal), false);
      source.sendSuccess(() -> Component.literal("edges.flat=" + report.edgesFlat), false);
      source.sendSuccess(() -> Component.literal("edges.half=" + report.edgesHalf), false);
      source.sendSuccess(() -> Component.literal("edges.full_step=" + report.edgesFullStep), false);
      source.sendSuccess(() -> Component.literal("edges.big_step=" + report.edgesBigStep), false);
      source.sendSuccess(() -> Component.literal("peaks.isolated=" + report.isolatedPeaks), false);
      source.sendSuccess(() -> Component.literal("valleys.isolated=" + report.isolatedValleys), false);
      source.sendSuccess(() -> Component.literal("max_abs_dy_half=" + report.maxAbsDyHalf), false);
      String rough = String.format("%.3f", report.roughness());
      source.sendSuccess(() -> Component.literal("roughness=" + rough), false);
      int shown = 0;

      for (BlockPos p : report.peakSamples) {
         if (shown >= 5) {
            break;
         }

         source.sendSuccess(() -> Component.literal("sample.peak=" + p.toShortString()), false);
         shown++;
      }

      shown = 0;

      for (BlockPos p : report.valleySamples) {
         if (shown >= 5) {
            break;
         }

         source.sendSuccess(() -> Component.literal("sample.valley=" + p.toShortString()), false);
         shown++;
      }

      return 1;
   }

   private static int surfaceHalfY(BlockPos pos, BlockState state) {
      Block b = state.getBlock();
      return b instanceof MillPathSlabBlock ? 2 * pos.getY() + 1 : 2 * (pos.getY() + 1);
   }

   private static boolean isSystemPath(BlockState s) {
      if (s.getBlock() instanceof MillPathBlock mp) {
         return !(Boolean)s.getValue(MillPathBlock.STABLE);
      } else {
         return s.getBlock() instanceof MillPathSlabBlock ms ? !(Boolean)s.getValue(MillPathSlabBlock.STABLE) : false;
      }
   }

   private static PathAuditCommand.AuditReport scan(ServerLevel level, BlockPos center, int radius) {
      PathAuditCommand.AuditReport r = new PathAuditCommand.AuditReport();
      Map<Long, PathAuditCommand.PathCell> cells = new HashMap<>();
      MutableBlockPos m = new MutableBlockPos();
      int cx = center.getX();
      int cz = center.getZ();

      for (int x = cx - radius; x <= cx + radius; x++) {
         for (int z = cz - radius; z <= cz + radius; z++) {
            int terrainY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;

            for (int y = terrainY - 3; y <= terrainY + 4; y++) {
               m.set(x, y, z);
               BlockState s = level.getBlockState(m);
               if (isSystemPath(s)) {
                  r.total++;
                  boolean isSlab = s.getBlock() instanceof MillPathSlabBlock;
                  if (isSlab) {
                     r.slab++;
                  } else {
                     r.full++;
                  }

                  int halfY = surfaceHalfY(m, s);
                  cells.put(packXZ(x, z), new PathAuditCommand.PathCell(y, halfY, m.immutable()));
                  break;
               }
            }
         }
      }

      for (Entry<Long, PathAuditCommand.PathCell> e : cells.entrySet()) {
         long k = e.getKey();
         int x = unpackX(k);
         int z = unpackZ(k);
         PathAuditCommand.PathCell me = e.getValue();
         int peakMatches = 0;
         int peakNeighbors = 0;
         int valleyMatches = 0;
         int valleyNeighbors = 0;

         for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            PathAuditCommand.PathCell nb = cells.get(packXZ(x + d[0], z + d[1]));
            if (nb != null) {
               if (x + d[0] < x || x + d[0] == x && z + d[1] < z) {
                  int dy = nb.halfY - me.halfY;
                  int abs = Math.abs(dy);
                  r.edgesTotal++;
                  r.sumAbsDyHalf += abs;
                  if (abs > r.maxAbsDyHalf) {
                     r.maxAbsDyHalf = abs;
                  }

                  if (abs == 0) {
                     r.edgesFlat++;
                  } else if (abs == 1) {
                     r.edgesHalf++;
                  } else if (abs == 2) {
                     r.edgesFullStep++;
                  } else {
                     r.edgesBigStep++;
                  }
               }

               peakNeighbors++;
               valleyNeighbors++;
               if (nb.halfY < me.halfY) {
                  peakMatches++;
               }

               if (nb.halfY > me.halfY) {
                  valleyMatches++;
               }
            }
         }

         if (peakNeighbors >= 2 && peakMatches == peakNeighbors) {
            r.isolatedPeaks++;
            if (r.peakSamples.size() < 16) {
               r.peakSamples.add(me.pos);
            }
         }

         if (valleyNeighbors >= 2 && valleyMatches == valleyNeighbors) {
            r.isolatedValleys++;
            if (r.valleySamples.size() < 16) {
               r.valleySamples.add(me.pos);
            }
         }
      }

      return r;
   }

   private static long packXZ(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }

   private static int unpackX(long k) {
      return (int)(k >> 32);
   }

   private static int unpackZ(long k) {
      return (int)k;
   }

   private static final class AuditReport {
      int total;
      int full;
      int slab;
      int edgesTotal;
      int edgesFlat;
      int edgesHalf;
      int edgesFullStep;
      int edgesBigStep;
      int maxAbsDyHalf;
      long sumAbsDyHalf;
      int isolatedPeaks;
      int isolatedValleys;
      final List<BlockPos> peakSamples = new ArrayList<>();
      final List<BlockPos> valleySamples = new ArrayList<>();

      double roughness() {
         return this.edgesTotal == 0 ? 0.0 : (double)this.sumAbsDyHalf / this.edgesTotal / 2.0;
      }
   }

   private record PathCell(int y, int halfY, BlockPos pos) {
   }
}
