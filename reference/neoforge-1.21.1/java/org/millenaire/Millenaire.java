package org.millenaire;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.advancement.UsageReporter;
import org.millenaire.block.BlockSilkWorm;
import org.millenaire.block.BlockSnailSoil;
import org.millenaire.block.BlockWetBrick;
import org.millenaire.block.ModBlockEntities;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingInstance;
import org.millenaire.command.ConvertAddonCommand;
import org.millenaire.command.DevCommand;
import org.millenaire.command.QueryCommand;
import org.millenaire.command.ReputationCommand;
import org.millenaire.command.SpawnLoneBuildingCommand;
import org.millenaire.command.SpawnVillageCommand;
import org.millenaire.command.TestCommand;
import org.millenaire.command.TpCommand;
import org.millenaire.command.VillagesCommand;
import org.millenaire.commerce.ModMenuTypes;
import org.millenaire.config.MillenaireClientConfig;
import org.millenaire.config.MillenaireCommonConfig;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.config.VillagerConfig;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.ContentStatsReporter;
import org.millenaire.content.NativeContentDeployer;
import org.millenaire.content.ReferenceCatalogGenerator;
import org.millenaire.content.ValidationReport;
import org.millenaire.content.legacy.LegacyAutoConverter;
import org.millenaire.culture.CultureLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.diagnostics.WaypointVisualizationManager;
import org.millenaire.entity.BuilderSafetyHandler;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.goal.gathering.GatheringHandlerRegistry;
import org.millenaire.goal.gathering.GatheringTypeLoader;
import org.millenaire.goal.impl.BringBackHomeGoal;
import org.millenaire.goal.impl.BuildGoal;
import org.millenaire.goal.impl.BuildPathGoal;
import org.millenaire.goal.impl.ChatGoal;
import org.millenaire.goal.impl.ChildBecomeAdultGoal;
import org.millenaire.goal.impl.ClearOldPathGoal;
import org.millenaire.goal.impl.DeliverResourcesToShopGoal;
import org.millenaire.goal.impl.ForeignMerchantKeepStallGoal;
import org.millenaire.goal.impl.GatherGoodsGoal;
import org.millenaire.goal.impl.GetGoodsForHouseholdGoal;
import org.millenaire.goal.impl.GetResourcesForShopsGoal;
import org.millenaire.goal.impl.GetToolGoal;
import org.millenaire.goal.impl.IdleGoal;
import org.millenaire.goal.impl.LightHearthGoal;
import org.millenaire.goal.impl.MerchantVisitBuildingGoal;
import org.millenaire.goal.impl.MerchantVisitInnGoal;
import org.millenaire.goal.impl.RestGoal;
import org.millenaire.goal.impl.SellerGoal;
import org.millenaire.goal.impl.SocialiseGoal;
import org.millenaire.goal.impl.SpotGatheringGoal;
import org.millenaire.goal.visit.VisitGoalLoader;
import org.millenaire.goal.visit.VisitGoalValidator;
import org.millenaire.item.InuitHuntingDropHandler;
import org.millenaire.item.ModCreativeTabs;
import org.millenaire.item.ModItems;
import org.millenaire.language.I18nKeyAuditor;
import org.millenaire.language.ServerTranslationCache;
import org.millenaire.map.VillageMapDecorationTypes;
import org.millenaire.map.VillageMapMarkerService;
import org.millenaire.network.ModPayloads;
import org.millenaire.network.QuestNetworkHelper;
import org.millenaire.quest.QuestInstance;
import org.millenaire.quest.QuestLoader;
import org.millenaire.quest.QuestManager;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.tag.ModTags;
import org.millenaire.test.TestPlayerManager;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.TravelBookNavigationState;
import org.millenaire.village.VillageChunkLoader;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.BiomeTagDisjointnessValidator;
import org.millenaire.world.ModProcessors;
import org.millenaire.world.VillageSpawnQueue;
import org.slf4j.Logger;

@Mod("millenaire")
public class Millenaire {
   public static final String MODID = "millenaire";
   private static final Logger LOGGER = LogUtils.getLogger();
   private static GoalRegistry goalRegistryInstance;
   private static ModContainer modContainerInstance;
   private static VillageSpawnQueue spawnQueueInstance;

   public static GoalRegistry getGoalRegistry() {
      return goalRegistryInstance;
   }

   public static ModContainer getModContainer() {
      return modContainerInstance;
   }

   public static String getModVersion() {
      return modContainerInstance.getModInfo().getVersion().toString();
   }

   @Nullable
   public static VillageSpawnQueue getSpawnQueue() {
      return spawnQueueInstance;
   }

   public Millenaire(IEventBus modEventBus, ModContainer modContainer) {
      modContainerInstance = modContainer;
      LOGGER.info("Millénaire {} — Initialisation", modContainer.getModInfo().getVersion());
      modContainer.registerConfig(Type.SERVER, MillenaireServerConfig.SPEC);
      modContainer.registerConfig(Type.COMMON, MillenaireCommonConfig.SPEC);
      modContainer.registerConfig(Type.CLIENT, MillenaireClientConfig.SPEC);
      modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent event) -> {
         if (event.getConfig().getType() == Type.COMMON) {
            MillenaireCommonConfig.COMMON.bindLogCategories();
         }
      });
      modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent event) -> {
         if (event.getConfig().getType() == Type.COMMON) {
            MillenaireCommonConfig.COMMON.bindLogCategories();
         }
      });
      ModBlocks.register(modEventBus);
      ModBlockEntities.register(modEventBus);
      ModEntities.register(modEventBus);
      ModItems.register(modEventBus);
      ModCreativeTabs.register(modEventBus);
      ModMenuTypes.MENU_TYPES.register(modEventBus);
      ModProcessors.register(modEventBus);
      MillAdvancements.register(modEventBus);
      VillageMapDecorationTypes.register(modEventBus);
      modEventBus.addListener(ModPayloads::register);
      modEventBus.addListener(
         RegisterCapabilitiesEvent.class,
         event -> event.registerBlockEntity(ItemHandler.BLOCK, (BlockEntityType)ModBlockEntities.LOCKED_CHEST.get(), (blockEntity, side) -> null)
      );
      modEventBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(VillageChunkLoader.getController()));
      GoalRegistry goalRegistry = new GoalRegistry();
      goalRegistry.register(new IdleGoal());
      goalRegistry.register(new RestGoal());
      goalRegistry.register(new BuildGoal());
      goalRegistry.register(new SocialiseGoal());
      goalRegistry.register(new ChatGoal());
      goalRegistry.register(new SellerGoal());
      goalRegistry.register(new ChildBecomeAdultGoal());
      goalRegistry.register(new BringBackHomeGoal());
      goalRegistry.register(new DeliverResourcesToShopGoal());
      goalRegistry.register(new GetResourcesForShopsGoal());
      goalRegistry.register(new GetGoodsForHouseholdGoal());
      goalRegistry.register(new GetToolGoal());
      goalRegistry.register(new GatherGoodsGoal());
      goalRegistry.register(new LightHearthGoal());
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "gather_silk"),
            "silkwormfarm",
            2,
            100,
            2,
            SpotGatheringGoal.StockSource.FARM,
            () -> (Item)ModItems.SILK.get(),
            128,
            BuildingInstance::getSilkwormBlockPositions,
            (level, pos) -> {
               BlockState st = level.getBlockState(pos);
               return st.is((Block)ModBlocks.SILK_WORM.get()) && (Integer)st.getValue(BlockSilkWorm.AGE) == 3;
            },
            (ctx, pos) -> {
               BlockState st = ctx.level().getBlockState(pos);
               if (st.is((Block)ModBlocks.SILK_WORM.get()) && (Integer)st.getValue(BlockSilkWorm.AGE) == 3) {
                  ctx.level().setBlock(pos, (BlockState)st.setValue(BlockSilkWorm.AGE, 0), 2);
                  ctx.villager().getInventory().add((Item)ModItems.SILK.get(), 1);
               }
            },
            false
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "gather_snails"),
            "snailsfarm",
            2,
            100,
            2,
            SpotGatheringGoal.StockSource.FARM,
            () -> Items.PURPLE_DYE,
            128,
            BuildingInstance::getSnailSoilBlockPositions,
            (level, pos) -> {
               BlockState st = level.getBlockState(pos);
               return st.is((Block)ModBlocks.SNAIL_SOIL.get()) && (Integer)st.getValue(BlockSnailSoil.AGE) == 3;
            },
            (ctx, pos) -> {
               BlockState st = ctx.level().getBlockState(pos);
               if (st.is((Block)ModBlocks.SNAIL_SOIL.get()) && (Integer)st.getValue(BlockSnailSoil.AGE) == 3) {
                  ctx.level().setBlock(pos, (BlockState)st.setValue(BlockSnailSoil.AGE, 0), 2);
                  ctx.villager().getInventory().add(Items.PURPLE_DYE, 1);
               }
            },
            false
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "gather_brick"),
            "brickkiln",
            1,
            100,
            2,
            SpotGatheringGoal.StockSource.TOWNHALL,
            () -> ((Block)ModBlocks.MUD_BRICK.get()).asItem(),
            4096,
            BuildingInstance::getBrickSpotPositions,
            (level, pos) -> level.getBlockState(pos).is((Block)ModBlocks.MUD_BRICK.get()),
            (ctx, pos) -> {
               if (ctx.level().getBlockState(pos).is((Block)ModBlocks.MUD_BRICK.get())) {
                  ctx.villager().getInventory().add(((Block)ModBlocks.MUD_BRICK.get()).asItem(), 1);
                  ctx.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
               }
            },
            true
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "dry_brick"),
            "brickkiln",
            1,
            120,
            0,
            SpotGatheringGoal.StockSource.NONE,
            null,
            0,
            BuildingInstance::getBrickSpotPositions,
            (level, pos) -> level.getBlockState(pos).isAir(),
            (ctx, pos) -> {
               if (ctx.level().getBlockState(pos).isAir()) {
                  ctx.level().setBlock(pos, ((BlockWetBrick)ModBlocks.WET_BRICK.get()).defaultBlockState(), 3);
               }
            },
            true
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "harvest_sugar_cane"),
            "sugarplantation",
            3,
            200,
            4,
            SpotGatheringGoal.StockSource.TOWNHALL,
            () -> Items.SUGAR_CANE,
            0,
            b -> b.getSoilPositions("sugarcane"),
            (level, pos) -> level.getBlockState(pos.above(2)).is(Blocks.SUGAR_CANE),
            (ctx, pos) -> {
               float irrigation = 0.0F;
               BlockPos top = pos.above(3);
               if (ctx.level().getBlockState(top).is(Blocks.SUGAR_CANE)) {
                  ctx.level().setBlock(top, Blocks.AIR.defaultBlockState(), 3);
                  int nbCrop = 1;
                  if (Math.random() < irrigation / 100.0) {
                     nbCrop++;
                  }

                  ctx.villager().getInventory().add(Items.SUGAR_CANE, nbCrop);
               }

               BlockPos mid = pos.above(2);
               if (ctx.level().getBlockState(mid).is(Blocks.SUGAR_CANE)) {
                  ctx.level().setBlock(mid, Blocks.AIR.defaultBlockState(), 3);
                  int nbCrop = 1;
                  if (Math.random() < irrigation / 100.0) {
                     nbCrop++;
                  }

                  ctx.villager().getInventory().add(Items.SUGAR_CANE, nbCrop);
               }
            },
            true
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "plant_sugar_cane"),
            "sugarplantation",
            3,
            120,
            0,
            SpotGatheringGoal.StockSource.NONE,
            null,
            0,
            b -> b.getSoilPositions("sugarcane"),
            (level, pos) -> level.getBlockState(pos.above()).isAir(),
            (ctx, pos) -> {
               BlockPos cropPos = pos.above();
               BlockState state = ctx.level().getBlockState(cropPos);
               if (state.isAir() || state.is(BlockTags.LEAVES)) {
                  ctx.level().setBlock(cropPos, Blocks.SUGAR_CANE.defaultBlockState(), 3);
               }
            },
            true
         )
      );
      goalRegistry.register(new ClearOldPathGoal());
      goalRegistry.register(new BuildPathGoal());
      VisitGoalLoader.loadAll(goalRegistry);
      goalRegistry.register(new ForeignMerchantKeepStallGoal());
      goalRegistry.register(new MerchantVisitInnGoal());
      goalRegistry.register(new MerchantVisitBuildingGoal());
      GatheringHandlerRegistry.registerDefaults();
      GatheringTypeLoader.loadAll(goalRegistry);
      goalRegistryInstance = goalRegistry;
      NeoForge.EVENT_BUS.addListener(Pre.class, event -> {
         if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getEntity() instanceof ServerPlayer) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.is(ModTags.Items.CULTURE_WEAPONS)) {
               MillAdvancements.grant(attacker, MillAdvancements.MP_WEAPON);
            }
         }
      });
      NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, InuitHuntingDropHandler::onLivingDrops);
      NeoForge.EVENT_BUS.addListener(Pre.class, BuilderSafetyHandler::onLivingDamage);
      NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterCommandsEvent.class, event -> {
         SpawnVillageCommand.register(event.getDispatcher());
         SpawnLoneBuildingCommand.register(event.getDispatcher());
         ReputationCommand.register(event.getDispatcher());
         VillagesCommand.register(event.getDispatcher());
         QueryCommand.register(event.getDispatcher());
         TestCommand.register(event.getDispatcher());
         DevCommand.register(event.getDispatcher());
         TpCommand.register(event.getDispatcher());
      });
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
         TestPlayerManager.onServerStopping();
         TravelBookNavigationState.clearAll();
         ConvertAddonCommand.onServerStopping(event);
      });
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
         if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            TravelBookNavigationState.clear(serverPlayer.getUUID());
         }
      });
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
         if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ContentStatsReporter.notifyOpIfRelevant(serverPlayer);
            ServerLevel overworld = serverPlayer.server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
               PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);

               for (QuestInstance qi : data.getActiveQuests(serverPlayer.getUUID())) {
                  PacketDistributor.sendToPlayer(serverPlayer, QuestNetworkHelper.buildSyncPayload(qi, serverPlayer), new CustomPacketPayload[0]);
               }
            }
         }
      });
      VillageSpawnQueue spawnQueue = new VillageSpawnQueue();
      spawnQueueInstance = spawnQueue;
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) -> {
         UsageReporter.tryReport(event.getServer());
         TestPlayerManager.acknowledgeChunkBatch();
         ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
         if (overworld != null) {
            VillageSavedData.get(overworld).getVillageManager().tick(overworld);
            spawnQueue.trySpawnNext(overworld);
         }

         for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel playerLevel = player.serverLevel();
            QuestManager.tickQuests(player, playerLevel);
            QuestManager.tickSpecialActions(player, playerLevel);
         }

         WaypointVisualizationManager.tick(event.getServer());
         if (overworld != null && overworld.getGameTime() % 20L == 0L) {
            VillageMapMarkerService.processAllPlayers(event.getServer());
         }
      });
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
         if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD) {
            spawnQueue.markResolved(event.getChunk().getPos());
            if (!event.isNewChunk()) {
               return;
            }

            BlockPos chunkCenter = event.getChunk().getPos().getMiddleBlockPosition(64);
            Holder<Biome> centerBiome = serverLevel.getBiome(chunkCenter);
            if (!VillageSpawnQueue.couldHostAnyVillage(ModCultures.getAllVillageTypes().values(), tag -> centerBiome.is(tag))) {
               return;
            }

            spawnQueue.enqueue(chunkCenter);
         }
      });
      NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartedEvent.class, event -> {
         ContentDirectoryManager.init(event.getServer());
         NativeContentDeployer.deployIfNeeded();
         LegacyAutoConverter.convertIfNeeded();
         ToolCategoryRegistry.load();
         ServerTranslationCache.load();
         VillagerConfig.load();
         CultureLoader.loadAll();
         GatheringTypeLoader.loadExternal(goalRegistryInstance);
         VisitGoalLoader.loadExternalPerCultureGoals(goalRegistryInstance, CultureLoader.discoverCultures());
         CultureLoader.validateAll(goalRegistryInstance);
         GatheringTypeLoader.validateAll(goalRegistryInstance);
         VisitGoalValidator.validateAll(goalRegistryInstance);
         QuestRegistry.clear();
         QuestLoader.loadAll();
         ValidationReport.generate(CultureLoader.discoverCultures(), new HashSet<>(CultureLoader.BUILTIN_CULTURES));
         ReferenceCatalogGenerator.generate(goalRegistryInstance);
         I18nKeyAuditor.audit();
         ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
         if (overworld != null) {
            BiomeTagDisjointnessValidator.validate(overworld.registryAccess());
            CultureLoader.rehydrateConstructionTasks(overworld);
            this.initVillagerGoals(overworld);
         }
      });
      NeoForge.EVENT_BUS
         .addListener(
            EventPriority.NORMAL,
            false,
            EntityJoinLevelEvent.class,
            event -> {
               if (event.getEntity() instanceof MillVillager villager
                  && !event.getLevel().isClientSide()
                  && villager.getGoalScheduler() == null
                  && villager.getVillagerTypeId() != null) {
                  VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
                  if (vType != null && goalRegistryInstance != null) {
                     villager.initGoals(goalRegistryInstance, vType);
                     LOGGER.debug("Goals reinitialized for villager {} (loaded from chunk)", villager.getVillagerTypeId());
                  }
               }
            }
         );
   }

   private void initVillagerGoals(ServerLevel level) {
      int count = 0;

      for (Entity entity : level.getAllEntities()) {
         if (entity instanceof MillVillager villager && villager.getVillagerTypeId() != null) {
            VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
            if (vType != null) {
               villager.initGoals(goalRegistryInstance, vType);
               villager.syncChiefFlag(vType);
               if (villager.getTexture() == null) {
                  VillagerAppearanceFactory.randomizeAppearance(villager, vType);
               }

               count++;
            }
         }
      }

      if (count > 0) {
         LOGGER.info("Goals initialized for {} existing villagers", count);
      }
   }
}
