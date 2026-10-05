package org.millenaire.fabric.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.fabric.content.BuildingPlacement;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Construction sites: a paid project is built block by block by the village builders, bottom layer first,
 * attachments (torches, doors) after the walls. Blocks holding contents (chests, panels, spawners) and the
 * starting stock are placed when the site is finished. Sites live in memory; an abandoned site is finished by
 * the growth tick, so a village never keeps half a house.
 */
public final class VillageConstruction {
    /** A site idle this long (no builder working) is finished directly. */
    static final long ABANDONED = 24000L * 2;

    public static final class Site {
        private final VillageGrowth.Project project;
        private final BuildingPlacement.Prepared prepared;
        private final List<BuildingPlacement.Change> order;
        private int next;
        private long lastWork;

        Site(VillageGrowth.Project project, BuildingPlacement.Prepared prepared, long now) {
            this.project = project;
            this.prepared = prepared;
            List<BuildingPlacement.Change> simple = new ArrayList<>();
            for (var change : prepared.changes()) if (!change.state().hasBlockEntity()) simple.add(change);
            simple.sort(Comparator.comparing((BuildingPlacement.Change c) -> c.secondPass()).thenComparingInt(c -> c.pos().getY()));
            this.order = List.copyOf(simple);
            this.lastWork = now;
        }

        public VillageGrowth.Project project() { return project; }
        public BuildingPlacement.Prepared prepared() { return prepared; }
        public boolean done() { return next >= order.size(); }
        public int progress() { return order.isEmpty() ? 100 : next * 100 / order.size(); }
        public long lastWork() { return lastWork; }

        /** Where the builder should stand next: the next block to place, or the site origin. */
        public BlockPos workPos() { return done() ? project.site() : order.get(next).pos(); }
    }

    private static final Map<String, Site> SITES = new ConcurrentHashMap<>();

    private VillageConstruction() {}

    public static Optional<Site> site(String settlementKey) { return Optional.ofNullable(SITES.get(settlementKey)); }

    static Site open(VillageGrowth.Project project, BuildingPlacement.Prepared prepared, long now) {
        return SITES.computeIfAbsent(project.settlementKey(), key -> new Site(project, prepared, now));
    }

    static void close(String settlementKey) { SITES.remove(settlementKey); }

    /** Closes exactly this site; false when another builder already finished it. */
    static boolean close(Site site) { return SITES.remove(site.project().settlementKey(), site); }

    /** Places up to {@code blocks} changed blocks; unchanged cells are skipped for free. Returns how many were placed. */
    public static int work(ServerLevel level, Site site, int blocks) {
        var world = BuildingPlacement.world(level);
        int placed = 0;
        while (!site.done() && placed < blocks) {
            var change = site.order.get(site.next++);
            if (!level.isLoaded(change.pos()) || world.get(change.pos()).equals(change.state())) continue;
            if (world.set(change.pos(), change.state())) placed++;
        }
        site.lastWork = level.getGameTime();
        return placed;
    }

    /** Sites nobody worked on for two days. */
    static List<Site> abandoned(long now) {
        return SITES.values().stream().filter(site -> now - site.lastWork > ABANDONED).toList();
    }
}
