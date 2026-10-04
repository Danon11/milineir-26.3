package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.millenaire.DisplayUtils;
import org.millenaire.client.ClientLanguageCache;
import org.millenaire.client.ClientQuestCache;
import org.millenaire.client.model.MillFemaleAsymModel;
import org.millenaire.client.model.MillFemaleSymModel;
import org.millenaire.client.model.MillMaleModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.config.MillenaireClientConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModelType;
import org.millenaire.item.ModItems;
import org.millenaire.language.DisplayNameResolver;
import org.millenaire.language.SpeechResolver;

@OnlyIn(Dist.CLIENT)
public class MillVillagerRenderer extends MobRenderer<MillVillager, HumanoidModel<MillVillager>> {
   private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/villager/default.png");
   private final HumanoidModel<MillVillager> maleModel = (HumanoidModel<MillVillager>)this.model;
   private final HumanoidModel<MillVillager> femaleSymModel;
   private final HumanoidModel<MillVillager> femaleAsymModel;

   public MillVillagerRenderer(Context context) {
      super(context, new MillMaleModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_MALE)), 0.5F);
      this.femaleSymModel = new MillFemaleSymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_SYM));
      this.femaleAsymModel = new MillFemaleAsymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM));
      Map<ModelType, HumanoidModel<MillVillager>> clothModels0 = Map.of(
         ModelType.MALE,
         new MillMaleModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_0)),
         ModelType.FEMALE_SYM,
         new MillFemaleSymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_0)),
         ModelType.FEMALE_ASYM,
         new MillFemaleAsymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_0))
      );
      Map<ModelType, HumanoidModel<MillVillager>> clothModels1 = Map.of(
         ModelType.MALE,
         new MillMaleModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_1)),
         ModelType.FEMALE_SYM,
         new MillFemaleSymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_1)),
         ModelType.FEMALE_ASYM,
         new MillFemaleAsymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_1))
      );
      this.addLayer(new ClothingLayer(this, clothModels0, 0));
      this.addLayer(new ClothingLayer(this, clothModels1, 1));
      this.addLayer(
         new HumanoidArmorLayer(
            this,
            new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
         )
      );
      this.addLayer(new ItemInHandLayer(this, context.getItemInHandRenderer()));
   }

   public void render(MillVillager entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      this.model = switch (entity.getModelType()) {
         case FEMALE_SYM -> this.femaleSymModel;
         case FEMALE_ASYM -> this.femaleAsymModel;
         default -> this.maleModel;
      };
      ((HumanoidModel)this.model).rightArmPose = entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
      ((HumanoidModel)this.model).leftArmPose = entity.getItemInHand(InteractionHand.OFF_HAND).isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      List<ItemStack> floatingIcons = defineFloatingIcons(entity);
      if (!floatingIcons.isEmpty()) {
         this.renderFloatingIcons(entity, floatingIcons, poseStack, bufferSource, packedLight);
      }
   }

   protected void setupRotations(MillVillager entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
      if (entity.isVillagerSleeping()) {
         float sleepRot = 180.0F - entity.getYRot();
         poseStack.mulPose(Axis.YP.rotationDegrees(sleepRot));
         poseStack.mulPose(Axis.ZP.rotationDegrees(this.getFlipDegrees(entity)));
         poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
      } else {
         super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
      }
   }

   protected void scale(MillVillager entity, PoseStack poseStack, float partialTick) {
      float s = entity.getVillagerScale();
      poseStack.scale(s, s, s);
   }

   public ResourceLocation getTextureLocation(MillVillager entity) {
      ResourceLocation tex = entity.getTexture();
      return tex != null ? tex : DEFAULT_TEXTURE;
   }

   protected boolean shouldShowName(MillVillager entity) {
      if (entity.isGuiPreviewMode()) {
         return false;
      } else if (!(Boolean)MillenaireClientConfig.CLIENT.showNames.get()) {
         return false;
      } else {
         String name = entity.getVillagerDisplayName();
         if (name != null && !name.isEmpty()) {
            int dist = MillenaireClientConfig.CLIENT.namesDistance.getAsInt();
            double distanceSq = this.entityRenderDispatcher.distanceToSqr(entity);
            return distanceSq <= (double)dist * dist;
         } else {
            return false;
         }
      }
   }

   protected void renderNameTag(MillVillager entity, Component content, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
      String displayName = entity.getVillagerDisplayName();
      if (displayName != null && !displayName.isEmpty()) {
         Font font = this.getFont();
         String nameStr = !displayName.startsWith("entity.") && !displayName.startsWith("role.")
            ? displayName
            : Component.translatable(displayName).getString();
         String nativeRole = entity.getNativeRoleName();
         String roleName = entity.getRoleName();
         boolean hasRole = nativeRole != null && !nativeRole.isEmpty();
         String nameRoleStr;
         if (hasRole) {
            ResourceLocation cultureId = entity.getCultureId();
            boolean canReadNames = cultureId != null && ClientLanguageCache.canReadVillagerNames(cultureId);
            if (canReadNames && roleName != null && !roleName.isEmpty()) {
               String translatedRole = roleName.startsWith("role.") ? Component.translatable(roleName).getString() : roleName;
               nameRoleStr = DisplayNameResolver.equivalent(nativeRole, translatedRole)
                  ? nameStr + ", " + nativeRole
                  : nameStr + ", " + nativeRole + " (" + translatedRole + ")";
            } else {
               nameRoleStr = nameStr + ", " + nativeRole;
            }
         } else {
            nameRoleStr = nameStr;
         }

         Component nameRoleLine = Component.literal(nameRoleStr);
         String goalLabel = entity.getGoalLabel();
         boolean hasGoal = goalLabel != null && !goalLabel.isEmpty();
         Component goalLine = hasGoal ? DisplayUtils.resolveGoalLabelComponent(goalLabel) : null;
         String questLabel = ClientQuestCache.getQuestLabelForVillager(entity.getUUID());
         boolean hasQuest = questLabel != null;
         Component questLine = hasQuest ? Component.literal("[" + questLabel + "]") : null;
         int lineHeight = 9 + 1;
         int bgColor = -1342177280;
         poseStack.pushPose();
         if (entity.isVillagerSleeping()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-270.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-this.getFlipDegrees(entity)));
            float sleepRot = 180.0F - entity.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(-sleepRot));
         }

         float scale = entity.getVillagerScale();
         float nameTagHeight = entity.getBbHeight() * scale + 0.5F;
         poseStack.translate(0.0, nameTagHeight, 0.0);
         poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
         poseStack.scale(0.025F, -0.025F, 0.025F);
         Matrix4f matrix = poseStack.last().pose();
         int emissiveLight = 15728880;
         BufferSource immediateBuffer = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
         float nameY = -lineHeight;
         float questY = nameY - (hasQuest ? lineHeight : 0);
         float goalY = (hasQuest ? questY : nameY) - (hasGoal ? lineHeight : 0);
         if (hasGoal) {
            float goalX = -font.width(goalLine) / 2.0F;
            drawNameLine(font, goalLine, goalX, goalY, -2307474, matrix, immediateBuffer, bgColor, emissiveLight);
         }

         if (hasQuest) {
            float questX = -font.width(questLine) / 2.0F;
            drawNameLine(font, questLine, questX, questY, -1596072483, matrix, immediateBuffer, bgColor, emissiveLight);
         }

         float nameX = -font.width(nameRoleLine) / 2.0F;
         drawNameLine(font, nameRoleLine, nameX, nameY, -1, matrix, immediateBuffer, bgColor, emissiveLight);
         String speechText = entity.getSpeechText();
         boolean hasSpeech = speechText != null && !speechText.isEmpty();
         if (hasSpeech) {
            int speechBg = -1342177280;
            int maxSpeechChars = 60;
            String[] speechParts = resolveSpeechLines(speechText, entity);
            List<String> nativeLines = speechParts[0] != null ? wordWrap(speechParts[0], maxSpeechChars) : List.of();
            List<String> translationLines = speechParts[1] != null ? wordWrap(speechParts[1], maxSpeechChars) : List.of();
            float topLine = hasGoal ? goalY : (hasQuest ? questY : nameY);
            float currentY = topLine - lineHeight - 2.0F;

            for (int i = translationLines.size() - 1; i >= 0; i--) {
               Component transComp = Component.literal(translationLines.get(i));
               float transX = -font.width(transComp) / 2.0F;
               drawNameLine(font, transComp, transX, currentY, -5636096, matrix, immediateBuffer, speechBg, emissiveLight);
               currentY -= lineHeight;
            }

            for (int i = nativeLines.size() - 1; i >= 0; i--) {
               Component nativeComp = Component.literal(nativeLines.get(i));
               float nativeX = -font.width(nativeComp) / 2.0F;
               drawNameLine(font, nativeComp, nativeX, currentY, -11184641, matrix, immediateBuffer, speechBg, emissiveLight);
               currentY -= lineHeight;
            }
         }

         immediateBuffer.endBatch();
         poseStack.popPose();
      }
   }

   private static void drawNameLine(
      Font font, Component text, float x, float y, int color, Matrix4f matrix, MultiBufferSource bufferSource, int bgColor, int emissiveLight
   ) {
      int seeThruColor = color & 16777215 | -2147483648;
      font.drawInBatch(text, x, y, seeThruColor, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, bgColor, emissiveLight);
      font.drawInBatch(text, x, y, color, false, matrix, bufferSource, DisplayMode.NORMAL, 0, emissiveLight);
   }

   private static String[] resolveSpeechLines(String speechRef, MillVillager entity) {
      return SpeechResolver.resolve(speechRef);
   }

   private static List<ItemStack> defineFloatingIcons(MillVillager entity) {
      List<ItemStack> icons = new ArrayList<>();
      if (entity.isChief()) {
         icons.add(new ItemStack(Items.GOLDEN_HELMET));
      }

      if (entity.isSelling()) {
         icons.add(new ItemStack((ItemLike)ModItems.PURSE.get()));
      }

      if (entity.isForeignMerchant()) {
         icons.add(new ItemStack((ItemLike)ModItems.PURSE.get()));
      }

      return icons;
   }

   private void renderFloatingIcons(MillVillager entity, List<ItemStack> icons, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      float scale = entity.getVillagerScale();
      float iconHeight = entity.getBbHeight() * scale + 1.3F;
      poseStack.pushPose();
      poseStack.translate(0.0, iconHeight, 0.0);
      poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
      poseStack.scale(0.5F, 0.5F, 0.5F);
      float iconSpacing = 0.8F;
      float startX = -((icons.size() - 1) * iconSpacing) / 2.0F;

      for (int i = 0; i < icons.size(); i++) {
         poseStack.pushPose();
         poseStack.translate(startX + i * iconSpacing, 0.0, 0.0);
         Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(icons.get(i), ItemDisplayContext.GUI, 15728880, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());
         poseStack.popPose();
      }

      poseStack.popPose();
   }

   private static List<String> wordWrap(String text, int maxChars) {
      if (text.length() <= maxChars) {
         return List.of(text);
      }

      List<String> lines = new ArrayList<>();
      int start = 0;

      while (start < text.length()) {
         if (start + maxChars >= text.length()) {
            lines.add(text.substring(start));
            break;
         }

         int end = start + maxChars;
         int lastSpace = text.lastIndexOf(32, end);
         if (lastSpace <= start) {
            lastSpace = end;
         }

         lines.add(text.substring(start, lastSpace));
         start = lastSpace < text.length() && text.charAt(lastSpace) == ' ' ? lastSpace + 1 : lastSpace;
      }

      return lines;
   }
}
