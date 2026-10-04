package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingInstance;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;
import org.millenaire.network.VillageScrollPurchasePayload;
import org.millenaire.village.panel.ConstructionPanelGenerator;
import org.millenaire.village.panel.MilitaryPanelGenerator;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.VillageOverviewPanelGenerator;
import org.slf4j.Logger;

public final class VillageBookService {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int SCROLL_PRICE = 128;
   public static final int SCROLL_REPUTATION = 8192;
   public static final int REGEN_INTERVAL = 1000;
   private static final String TAG_VILLAGE_ID = "village_id";
   private static final String TAG_VILLAGE_NAME = "village_name";
   private static final double MAX_PURCHASE_DISTANCE_SQ = 4096.0;

   private VillageBookService() {
   }

   public static ItemStack createScrollForVillage(Village village) {
      ItemStack stack = new ItemStack((ItemLike)ModItems.VILLAGE_SCROLL.get());
      CompoundTag tag = new CompoundTag();
      tag.putString("village_id", village.getId().uuid().toString());
      tag.putString("village_name", village.getVillageName());
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      return stack;
   }

   public static List<PanelContent> generateBookContent(Village village, @Nullable ServerLevel level, @Nullable ServerPlayer player) {
      ServerLevel effectiveLevel = level != null && isDegraded(village, level) ? null : level;
      BuildingInstance townhall = village.getTownhall();
      List<PanelContent> sections = new ArrayList<>(7);
      sections.add(VillageOverviewPanelGenerator.generateSummary(village, townhall, effectiveLevel));
      sections.add(VillageOverviewPanelGenerator.generatePopulation(village, effectiveLevel));
      sections.add(ConstructionPanelGenerator.generateConstructions(village, player));
      sections.add(ConstructionPanelGenerator.generateProjects(village, player));
      sections.add(ConstructionPanelGenerator.generateResources(village, effectiveLevel));
      sections.add(MilitaryPanelGenerator.generateMilitary(village, effectiveLevel));
      sections.add(VillageOverviewPanelGenerator.generateChronicle(village));
      return sections;
   }

   public static boolean isDegraded(Village village, ServerLevel level) {
      BuildingInstance townhall = village.getTownhall();
      return townhall == null ? true : !level.isLoaded(townhall.getOrigin());
   }

   public static void handleScrollPurchasePacket(VillageScrollPurchasePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            UUID villageUuid;
            try {
               villageUuid = UUID.fromString(payload.villageId());
            } catch (IllegalArgumentException e) {
               LOGGER.warn("Scroll purchase payload: invalid UUID '{}'", payload.villageId());
               return;
            }

            handleScrollPurchase(serverPlayer, villageUuid);
         }
      });
   }

   public static void handleScrollPurchase(ServerPlayer player, UUID villageUuid) {
      ServerLevel level = player.serverLevel();
      VillageSavedData savedData = VillageSavedData.get(level);
      Village village = savedData.getVillageManager().getVillage(new VillageId(villageUuid));
      if (village == null) {
         player.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
         LOGGER.warn("Scroll purchase failed: village {} not found", villageUuid);
      } else {
         double distSq = player.blockPosition().distSqr(village.getCenter());
         if (distSq > 4096.0) {
            LOGGER.warn("Scroll purchase denied: player {} too far from village {}", player.getName().getString(), villageUuid);
         } else {
            int rep = village.getCombinedReputation(level, player.getUUID());
            if (rep < 8192) {
               player.sendSystemMessage(Component.translatable("millenaire.scroll.error.reputation"));
            } else if (!MoneyHelper.removeDeniers(player.getInventory(), 128)) {
               player.sendSystemMessage(Component.translatable("millenaire.scroll.error.money"));
            } else {
               ItemStack scroll = createScrollForVillage(village);
               if (!player.getInventory().add(scroll)) {
                  player.drop(scroll, false);
               }

               player.sendSystemMessage(Component.translatable("millenaire.scroll.purchased", new Object[]{village.getVillageName()}));
               LOGGER.debug("Scroll purchased by {} for village {}", player.getName().getString(), village.getVillageName());
            }
         }
      }
   }
}
