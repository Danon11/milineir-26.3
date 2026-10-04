package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.DisplayUtils;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.NameLists;
import org.millenaire.culture.VillagerType;
import org.slf4j.Logger;

public final class VillagerAppearanceFactory {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int MAX_CHILD_SIZE = 20;
   private static final float SCALE_BASE_MIN = 0.8F;
   private static final float SCALE_VARIATION = 0.09F;

   private VillagerAppearanceFactory() {
   }

   public static void randomizeAppearance(MillVillager villager, VillagerType vType) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      ModelType modelType = vType.modelType();
      List<ResourceLocation> textures = vType.textures();
      ResourceLocation texture = textures.isEmpty() ? null : textures.get(random.nextInt(textures.size()));
      float scale;
      if (vType.isChild()) {
         if (villager.getChildSize() < 0) {
            villager.setChildSize(0);
         }

         scale = villager.getVillagerScale();
      } else {
         scale = vType.baseScale() * (0.8F + random.nextFloat() * 0.09F);
      }

      String roleName = DisplayUtils.resolveRoleKey(vType.id());
      String[] names = generateName(vType);
      villager.initAppearance(modelType, texture, null, null, scale, names[0], names[1], roleName);
      villager.updateClothTextures(vType);
   }

   public static String[] generateName(VillagerType vType) {
      ResourceLocation cultureId = vType.culture();
      NameLists nameLists = ModCultures.getNameLists(cultureId);
      if (nameLists == null) {
         LOGGER.warn("[Millénaire] NameLists not loaded for culture {} — villager will get a placeholder name", cultureId);
         return new String[]{"entity.millenaire.villager", ""};
      }

      String firstNameKey = vType.firstNameList();
      if (firstNameKey == null) {
         firstNameKey = vType.gender() == Gender.FEMALE ? "women_names" : "men_names";
      }

      String firstName = nameLists.randomFrom(firstNameKey);
      String familyKey = vType.familyNameList();
      if (familyKey == null) {
         familyKey = vType.hasTag("noble") ? "noble_family_names" : "family_names";
      }

      String familyName = nameLists.randomFrom(familyKey);
      if (firstName == null) {
         LOGGER.warn("[Millénaire] No first name found for key '{}' in culture {} — using placeholder", firstNameKey, cultureId);
         firstName = "entity.millenaire.villager";
      }

      if (familyName == null) {
         familyName = "";
      }

      return new String[]{firstName, familyName};
   }

   public static boolean isCorruptedName(@Nullable String name) {
      return name != null && name.startsWith("entity.");
   }

   public static float computeChildScale(int childSize, Gender gender) {
      if (childSize >= 20) {
         return gender == Gender.FEMALE ? 0.8F : 0.9F;
      } else {
         return 0.5F + childSize / 100.0F;
      }
   }
}
