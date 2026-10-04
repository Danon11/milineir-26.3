package org.millenaire.goal.gathering;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.millenaire.goal.gathering.handler.BreedingHandler;
import org.millenaire.goal.gathering.handler.ChoppingHandler;
import org.millenaire.goal.gathering.handler.CocoaHarvestingHandler;
import org.millenaire.goal.gathering.handler.CocoaPlantingHandler;
import org.millenaire.goal.gathering.handler.CraftingHandler;
import org.millenaire.goal.gathering.handler.FishingHandler;
import org.millenaire.goal.gathering.handler.FishingInuitHandler;
import org.millenaire.goal.gathering.handler.FlowerPlantingHandler;
import org.millenaire.goal.gathering.handler.FruitHarvestingHandler;
import org.millenaire.goal.gathering.handler.HarvestingHandler;
import org.millenaire.goal.gathering.handler.MiningHandler;
import org.millenaire.goal.gathering.handler.PaddyHarvestingHandler;
import org.millenaire.goal.gathering.handler.PaddyPlantingHandler;
import org.millenaire.goal.gathering.handler.PlantingHandler;
import org.millenaire.goal.gathering.handler.SaplingPlantingHandler;
import org.millenaire.goal.gathering.handler.ShearingHandler;
import org.millenaire.goal.gathering.handler.SlaughterHandler;
import org.millenaire.goal.gathering.handler.SmeltingHandler;
import org.millenaire.goal.gathering.handler.TakeFromBuildingHandler;

public final class GatheringHandlerRegistry {
   private static final Map<String, GatheringHandler> HANDLERS = new ConcurrentHashMap<>();

   private GatheringHandlerRegistry() {
   }

   public static void register(GatheringHandler handler) {
      HANDLERS.put(handler.id(), handler);
   }

   @Nullable
   public static GatheringHandler get(String id) {
      return HANDLERS.get(id);
   }

   public static List<String> getAllIds() {
      return HANDLERS.keySet().stream().sorted().toList();
   }

   public static void clear() {
      HANDLERS.values().forEach(GatheringHandler::onClear);
      HANDLERS.clear();
   }

   public static void registerDefaults() {
      register(new HarvestingHandler());
      register(new PlantingHandler());
      register(new ChoppingHandler());
      register(new SaplingPlantingHandler());
      register(new BreedingHandler());
      register(new MiningHandler());
      register(new SlaughterHandler());
      register(new CraftingHandler());
      register(new ShearingHandler());
      register(new FishingHandler());
      register(new FishingInuitHandler());
      register(new SmeltingHandler());
      register(new FlowerPlantingHandler());
      register(new FruitHarvestingHandler());
      register(new CocoaHarvestingHandler());
      register(new CocoaPlantingHandler());
      register(new PaddyPlantingHandler());
      register(new PaddyHarvestingHandler());
      register(new TakeFromBuildingHandler());
   }
}
