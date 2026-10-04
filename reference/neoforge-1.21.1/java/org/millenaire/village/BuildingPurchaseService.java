package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.BuildingPurchasePayload;
import org.slf4j.Logger;

public final class BuildingPurchaseService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double MAX_PURCHASE_DISTANCE_SQ = 4096.0;

   private BuildingPurchaseService() {
   }

   public static void handlePurchasePacket(BuildingPurchasePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            UUID villageUuid;
            try {
               villageUuid = UUID.fromString(payload.villageId());
            } catch (IllegalArgumentException e) {
               LOGGER.warn("Building purchase payload: invalid UUID '{}'", payload.villageId());
               return;
            }

            ResourceLocation planSetId;
            try {
               planSetId = ResourceLocation.parse(payload.planSetId());
            } catch (Exception e) {
               LOGGER.warn("Building purchase payload: invalid planSetId '{}'", payload.planSetId());
               return;
            }

            handlePurchase(serverPlayer, villageUuid, planSetId);
         }
      });
   }

   private static void handlePurchase(ServerPlayer player, UUID villageUuid, ResourceLocation planSetId) {
      if (player.level() instanceof ServerLevel serverLevel) {
         VillageId var10 = new VillageId(villageUuid);
         Village village = Village.resolve(serverLevel, var10);
         if (village == null) {
            LOGGER.warn("Building purchase: village not found {}", villageUuid);
         } else if (player.blockPosition().distSqr(village.getCenter()) > 4096.0) {
            LOGGER.warn("Building purchase: player {} too far from village", player.getName().getString());
         } else {
            int playerReputation = village.getCombinedReputation(serverLevel, player.getUUID());
            int playerMoney = MoneyHelper.getTotalDeniers(player.getInventory());
            BuildingPurchaseService.PurchaseResult result = validatePurchase(village, planSetId, playerReputation, playerMoney);
            if (!result.isOk()) {
               LOGGER.warn("Building purchase refused for {}: {}", player.getName().getString(), result.errorKey());
               player.sendSystemMessage(Component.translatable(result.errorKey(), result.args()));
            } else {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
               if (planSet != null) {
                  if (!MoneyHelper.removeDeniers(player.getInventory(), planSet.price())) {
                     LOGGER.error(
                        "Failed to deduct {} deniers from {} despite pre-validation — aborting purchase of {}",
                        new Object[]{planSet.price(), player.getName().getString(), planSetId}
                     );
                     player.sendSystemMessage(Component.translatable("message.millenaire.purchase_failed"));
                  } else {
                     village.addBoughtBuilding(planSetId);
                     village.setPendingProject(null);
                     VillageGrowthManager.onBuildingCompleted(village);
                     player.sendSystemMessage(Component.translatable("message.millenaire.building_purchased", new Object[]{planSet.nativeName()}));
                     LOGGER.info("Player {} purchased {} in village {}", new Object[]{player.getName().getString(), planSetId, village.getVillageName()});
                  }
               }
            }
         }
      }
   }

   static BuildingPurchaseService.PurchaseResult validatePurchase(Village village, ResourceLocation planSetId, int playerReputation, int playerMoney) {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         return BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_plan_not_found");
      } else if (planSet.price() <= 0) {
         return BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_not_buyable");
      } else {
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType == null || !villageType.playerBuildings().contains(planSetId)) {
            return BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_not_available");
         } else if (village.isBuildingBought(planSetId)) {
            return BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_already_bought");
         } else {
            boolean alreadyBuilt = village.getBuildings().stream().anyMatch(b -> planSetId.equals(b.getPlanSetId()));
            if (alreadyBuilt) {
               return BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_already_built");
            } else if (playerReputation < planSet.reputation()) {
               return BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_no_rep", playerReputation, planSet.reputation());
            } else {
               return playerMoney < planSet.price()
                  ? BuildingPurchaseService.PurchaseResult.error("message.millenaire.purchase_no_money", playerMoney, planSet.price())
                  : BuildingPurchaseService.PurchaseResult.ok();
            }
         }
      }
   }

   record PurchaseResult(@Nullable String errorKey, Object... args) {
      static BuildingPurchaseService.PurchaseResult ok() {
         return new BuildingPurchaseService.PurchaseResult(null);
      }

      static BuildingPurchaseService.PurchaseResult error(String key, Object... args) {
         return new BuildingPurchaseService.PurchaseResult(key, args);
      }

      boolean isOk() {
         return this.errorKey == null;
      }
   }
}
