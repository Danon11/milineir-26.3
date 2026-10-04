package org.millenaire.fabric.goal;

import java.util.*;
import java.util.random.RandomGenerator;

/** World-independent decisions of the data goals: when a goal may run and what it produces. */
public final class GoalRules {
    private GoalRules() {}

    /** Priority used to pick between eligible goals: base priority plus a random bonus. */
    public static int score(GoalDefinition goal, RandomGenerator random) {
        return goal.priority() + (goal.priorityRandom() > 0 ? random.nextInt(goal.priorityRandom() + 1) : 0);
    }

    /** True while no building limit would be exceeded by the goal's outputs or harvest. */
    public static boolean belowLimits(GoalDefinition goal, GoodsStore building, GoodsStore townhall) {
        for (var limit : goal.buildingLimits().entrySet()) if (building.count(limit.getKey()) >= limit.getValue()) return false;
        if (townhall != null)
            for (var limit : goal.townhallLimits().entrySet()) if (townhall.count(limit.getKey()) >= limit.getValue()) return false;
        return true;
    }

    /** A crafting goal can run when every input is in the building, outputs fit, and limits are not reached. */
    public static boolean canCraft(GoalDefinition goal, GoodsStore building, GoodsStore townhall) {
        if (goal.outputs().isEmpty()) return false;
        for (var input : goal.inputs().entrySet()) if (building.count(input.getKey()) < input.getValue()) return false;
        for (var output : goal.outputs().entrySet()) if (building.space(output.getKey()) < output.getValue()) return false;
        return belowLimits(goal, building, townhall);
    }

    /** Consumes inputs and stores outputs; returns false and changes nothing if the goal cannot run. */
    public static boolean craft(GoalDefinition goal, GoodsStore building, GoodsStore townhall) {
        if (!canCraft(goal, building, townhall)) return false;
        goal.inputs().forEach(building::remove);
        goal.outputs().forEach(building::add);
        return true;
    }

    /** The cooked good of a cooking goal: the first held or limited good that is not the raw input. */
    public static Optional<String> cookedGood(GoalDefinition goal) {
        List<String> candidates = new ArrayList<>(goal.heldItems());
        candidates.addAll(goal.buildingLimits().keySet());
        return candidates.stream().filter(good -> !good.equals(goal.itemToCook())).findFirst();
    }

    /** Cooking takes a batch of at least {@code minimumtocook} raw goods, up to a stack. */
    public static int cookBatch(GoalDefinition goal, GoodsStore building, GoodsStore townhall) {
        if (goal.itemToCook().isEmpty() || cookedGood(goal).isEmpty() || !belowLimits(goal, building, townhall)) return 0;
        int available = Math.min(building.count(goal.itemToCook()), 64);
        int batch = Math.min(available, building.space(cookedGood(goal).get()));
        return batch >= Math.max(1, goal.minimum()) ? batch : 0;
    }

    /** Rolls each harvest row once; rows with 0% never drop. */
    public static Map<String, Integer> rollHarvest(GoalDefinition goal, RandomGenerator random) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var roll : goal.harvestRolls())
            if (roll.percent() > 0 && random.nextInt(100) < roll.percent()) result.merge(roll.good(), 1, Integer::sum);
        return result;
    }

    /** Goods to move for a take-from-building goal; zero when below {@code minimumpickup}. */
    public static Map<String, Integer> pickup(GoalDefinition goal, GoodsStore source, GoodsStore home) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var wanted : goal.collectGoods().entrySet()) {
            int missing = wanted.getValue() - home.count(wanted.getKey());
            int amount = Math.min(Math.min(missing, source.count(wanted.getKey())), home.space(wanted.getKey()));
            if (amount > 0 && amount >= Math.max(1, goal.minimum())) result.put(wanted.getKey(), amount);
        }
        return result;
    }

    /** Soil service point for a crop type, following the legacy palette names. */
    public static String soilFor(String cropType) {
        String crop = cropType.toLowerCase(Locale.ROOT);
        return switch (crop) {
            case "wheat" -> "soil";
            case "potatoes" -> "potatosoil";
            case "carrots" -> "carrotsoil";
            case "flower" -> "flowersoil";
            case "millenaire:crop_maize" -> "maizesoil";
            case "millenaire:crop_rice" -> "ricesoil";
            case "millenaire:crop_turmeric" -> "turmericsoil";
            case "millenaire:crop_cotton" -> "cottonsoil";
            case "millenaire:crop_vine" -> "vinesoil";
            default -> crop.contains(":") ? crop.substring(crop.indexOf(':') + 1) + "soil" : crop + "soil";
        };
    }
}
