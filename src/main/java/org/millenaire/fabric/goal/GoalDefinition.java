package org.millenaire.fabric.goal;

import org.millenaire.fabric.content.LegacyDocument;
import org.millenaire.fabric.content.LegacyNumbers;

import java.util.*;

/**
 * One data-driven villager goal from {@code goals/<kind>/<name>.txt}. Durations and delays are kept in
 * the legacy milliseconds; {@link #durationTicks()} converts them for the server tick loop.
 */
public record GoalDefinition(String key, Kind kind, int priority, int priorityRandom, int durationMillis, int reoccurDelayMillis,
                             List<String> buildingTags, List<String> requiredTags, boolean townhallGoal, boolean leisure,
                             int range, int maxInBuilding, int maxTotal, int minimumTime, int maximumTime,
                             List<String> heldItems, Map<String, Integer> inputs, Map<String, Integer> outputs,
                             Map<String, Integer> buildingLimits, Map<String, Integer> townhallLimits, Map<String, Integer> villageLimits,
                             List<Roll> harvestRolls, Map<String, Integer> loot, Map<String, Integer> collectGoods, String cropType, String blockState, String resultingBlockState,
                             String itemToCook, int minimum, String animal, List<String> targetGoals, LegacyDocument source) {
    /** One {@code harvestitem}/{@code bonusitem} row: a single item with the given percent chance. */
    public record Roll(String good, int percent) {
        public Roll { if (percent < 0 || percent > 100) throw new IllegalArgumentException("chance must be 0..100: " + good); }
    }

    public enum Kind {
        COOKING("genericcooking"), CRAFTING("genericcrafting"), GATHER_BLOCKS("genericgatherblocks"),
        HARVESTING("genericharvesting"), MINING("genericmining"), PLANTING("genericplanting"),
        PLANT_SAPLING("genericplantsapling"), SLAUGHTER_ANIMAL("genericslaughteranimal"),
        TAKE_FROM_BUILDING("generictakefrombuilding"), TEND_FURNACE("generictendfurnace"), VISIT("genericvisit");

        private final String folder;
        Kind(String folder) { this.folder = folder; }
        public String folder() { return folder; }
        public static Optional<Kind> byFolder(String folder) {
            return Arrays.stream(values()).filter(kind -> kind.folder.equalsIgnoreCase(folder)).findFirst();
        }
    }

    public GoalDefinition {
        buildingTags = List.copyOf(buildingTags); requiredTags = List.copyOf(requiredTags); heldItems = List.copyOf(heldItems);
        inputs = map(inputs); outputs = map(outputs); buildingLimits = map(buildingLimits); townhallLimits = map(townhallLimits);
        villageLimits = map(villageLimits); harvestRolls = List.copyOf(harvestRolls); loot = map(loot); collectGoods = map(collectGoods);
        targetGoals = List.copyOf(targetGoals);
        if (durationMillis < 0 || reoccurDelayMillis < 0 || range < 0) throw new IllegalArgumentException("negative duration, delay or range");
        if (minimumTime < 0 || minimumTime > DAY || maximumTime < 0 || maximumTime > DAY) throw new IllegalArgumentException("time of day out of range");
    }

    private static Map<String, Integer> map(Map<String, Integer> source) { return Collections.unmodifiableMap(new LinkedHashMap<>(source)); }

    /** Legacy goals measured time in milliseconds of a 20 tick/s server. */
    public int durationTicks() { return Math.max(1, durationMillis / 50); }
    public int reoccurDelayTicks() { return reoccurDelayMillis / 50; }

    public static final int DAY = 24000;

    /** {@code minimumhour}/{@code maximumhour} are day-time ticks (0..24000) despite their names. */
    public boolean activeAt(long dayTime) {
        int time = (int) Math.floorMod(dayTime, (long) DAY);
        if (minimumTime == 0 && maximumTime == DAY) return true;
        return minimumTime <= maximumTime ? time >= minimumTime && time < maximumTime : time >= minimumTime || time < maximumTime;
    }

    /** Every goods alias the goal reads or writes, for validation against itemlist.txt. */
    public Set<String> referencedGoods() {
        Set<String> goods = new TreeSet<>();
        goods.addAll(inputs.keySet()); goods.addAll(outputs.keySet()); goods.addAll(buildingLimits.keySet());
        goods.addAll(townhallLimits.keySet()); goods.addAll(villageLimits.keySet());
        harvestRolls.forEach(roll -> goods.add(roll.good())); goods.addAll(loot.keySet()); goods.addAll(collectGoods.keySet());
        goods.addAll(heldItems);
        if (!itemToCook.isEmpty()) goods.add(itemToCook);
        return goods;
    }

    public static GoalDefinition parse(String key, Kind kind, LegacyDocument document) {
        Map<String, Integer> inputs = goods(document, "input"), outputs = goods(document, "output");
        List<Roll> rolls = new ArrayList<>();
        for (String field : List.of("harvestitem", "bonusitem"))
            for (String value : document.values(field)) {
                String[] parts = value.split(",", -1);
                if (parts.length != 2) throw new IllegalArgumentException(field + " expects good,percent: '" + value + "'");
                rolls.add(new Roll(parts[0].trim().toLowerCase(Locale.ROOT), LegacyNumbers.product(parts[1])));
            }
        List<String> held = new ArrayList<>();
        for (String field : List.of("helditems", "helditemsoffhand", "helditemsdestination", "helditemsoffhanddestination"))
            for (String value : document.values(field)) for (String part : value.split(",")) if (!part.isBlank()) held.add(part.trim().toLowerCase(Locale.ROOT));
        String itemToCook = document.first("itemtocook", "").trim().toLowerCase(Locale.ROOT);
        int minimum = integer(document, "minimumtocook", integer(document, "minimumfuel", integer(document, "minimumpickup", 0)));
        String blockState = first(document, "harvestblockstate", first(document, "plantblockstate",
                first(document, "sourceblockstate", first(document, "gatherblockstate", ""))));
        List<String> targets = new ArrayList<>();
        for (String value : document.values("targetvillagergoals")) for (String part : value.split(",")) if (!part.isBlank()) targets.add(part.trim().toLowerCase(Locale.ROOT));
        return new GoalDefinition(key, kind, integer(document, "priority", 0), integer(document, "priorityrandom", 0),
                integer(document, "duration", 0), integer(document, "reoccurdelay", 0),
                list(document, "buildingtag"), list(document, "requiredtag"), bool(document, "townhallgoal"), bool(document, "leasure"),
                integer(document, "range", 0), integer(document, "maxsimultaneousinbuilding", 0), integer(document, "maxsimultaneoustotal", 0),
                integer(document, "minimumhour", 0), integer(document, "maximumhour", DAY), held, inputs, outputs,
                goods(document, "buildinglimit"), goods(document, "townhalllimit"), goods(document, "villagelimit"), rolls,
                goods(document, "loot"), goods(document, "collect_good"),
                first(document, "croptype", ""), blockState, first(document, "resultingblockstate", ""), itemToCook, minimum,
                first(document, "animalkey", "").toLowerCase(Locale.ROOT), targets, document);
    }

    private static String first(LegacyDocument document, String key, String fallback) {
        String value = document.first(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }
    private static int integer(LegacyDocument document, String key, int fallback) {
        List<String> values = document.values(key);
        return values.isEmpty() || values.getLast().isBlank() ? fallback : LegacyNumbers.product(values.getLast());
    }
    private static boolean bool(LegacyDocument document, String key) {
        String value = document.first(key, "false").trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + " expects true or false, got '" + value + "'");
    }
    private static List<String> list(LegacyDocument document, String key) {
        List<String> result = new ArrayList<>();
        for (String value : document.values(key)) for (String part : value.split(",")) if (!part.isBlank()) result.add(part.trim().toLowerCase(Locale.ROOT));
        return result;
    }
    /** {@code key=good,count} rows; repeated goods are summed. */
    private static Map<String, Integer> goods(LegacyDocument document, String key) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String value : document.values(key)) {
            String[] parts = value.split(",", -1);
            if (parts.length != 2) throw new IllegalArgumentException(key + " expects good,count: '" + value + "'");
            int count = LegacyNumbers.product(parts[1]);
            if (count < 0) throw new IllegalArgumentException(key + " has a negative count");
            result.merge(parts[0].trim().toLowerCase(Locale.ROOT), count, Math::addExact);
        }
        return result;
    }
}
