package org.millenaire.client;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.millenaire.Millenaire;
import org.millenaire.block.ModBlockEntities;
import org.millenaire.block.ModBlocks;
import org.millenaire.client.gui.FirePitScreen;
import org.millenaire.client.gui.LockedChestScreen;
import org.millenaire.client.gui.TradeScreen;
import org.millenaire.client.model.MillFemaleAsymModel;
import org.millenaire.client.model.MillFemaleSymModel;
import org.millenaire.client.model.MillMaleModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.client.render.LockedChestRenderer;
import org.millenaire.client.render.MillVillagerRenderer;
import org.millenaire.client.render.MillWallDecorationRenderer;
import org.millenaire.client.render.VillagePanelRenderer;
import org.millenaire.commerce.ModMenuTypes;
import org.millenaire.entity.ModEntities;
import org.millenaire.item.ModItems;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class ClientEvents {
   private ClientEvents() {
   }

   @SubscribeEvent
   public static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)ModEntities.MILL_VILLAGER.get(), MillVillagerRenderer::new);
      event.registerEntityRenderer((EntityType)ModEntities.WALL_DECORATION.get(), MillWallDecorationRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.LOCKED_CHEST.get(), LockedChestRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.VILLAGE_PANEL.get(), VillagePanelRenderer::new);
   }

   @SubscribeEvent
   public static void onRegisterLayerDefinitions(RegisterLayerDefinitions event) {
      CubeDeformation none = CubeDeformation.NONE;
      CubeDeformation cloth0 = new CubeDeformation(0.1F);
      CubeDeformation cloth1 = new CubeDeformation(0.2F);
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_MALE, () -> MillMaleModel.createBodyLayer(none));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_SYM, () -> MillFemaleSymModel.createBodyLayer(none));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM, () -> MillFemaleAsymModel.createBodyLayer(none));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_0, () -> MillMaleModel.createBodyLayer(cloth0));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_0, () -> MillFemaleSymModel.createBodyLayer(cloth0));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_0, () -> MillFemaleAsymModel.createBodyLayer(cloth0));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_1, () -> MillMaleModel.createBodyLayer(cloth1));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_1, () -> MillFemaleSymModel.createBodyLayer(cloth1));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_1, () -> MillFemaleAsymModel.createBodyLayer(cloth1));
   }

   @SubscribeEvent
   public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
      event.register(ModMenuTypes.TRADE.get(), TradeScreen::new);
      event.register(ModMenuTypes.LOCKED_CHEST.get(), LockedChestScreen::new);
      event.register(ModMenuTypes.FIRE_PIT.get(), FirePitScreen::new);
   }

   @SubscribeEvent
   public static void onRegisterBlockColors(Block event) {
      event.register(
         (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(),
         new net.minecraft.world.level.block.Block[]{
            (net.minecraft.world.level.block.Block)ModBlocks.PISTACHIO_TREE_LEAVES.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.APPLE_TREE_LEAVES.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.OLIVE_TREE_LEAVES.get()
         }
      );
   }

   @SubscribeEvent
   public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(new LanguageFallbackListener());
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      ModContainer container = Millenaire.getModContainer();
      container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)(mc, parent) -> new ConfigurationScreen(container, parent));
      event.enqueueWork(() -> {
         registerBowProperties((BowItem)ModItems.YUMIBOW.get());
         registerBowProperties((BowItem)ModItems.SELJUK_BOW.get());
         registerBowProperties((BowItem)ModItems.INUIT_BOW.get());
      });
   }

   private static void registerBowProperties(BowItem bow) {
      ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
         if (entity == null) {
            return 0.0F;
         } else {
            return entity.getUseItem() != stack ? 0.0F : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
         }
      });
      ItemProperties.register(
         bow,
         ResourceLocation.withDefaultNamespace("pulling"),
         (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
      );
   }
}
