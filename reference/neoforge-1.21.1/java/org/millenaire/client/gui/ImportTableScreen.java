package org.millenaire.client.gui;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.network.ImportTableActionPayload;
import org.millenaire.network.ImportTableCostsPayload;
import org.millenaire.network.ImportTableSyncPayload;

public class ImportTableScreen extends AbstractMillenaireScreen {
   private static final int PANEL_WIDTH = 256;
   private static final int PANEL_HEIGHT = 220;
   private static final int PADDING = 10;
   private static final int LINE_HEIGHT = 14;
   private static final int BUTTON_HEIGHT = 20;
   private static final int MAX_LIST_ITEMS = 7;
   private static final int COLOR_WHITE = -13421773;
   private static final int COLOR_GRAY = -10066347;
   private static final int COLOR_YELLOW = -11193600;
   private static final int COLOR_GREEN = -13408717;
   private static final String[] ORIENTATION_NAMES = new String[]{"N", "E", "S", "W"};
   private final ImportTableSyncPayload syncData;
   private String buildingId;
   private String variant;
   private String cultureKey;
   private String parentBuildingId;
   private int length;
   private int plotWidth;
   private int upgradeLevel;
   private int startingLevel;
   private int plotHeight;
   private int orientation;
   private boolean clearGround;
   private boolean exportSnow;
   private boolean importMockBlocks;
   private boolean convertToPreserveGround;
   private boolean isMainTable;
   private EditBox lengthField;
   private EditBox plotWidthField;
   private EditBox startingLevelField;
   private EditBox plotHeightField;
   private ImportTableScreen.ScreenState currentState = ImportTableScreen.ScreenState.HOME;
   private final Deque<ImportTableScreen.ScreenState> previousScreens = new ArrayDeque<>();
   private String selectedCultureKey = "";
   private String selectedCategory = "";
   private String selectedBuildingId = "";
   private String selectedVariant = "a";
   private String selectedExportBuildingId = "";
   private String selectedExportVariant = "a";
   private List<String> cultureCategories = List.of();
   private List<BuildingPlanSet> categoryBuildings = List.of();
   private List<Integer> buildingLevels = List.of();
   private List<String> exportBuildings = List.of();
   private List<Integer> exportLevels = List.of();
   private BuildingPlanSet currentBuildingPlanSet;
   private List<String> availableExportVariants = List.of();
   private List<ImportTableCostsPayload.Entry> costs = null;
   private String costsLabel = "";

   public ImportTableScreen(ImportTableSyncPayload payload) {
      super(Component.translatable("gui.millenaire.importtable.title"));
      this.syncData = payload;
      this.buildingId = payload.buildingId();
      this.variant = payload.variant();
      this.cultureKey = payload.cultureKey();
      this.parentBuildingId = payload.parentBuildingId();
      this.length = payload.length();
      this.plotWidth = payload.width();
      this.upgradeLevel = payload.upgradeLevel();
      this.startingLevel = payload.startingLevel();
      this.plotHeight = payload.height();
      this.orientation = payload.orientation();
      this.clearGround = payload.clearGround();
      this.exportSnow = payload.exportSnow();
      this.importMockBlocks = payload.importMockBlocks();
      this.convertToPreserveGround = payload.convertToPreserveGround();
      this.isMainTable = payload.isMainTable();
   }

   private boolean hasPlan() {
      return this.buildingId != null && !this.buildingId.isEmpty();
   }

   protected void init() {
      super.init();
      this.rebuildScreen();
   }

   private void rebuildScreen() {
      this.clearWidgets();
      int panelX = (this.width - 256) / 2;
      int panelY = (this.height - 220) / 2;
      switch (this.currentState) {
         case HOME:
            this.buildHomeScreen(panelX, panelY);
            break;
         case NEW_BUILDING:
            this.buildNewBuildingScreen(panelX, panelY);
            break;
         case SETTINGS:
            this.buildSettingsScreen(panelX, panelY);
            break;
         case IMPORT_CULTURE:
            this.buildImportCultureScreen(panelX, panelY);
            break;
         case IMPORT_CULTURE_SUBDIR:
            this.buildImportCultureSubdirScreen(panelX, panelY);
            break;
         case IMPORT_CULTURE_BUILDING:
            this.buildImportCultureBuildingScreen(panelX, panelY);
            break;
         case IMPORT_EXPORT_DIR:
            this.buildImportExportDirScreen(panelX, panelY);
            break;
         case IMPORT_EXPORT_DIR_BUILDING:
            this.buildImportExportDirBuildingScreen(panelX, panelY);
            break;
         case COSTS:
            this.buildCostsScreen(panelX, panelY);
      }

      this.totalPages = this.computeTotalPages();
      if (this.currentState != ImportTableScreen.ScreenState.HOME) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.importtable.back"), btn -> this.navigateBack())
               .bounds(panelX + 10, panelY + 220 - 25, 50, 20)
               .build()
         );
      }

      if (this.totalPages > 1) {
         int midX = panelX + 128;
         this.prevButton = Button.builder(Component.literal("<"), btn -> {
            if (this.currentPage > 0) {
               this.currentPage--;
               this.rebuildScreen();
            }
         }).bounds(midX - 40, panelY + 220 - 25, 20, 20).build();
         this.prevButton.active = this.currentPage > 0;
         this.addRenderableWidget(this.prevButton);
         this.nextButton = Button.builder(Component.literal(">"), btn -> {
            if (this.currentPage < this.totalPages - 1) {
               this.currentPage++;
               this.rebuildScreen();
            }
         }).bounds(midX + 20, panelY + 220 - 25, 20, 20).build();
         this.nextButton.active = this.currentPage < this.totalPages - 1;
         this.addRenderableWidget(this.nextButton);
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), btn -> this.onClose()).bounds(panelX + 256 - 10 - 50, panelY + 220 - 25, 50, 20).build()
      );
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int panelX = (this.width - 256) / 2;
      int panelY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.QUEST_TEXTURE, panelX, panelY, 256, 220);

      Component title = switch (this.currentState) {
         case HOME -> this.hasPlan()
            ? Component.translatable("gui.millenaire.importtable.title_with_building", new Object[]{this.buildingId})
            : Component.translatable("gui.millenaire.importtable.title");
         case NEW_BUILDING -> Component.translatable("gui.millenaire.importtable.title_new_building");
         case SETTINGS -> Component.translatable("gui.millenaire.importtable.title_settings");
         case IMPORT_CULTURE -> Component.translatable("gui.millenaire.importtable.title_import_culture", new Object[]{this.selectedCultureKey});
         case IMPORT_CULTURE_SUBDIR -> Component.translatable("gui.millenaire.importtable.title_category", new Object[]{this.selectedCategory});
         case IMPORT_CULTURE_BUILDING -> Component.translatable("gui.millenaire.importtable.title_building", new Object[]{this.selectedBuildingId});
         case IMPORT_EXPORT_DIR -> Component.translatable("gui.millenaire.importtable.title_import_exports");
         case IMPORT_EXPORT_DIR_BUILDING -> Component.translatable("gui.millenaire.importtable.title_export", new Object[]{this.selectedExportBuildingId});
         case COSTS -> Component.translatable("gui.millenaire.importtable.title_costs", new Object[]{this.costsLabel});
      };
      graphics.drawCenteredString(this.font, title, panelX + 128, panelY + 6, -11193600);
      if (this.currentState == ImportTableScreen.ScreenState.HOME && this.hasPlan()) {
         this.renderPlanInfo(graphics, panelX + 10, panelY + 22);
      }

      if (this.currentState == ImportTableScreen.ScreenState.NEW_BUILDING) {
         this.renderNewBuildingLabels(graphics);
      }

      if (this.currentState == ImportTableScreen.ScreenState.SETTINGS) {
         this.renderSettingsLabels(graphics);
      }

      if (this.currentState == ImportTableScreen.ScreenState.IMPORT_EXPORT_DIR && this.exportBuildings.isEmpty()) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.millenaire.importtable.no_exports"), panelX + 128, panelY + 50, -10066347);
      }

      if (this.currentState == ImportTableScreen.ScreenState.COSTS) {
         this.renderCosts(graphics, panelX + 10, panelY + 22);
      }

      if (this.totalPages > 1) {
         String pageStr = this.currentPage + 1 + "/" + this.totalPages;
         graphics.drawCenteredString(this.font, pageStr, panelX + 128, panelY + 220 - 38, -10066347);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void buildHomeScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int buttonWidth = 236;
      if (this.hasPlan()) {
         y += 70;
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.millenaire.importtable.reimport"),
                  btn -> this.sendAction(ImportTableActionPayload.Action.REIMPORT, new CompoundTag())
               )
               .bounds(panelX + 10, y, buttonWidth / 2 - 2, 20)
               .build()
         );
         Button reimportAll = Button.builder(
               Component.translatable("gui.millenaire.importtable.reimport_all"),
               btn -> this.sendAction(ImportTableActionPayload.Action.REIMPORT_ALL, new CompoundTag())
            )
            .bounds(panelX + 10 + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, 20)
            .build();
         reimportAll.active = this.isMainTable || this.syncData.hasMainTablePos();
         this.addRenderableWidget(reimportAll);
         y += 24;
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.millenaire.importtable.export"),
                  btn -> this.sendAction(ImportTableActionPayload.Action.EXPORT, new CompoundTag())
               )
               .bounds(panelX + 10, y, buttonWidth / 2 - 2, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.millenaire.importtable.export_new_level"),
                  btn -> this.sendAction(ImportTableActionPayload.Action.EXPORT_NEW_LEVEL, new CompoundTag())
               )
               .bounds(panelX + 10 + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, 20)
               .build()
         );
         y += 24;
         this.addRenderableWidget(Button.builder(Component.translatable("gui.millenaire.importtable.change_plan"), btn -> {
            this.buildingId = "";
            this.rebuildScreen();
         }).bounds(panelX + 10, y, buttonWidth / 2 - 2, 20).build());
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.importtable.settings"), btn -> this.navigateTo(ImportTableScreen.ScreenState.SETTINGS))
               .bounds(panelX + 10 + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, 20)
               .build()
         );
         y += 24;
         this.addRenderableWidget(Button.builder(Component.translatable("gui.millenaire.importtable.costs"), btn -> {
            this.costs = null;
            this.costsLabel = this.buildingId + " " + this.variant + " level " + this.upgradeLevel;
            this.sendAction(ImportTableActionPayload.Action.SHOW_COSTS, new CompoundTag());
            this.navigateTo(ImportTableScreen.ScreenState.COSTS);
         }).bounds(panelX + 10, y, buttonWidth, 20).build());
      } else {
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.millenaire.importtable.new_building"), btn -> this.navigateTo(ImportTableScreen.ScreenState.NEW_BUILDING)
               )
               .bounds(panelX + 10, y, buttonWidth, 20)
               .build()
         );
         y += 24;
         this.addRenderableWidget(Button.builder(Component.translatable("gui.millenaire.importtable.import_from_exports"), btn -> {
            this.loadExportBuildings();
            this.navigateTo(ImportTableScreen.ScreenState.IMPORT_EXPORT_DIR);
         }).bounds(panelX + 10, y, buttonWidth, 20).build());
         y += 24;
         Map<ResourceLocation, Culture> cultures = ModCultures.getAllCultures();
         int halfWidth = (buttonWidth - 4) / 2;
         int col = 0;

         for (Entry<ResourceLocation, Culture> entry : cultures.entrySet()) {
            Culture culture = entry.getValue();
            String cKey = entry.getKey().toString();
            int btnX = panelX + 10 + col * (halfWidth + 4);
            this.addRenderableWidget(Button.builder(Component.literal(culture.displayName()), btn -> {
               this.selectedCultureKey = cKey;
               this.loadCultureCategories();
               this.navigateTo(ImportTableScreen.ScreenState.IMPORT_CULTURE);
            }).bounds(btnX, y, halfWidth, 20).build());
            if (++col >= 2) {
               col = 0;
               y += 22;
            }

            if (y > panelY + 220 - 55) {
               break;
            }
         }

         if (col > 0) {
            y += 22;
         }

         y += 4;
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.importtable.settings"), btn -> this.navigateTo(ImportTableScreen.ScreenState.SETTINGS))
               .bounds(panelX + 10, y, buttonWidth, 20)
               .build()
         );
      }
   }

   private void renderPlanInfo(GuiGraphics graphics, int x, int y) {
      Component cultureLabel = this.cultureKey.isEmpty()
         ? Component.translatable("gui.millenaire.importtable.info.culture_custom")
         : Component.literal(this.cultureKey);
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.info.culture", new Object[]{cultureLabel}), x, y, -10066347);
      y += 14;
      graphics.drawString(
         this.font, Component.translatable("gui.millenaire.importtable.info.building", new Object[]{this.buildingId, this.variant}), x, y, -13421773
      );
      y += 14;
      graphics.drawString(
         this.font,
         Component.translatable("gui.millenaire.importtable.info.level_size", new Object[]{this.upgradeLevel, this.plotWidth, this.length}),
         x,
         y,
         -13421773
      );
      y += 14;
      graphics.drawString(
         this.font,
         Component.translatable(
            "gui.millenaire.importtable.info.y_range_orientation",
            new Object[]{this.startingLevel, this.startingLevel + this.plotHeight - 1, ORIENTATION_NAMES[this.orientation % 4]}
         ),
         x,
         y,
         -13421773
      );
   }

   private void buildNewBuildingScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int valueX = panelX + 10 + 120;
      int editBoxW = 40;
      this.lengthField = new EditBox(this.font, valueX + 22, y, editBoxW, 20, Component.literal(""));
      this.lengthField.setValue(String.valueOf(this.length));
      this.lengthField.setMaxLength(4);
      this.lengthField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
      this.lengthField.setResponder(s -> {
         try {
            this.length = Math.max(1, Integer.parseInt(s));
         } catch (NumberFormatException var3x) {
         }
      });
      this.addRenderableWidget(this.lengthField);
      this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
         this.length = Math.max(1, this.length - 1);
         this.lengthField.setValue(String.valueOf(this.length));
      }).bounds(valueX, y, 20, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
         this.length++;
         this.lengthField.setValue(String.valueOf(this.length));
      }).bounds(valueX + 22 + editBoxW + 2, y, 20, 20).build());
      y += 22;
      this.plotWidthField = new EditBox(this.font, valueX + 22, y, editBoxW, 20, Component.literal(""));
      this.plotWidthField.setValue(String.valueOf(this.plotWidth));
      this.plotWidthField.setMaxLength(4);
      this.plotWidthField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
      this.plotWidthField.setResponder(s -> {
         try {
            this.plotWidth = Math.max(1, Integer.parseInt(s));
         } catch (NumberFormatException var3x) {
         }
      });
      this.addRenderableWidget(this.plotWidthField);
      this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
         this.plotWidth = Math.max(1, this.plotWidth - 1);
         this.plotWidthField.setValue(String.valueOf(this.plotWidth));
      }).bounds(valueX, y, 20, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
         this.plotWidth++;
         this.plotWidthField.setValue(String.valueOf(this.plotWidth));
      }).bounds(valueX + 22 + editBoxW + 2, y, 20, 20).build());
      y += 22;
      this.startingLevelField = new EditBox(this.font, valueX + 22, y, editBoxW, 20, Component.literal(""));
      this.startingLevelField.setValue(String.valueOf(this.startingLevel));
      this.startingLevelField.setMaxLength(4);
      this.startingLevelField.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
      this.startingLevelField.setResponder(s -> {
         try {
            this.startingLevel = Integer.parseInt(s);
         } catch (NumberFormatException var3x) {
         }
      });
      this.addRenderableWidget(this.startingLevelField);
      this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
         this.startingLevel--;
         this.startingLevelField.setValue(String.valueOf(this.startingLevel));
      }).bounds(valueX, y, 20, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
         this.startingLevel++;
         this.startingLevelField.setValue(String.valueOf(this.startingLevel));
      }).bounds(valueX + 22 + editBoxW + 2, y, 20, 20).build());
      y += 22;
      this.plotHeightField = new EditBox(this.font, valueX + 22, y, editBoxW, 20, Component.literal(""));
      this.plotHeightField.setValue(String.valueOf(this.plotHeight));
      this.plotHeightField.setMaxLength(4);
      this.plotHeightField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
      this.plotHeightField.setResponder(s -> {
         try {
            this.plotHeight = Math.max(1, Integer.parseInt(s));
         } catch (NumberFormatException var3x) {
         }
      });
      this.addRenderableWidget(this.plotHeightField);
      this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
         this.plotHeight = Math.max(1, this.plotHeight - 1);
         this.plotHeightField.setValue(String.valueOf(this.plotHeight));
      }).bounds(valueX, y, 20, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
         this.plotHeight++;
         this.plotHeightField.setValue(String.valueOf(this.plotHeight));
      }).bounds(valueX + 22 + editBoxW + 2, y, 20, 20).build());
      y += 22;
      this.addRenderableWidget(
         Button.builder(Component.translatable(this.clearGround ? "gui.millenaire.importtable.toggle.on" : "gui.millenaire.importtable.toggle.off"), btn -> {
            this.clearGround = !this.clearGround;
            this.rebuildScreen();
         }).bounds(valueX, y, 50, 20).build()
      );
      y += 28;
      this.addRenderableWidget(Button.builder(Component.translatable("gui.millenaire.importtable.create"), btn -> {
         CompoundTag data = new CompoundTag();
         data.putInt("length", this.length);
         data.putInt("width", this.plotWidth);
         data.putInt("startingLevel", this.startingLevel);
         data.putInt("height", this.plotHeight);
         data.putBoolean("clearGround", this.clearGround);
         this.sendAction(ImportTableActionPayload.Action.CREATE_NEW, data);
         this.onClose();
      }).bounds(panelX + 10, y, 236, 20).build());
   }

   private void renderNewBuildingLabels(GuiGraphics graphics) {
      int panelX = (this.width - 256) / 2;
      int panelY = (this.height - 220) / 2;
      int y = panelY + 22;
      int labelX = panelX + 10;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.length"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.width"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.starting_level"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.height"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.clear_ground"), labelX, y + 5, -13421773);
   }

   private void buildSettingsScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int valueX = panelX + 10 + 160;
      int btnW = 50;
      this.addRenderableWidget(Button.builder(Component.literal(ORIENTATION_NAMES[this.orientation % 4]), btn -> {
         this.orientation = (this.orientation + 1) % 4;
         this.sendSettingsUpdate();
         this.rebuildScreen();
      }).bounds(valueX, y, btnW, 20).build());
      y += 22;
      this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
         this.startingLevel--;
         this.plotHeight++;
         this.sendSettingsUpdate();
         this.rebuildScreen();
      }).bounds(valueX, y, 20, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
         this.startingLevel++;
         if (this.plotHeight > 1) {
            this.plotHeight--;
         }

         this.sendSettingsUpdate();
         this.rebuildScreen();
      }).bounds(valueX + 30, y, 20, 20).build());
      y += 22;
      this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
         this.plotHeight = Math.max(1, this.plotHeight - 1);
         this.sendSettingsUpdate();
         this.rebuildScreen();
      }).bounds(valueX, y, 20, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
         this.plotHeight++;
         this.sendSettingsUpdate();
         this.rebuildScreen();
      }).bounds(valueX + 30, y, 20, 20).build());
      y += 22;
      this.addRenderableWidget(
         Button.builder(Component.translatable(this.exportSnow ? "gui.millenaire.importtable.toggle.on" : "gui.millenaire.importtable.toggle.off"), btn -> {
            this.exportSnow = !this.exportSnow;
            this.sendSettingsUpdate();
            this.rebuildScreen();
         }).bounds(valueX, y, btnW, 20).build()
      );
      y += 22;
      this.addRenderableWidget(
         Button.builder(
               Component.translatable(this.importMockBlocks ? "gui.millenaire.importtable.toggle.on" : "gui.millenaire.importtable.toggle.off"), btn -> {
                  this.importMockBlocks = !this.importMockBlocks;
                  this.sendSettingsUpdate();
                  this.rebuildScreen();
               }
            )
            .bounds(valueX, y, btnW, 20)
            .build()
      );
      y += 22;
      this.addRenderableWidget(
         Button.builder(
               Component.translatable(this.convertToPreserveGround ? "gui.millenaire.importtable.toggle.on" : "gui.millenaire.importtable.toggle.off"),
               btn -> {
                  this.convertToPreserveGround = !this.convertToPreserveGround;
                  this.sendSettingsUpdate();
                  this.rebuildScreen();
               }
            )
            .bounds(valueX, y, btnW, 20)
            .build()
      );
   }

   private void renderSettingsLabels(GuiGraphics graphics) {
      int panelX = (this.width - 256) / 2;
      int panelY = (this.height - 220) / 2;
      int y = panelY + 22;
      int labelX = panelX + 10;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.orientation"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(
         this.font, Component.translatable("gui.millenaire.importtable.label.starting_level_value", new Object[]{this.startingLevel}), labelX, y + 5, -13421773
      );
      y += 22;
      graphics.drawString(
         this.font, Component.translatable("gui.millenaire.importtable.label.height_value", new Object[]{this.plotHeight}), labelX, y + 5, -13421773
      );
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.export_snow"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.import_mock_blocks"), labelX, y + 5, -13421773);
      y += 22;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.importtable.label.convert_preserve_ground"), labelX, y + 5, -13421773);
   }

   private void buildImportCultureScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int buttonWidth = 236;

      for (String category : this.pageSlice(this.cultureCategories)) {
         this.addRenderableWidget(Button.builder(Component.literal(category), btn -> {
            this.selectedCategory = category;
            this.loadCategoryBuildings();
            this.navigateTo(ImportTableScreen.ScreenState.IMPORT_CULTURE_SUBDIR);
         }).bounds(panelX + 10, y, buttonWidth, 20).build());
         y += 22;
      }
   }

   private void buildImportCultureSubdirScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int buttonWidth = 236;

      for (BuildingPlanSet planSet : this.pageSlice(this.categoryBuildings)) {
         String label = planSet.buildingId();
         this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
            this.selectedBuildingId = planSet.buildingId();
            this.currentBuildingPlanSet = planSet;
            if (!planSet.variants().isEmpty()) {
               this.selectedVariant = planSet.variants().keySet().iterator().next();
            }

            this.loadBuildingLevels(planSet);
            this.navigateTo(ImportTableScreen.ScreenState.IMPORT_CULTURE_BUILDING);
         }).bounds(panelX + 10, y, buttonWidth, 20).build());
         y += 22;
      }
   }

   private void buildImportCultureBuildingScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int buttonWidth = 236;
      if (this.currentBuildingPlanSet != null && this.currentBuildingPlanSet.variants().size() > 1) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.importtable.variant_hint", new Object[]{this.selectedVariant}), btn -> {
               List<String> variants = new ArrayList<>(this.currentBuildingPlanSet.variants().keySet());
               int idx = variants.indexOf(this.selectedVariant);
               this.selectedVariant = variants.get((idx + 1) % variants.size());
               this.loadBuildingLevels(this.currentBuildingPlanSet);
               this.currentPage = 0;
               this.rebuildScreen();
            }).bounds(panelX + 10, y, buttonWidth, 20).build()
         );
         y += 24;
      }

      this.addRenderableWidget(Button.builder(Component.translatable("gui.millenaire.importtable.import_all"), btn -> {
         CompoundTag data = new CompoundTag();
         data.putString("cultureKey", this.selectedCultureKey);
         data.putString("buildingId", this.selectedBuildingId);
         data.putString("variant", this.selectedVariant);
         this.sendAction(ImportTableActionPayload.Action.IMPORT_ALL, data);
         this.onClose();
      }).bounds(panelX + 10, y, buttonWidth, 20).build());
      y += 24;

      for (int lvl : this.pageSlice(this.buildingLevels)) {
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.millenaire.importtable.building_level_label", new Object[]{this.selectedBuildingId, this.selectedVariant, lvl}),
                  btn -> {
                     CompoundTag data = new CompoundTag();
                     data.putString("cultureKey", this.selectedCultureKey);
                     data.putString("buildingId", this.selectedBuildingId);
                     data.putString("variant", this.selectedVariant);
                     data.putInt("level", lvl);
                     data.putBoolean("isSubBuilding", false);
                     data.putString("parentBuildingId", "");
                     this.sendAction(ImportTableActionPayload.Action.IMPORT_LEVEL, data);
                     this.onClose();
                  }
               )
               .bounds(panelX + 10, y, buttonWidth, 20)
               .build()
         );
         y += 22;
      }
   }

   private void buildImportExportDirScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int buttonWidth = 236;

      for (String building : this.pageSlice(this.exportBuildings)) {
         this.addRenderableWidget(Button.builder(Component.literal(building), btn -> {
            this.selectedExportBuildingId = building;
            this.loadExportVariants();
            this.loadExportLevels();
            this.navigateTo(ImportTableScreen.ScreenState.IMPORT_EXPORT_DIR_BUILDING);
         }).bounds(panelX + 10, y, buttonWidth, 20).build());
         y += 22;
      }

      if (this.exportBuildings.isEmpty()) {
      }
   }

   private void buildImportExportDirBuildingScreen(int panelX, int panelY) {
      int y = panelY + 22;
      int buttonWidth = 236;
      if (this.availableExportVariants.size() > 1) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.importtable.variant_hint", new Object[]{this.selectedExportVariant}), btn -> {
               int idx = this.availableExportVariants.indexOf(this.selectedExportVariant);
               this.selectedExportVariant = this.availableExportVariants.get((idx + 1) % this.availableExportVariants.size());
               this.loadExportLevels();
               this.currentPage = 0;
               this.rebuildScreen();
            }).bounds(panelX + 10, y, buttonWidth, 20).build()
         );
         y += 24;
      }

      this.addRenderableWidget(Button.builder(Component.translatable("gui.millenaire.importtable.import_all"), btn -> {
         CompoundTag data = new CompoundTag();
         data.putString("buildingId", this.selectedExportBuildingId);
         data.putString("variant", this.selectedExportVariant);
         this.sendAction(ImportTableActionPayload.Action.IMPORT_ALL_EXPORT, data);
         this.onClose();
      }).bounds(panelX + 10, y, buttonWidth, 20).build());
      y += 24;

      for (int lvl : this.pageSlice(this.exportLevels)) {
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable(
                     "gui.millenaire.importtable.building_level_label", new Object[]{this.selectedExportBuildingId, this.selectedExportVariant, lvl}
                  ),
                  btn -> {
                     CompoundTag data = new CompoundTag();
                     data.putString("buildingId", this.selectedExportBuildingId);
                     data.putString("variant", this.selectedExportVariant);
                     data.putInt("level", lvl);
                     this.sendAction(ImportTableActionPayload.Action.IMPORT_LEVEL_EXPORT, data);
                     this.onClose();
                  }
               )
               .bounds(panelX + 10, y, buttonWidth, 20)
               .build()
         );
         y += 22;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.lengthField != null && this.lengthField.isFocused() && this.lengthField.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      } else if (this.plotWidthField != null && this.plotWidthField.isFocused() && this.plotWidthField.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      } else if (this.startingLevelField != null && this.startingLevelField.isFocused() && this.startingLevelField.keyPressed(keyCode, scanCode, modifiers)) {
         return true;
      } else {
         return this.plotHeightField != null && this.plotHeightField.isFocused() && this.plotHeightField.keyPressed(keyCode, scanCode, modifiers)
            ? true
            : super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   private void buildCostsScreen(int panelX, int panelY) {
   }

   private void renderCosts(GuiGraphics graphics, int x, int y) {
      if (this.costs == null) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.millenaire.importtable.computing"), x + 118, y + 30, -10066347);
      } else if (this.costs.isEmpty()) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.millenaire.importtable.no_resources"), x + 118, y + 30, -10066347);
      } else {
         List<ImportTableCostsPayload.Entry> page = this.pageSlice(this.costs);
         int lineY = y;

         for (ImportTableCostsPayload.Entry entry : page) {
            String line = entry.itemId() + "  ×" + entry.quantity();
            graphics.drawString(this.font, line, x, lineY, -13421773, false);
            lineY += 14;
         }
      }
   }

   public static void applyCosts(ImportTableCostsPayload payload) {
      if (Minecraft.getInstance().screen instanceof ImportTableScreen screen) {
         screen.costs = payload.costs();
         screen.costsLabel = payload.buildingId() + " " + payload.variant() + " level " + payload.level();
         if (screen.currentState == ImportTableScreen.ScreenState.COSTS) {
            screen.rebuildScreen();
         }
      }
   }

   private int maxItemsPerPage() {
      int headerPx = 0;
      switch (this.currentState) {
         case IMPORT_CULTURE_BUILDING:
            if (this.currentBuildingPlanSet != null && this.currentBuildingPlanSet.variants().size() > 1) {
               headerPx += 24;
            }

            headerPx += 24;
            break;
         case IMPORT_EXPORT_DIR_BUILDING:
            if (this.availableExportVariants.size() > 1) {
               headerPx += 24;
            }

            headerPx += 24;
      }

      int titlePx = 22;
      int bottomPx = 38;
      int available = 220 - titlePx - bottomPx - headerPx;
      int itemPx = 22;
      int fit = Math.max(1, available / itemPx);
      return Math.min(fit, 7);
   }

   private int computeTotalPages() {
      int listSize = switch (this.currentState) {
         case IMPORT_CULTURE -> this.cultureCategories.size();
         case IMPORT_CULTURE_SUBDIR -> this.categoryBuildings.size();
         case IMPORT_CULTURE_BUILDING -> this.buildingLevels.size();
         case IMPORT_EXPORT_DIR -> this.exportBuildings.size();
         case IMPORT_EXPORT_DIR_BUILDING -> this.exportLevels.size();
         case COSTS -> this.costs == null ? 0 : this.costs.size();
         default -> 0;
      };
      int max = this.maxItemsPerPage();
      return listSize <= max ? 1 : (listSize + max - 1) / max;
   }

   private <T> List<T> pageSlice(List<T> list) {
      int max = this.maxItemsPerPage();
      int from = this.currentPage * max;
      int to = Math.min(from + max, list.size());
      return from >= list.size() ? List.of() : list.subList(from, to);
   }

   private void navigateTo(ImportTableScreen.ScreenState state) {
      this.previousScreens.push(this.currentState);
      this.currentState = state;
      this.currentPage = 0;
      this.rebuildScreen();
   }

   private void navigateBack() {
      if (!this.previousScreens.isEmpty()) {
         this.currentState = this.previousScreens.pop();
      } else {
         this.currentState = ImportTableScreen.ScreenState.HOME;
      }

      this.currentPage = 0;
      this.rebuildScreen();
   }

   private void sendAction(ImportTableActionPayload.Action action, CompoundTag data) {
      PacketDistributor.sendToServer(new ImportTableActionPayload(this.syncData.blockPos(), action, data), new CustomPacketPayload[0]);
   }

   private void sendSettingsUpdate() {
      CompoundTag data = new CompoundTag();
      data.putInt("orientation", this.orientation);
      data.putInt("startingLevel", this.startingLevel);
      data.putInt("height", this.plotHeight);
      data.putBoolean("exportSnow", this.exportSnow);
      data.putBoolean("importMockBlocks", this.importMockBlocks);
      data.putBoolean("convertToPreserveGround", this.convertToPreserveGround);
      this.sendAction(ImportTableActionPayload.Action.UPDATE_SETTINGS, data);
   }

   private void loadCultureCategories() {
      Map<String, List<BuildingPlanSet>> byCategory = new TreeMap<>();

      for (BuildingPlanSet planSet : ModCultures.getAllBuildingPlanSets().values()) {
         if (planSet.culture().toString().equals(this.selectedCultureKey)) {
            byCategory.computeIfAbsent(planSet.category(), k -> new ArrayList<>()).add(planSet);
         }
      }

      this.cultureCategories = new ArrayList<>(byCategory.keySet());
   }

   private void loadCategoryBuildings() {
      List<BuildingPlanSet> buildings = new ArrayList<>();

      for (BuildingPlanSet planSet : ModCultures.getAllBuildingPlanSets().values()) {
         if (planSet.culture().toString().equals(this.selectedCultureKey) && planSet.category().equals(this.selectedCategory)) {
            buildings.add(planSet);
         }
      }

      buildings.sort((a, b) -> a.buildingId().compareToIgnoreCase(b.buildingId()));
      this.categoryBuildings = buildings;
   }

   private void loadBuildingLevels(BuildingPlanSet planSet) {
      List<BuildingPlanSet.LevelDef> levels = planSet.variants().get(this.selectedVariant);
      if (levels == null) {
         this.buildingLevels = List.of();
      } else {
         this.buildingLevels = levels.stream().map(BuildingPlanSet.LevelDef::level).toList();
      }
   }

   private void loadExportBuildings() {
      this.exportBuildings = new ArrayList<>();
      Path gameDir = singleplayerGameDir();
      if (gameDir != null) {
         this.exportBuildings.addAll(BuildingExporter.listExportedBuildingIds(gameDir));
      }
   }

   private void loadExportLevels() {
      this.exportLevels = new ArrayList<>();
      Path gameDir = singleplayerGameDir();
      if (gameDir != null) {
         this.exportLevels.addAll(BuildingExporter.listExportedLevels(gameDir, this.selectedExportBuildingId, this.selectedExportVariant));
      }
   }

   private void loadExportVariants() {
      this.availableExportVariants = new ArrayList<>();
      Path gameDir = singleplayerGameDir();
      if (gameDir != null) {
         this.availableExportVariants.addAll(BuildingExporter.listExportedVariants(gameDir, this.selectedExportBuildingId));
         if (!this.availableExportVariants.isEmpty() && !this.availableExportVariants.contains(this.selectedExportVariant)) {
            this.selectedExportVariant = this.availableExportVariants.get(0);
         }
      }
   }

   private static Path singleplayerGameDir() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      return server == null ? null : server.getServerDirectory();
   }

   enum ScreenState {
      HOME,
      NEW_BUILDING,
      SETTINGS,
      IMPORT_CULTURE,
      IMPORT_CULTURE_SUBDIR,
      IMPORT_CULTURE_BUILDING,
      IMPORT_EXPORT_DIR,
      IMPORT_EXPORT_DIR_BUILDING,
      COSTS;
   }
}
