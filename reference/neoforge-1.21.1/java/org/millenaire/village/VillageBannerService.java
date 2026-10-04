package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatternLayers.Layer;
import org.millenaire.culture.Culture;
import org.millenaire.culture.VillageType;
import org.slf4j.Logger;

public final class VillageBannerService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<String, ResourceLocation> SHORTCODE_TO_PATTERN = buildShortcodeMap();

   private VillageBannerService() {
   }

   private static Map<String, ResourceLocation> buildShortcodeMap() {
      Map<String, ResourceLocation> m = new HashMap<>();
      m.put("bl", rl("minecraft", "square_bottom_left"));
      m.put("br", rl("minecraft", "square_bottom_right"));
      m.put("tl", rl("minecraft", "square_top_left"));
      m.put("tr", rl("minecraft", "square_top_right"));
      m.put("bs", rl("minecraft", "stripe_bottom"));
      m.put("ts", rl("minecraft", "stripe_top"));
      m.put("ls", rl("minecraft", "stripe_left"));
      m.put("rs", rl("minecraft", "stripe_right"));
      m.put("cs", rl("minecraft", "stripe_center"));
      m.put("ms", rl("minecraft", "stripe_middle"));
      m.put("drs", rl("minecraft", "stripe_downright"));
      m.put("dls", rl("minecraft", "stripe_downleft"));
      m.put("ss", rl("minecraft", "small_stripes"));
      m.put("cr", rl("minecraft", "cross"));
      m.put("sc", rl("minecraft", "straight_cross"));
      m.put("bt", rl("minecraft", "triangle_bottom"));
      m.put("tt", rl("minecraft", "triangle_top"));
      m.put("bts", rl("minecraft", "triangles_bottom"));
      m.put("tts", rl("minecraft", "triangles_top"));
      m.put("ld", rl("minecraft", "diagonal_left"));
      m.put("rd", rl("minecraft", "diagonal_right"));
      m.put("lud", rl("minecraft", "diagonal_up_left"));
      m.put("rud", rl("minecraft", "diagonal_up_right"));
      m.put("vh", rl("minecraft", "half_vertical"));
      m.put("vhr", rl("minecraft", "half_vertical_right"));
      m.put("hh", rl("minecraft", "half_horizontal"));
      m.put("hhb", rl("minecraft", "half_horizontal_bottom"));
      m.put("bo", rl("minecraft", "border"));
      m.put("cbo", rl("minecraft", "curly_border"));
      m.put("cre", rl("minecraft", "creeper"));
      m.put("gra", rl("minecraft", "gradient"));
      m.put("gru", rl("minecraft", "gradient_up"));
      m.put("bri", rl("minecraft", "bricks"));
      m.put("sku", rl("minecraft", "skull"));
      m.put("flo", rl("minecraft", "flower"));
      m.put("moj", rl("minecraft", "mojang"));
      m.put("glb", rl("minecraft", "globe"));
      m.put("pig", rl("minecraft", "piglin"));
      m.put("mc", rl("minecraft", "circle"));
      m.put("mr", rl("minecraft", "rhombus"));
      m.put("byz", rl("millenaire", "byzantine"));
      m.put("by1", rl("millenaire", "byzantine_1"));
      m.put("by2", rl("millenaire", "byzantine_2"));
      m.put("sjk", rl("millenaire", "seljuk"));
      m.put("sjkr", rl("millenaire", "seljuk_rel"));
      m.put("sjkm", rl("millenaire", "seljuk_mil"));
      m.put("may", rl("millenaire", "mayan"));
      m.put("ma1", rl("millenaire", "mayan_1"));
      m.put("ma2", rl("millenaire", "mayan_2"));
      m.put("ma3", rl("millenaire", "mayan_3"));
      m.put("ma4", rl("millenaire", "mayan_4"));
      m.put("inu", rl("millenaire", "inuit"));
      m.put("iu1", rl("millenaire", "inuit_1"));
      m.put("iu2", rl("millenaire", "inuit_2"));
      m.put("iu3", rl("millenaire", "inuit_3"));
      m.put("iu4", rl("millenaire", "inuit_4"));
      m.put("ind", rl("millenaire", "indian"));
      m.put("in1", rl("millenaire", "indian_1"));
      m.put("in2", rl("millenaire", "indian_2"));
      m.put("in3", rl("millenaire", "indian_3"));
      m.put("in4", rl("millenaire", "indian_4"));
      m.put("in5", rl("millenaire", "indian_5"));
      m.put("nor", rl("millenaire", "norman"));
      m.put("jap", rl("millenaire", "japanese"));
      m.put("jaa", rl("millenaire", "japanese_agr"));
      m.put("jam", rl("millenaire", "japanese_mil"));
      m.put("jar", rl("millenaire", "japanese_rel"));
      m.put("jat", rl("millenaire", "japanese_tra"));
      return Map.copyOf(m);
   }

   private static ResourceLocation rl(String ns, String path) {
      return ResourceLocation.fromNamespaceAndPath(ns, path);
   }

   public static ItemStack generateVillageBanner(VillageType type, RegistryAccess registryAccess, RandomSource random) {
      List<String> pool = type.bannerJsons();
      if (pool != null && !pool.isEmpty()) {
         List<String> shuffled = new ArrayList<>(pool);
         Collections.shuffle(shuffled, new Random(random.nextLong()));

         for (String nbt : shuffled) {
            ItemStack stack = parseLegacyBanner(nbt, registryAccess);
            if (!stack.isEmpty()) {
               return stack;
            }
         }

         LOGGER.warn("[banner] village type {} has {} banner entries but none parsed", type.id(), pool.size());
         return ItemStack.EMPTY;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static ItemStack getCultureBanner(Culture culture, RegistryAccess registryAccess) {
      if (culture != null && culture.cultureBannerNbt() != null) {
         ItemStack built = parseLegacyBanner(culture.cultureBannerNbt(), registryAccess);
         if (built.isEmpty()) {
            LOGGER.warn("[banner] culture {} banner NBT did not parse: {}", culture.id(), culture.cultureBannerNbt());
         }

         return built;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static ItemStack parseLegacyBanner(String legacyNbt, RegistryAccess registryAccess) {
      if (legacyNbt != null && !legacyNbt.isBlank()) {
         try {
            CompoundTag root = TagParser.parseTag(legacyNbt);
            CompoundTag bet = root.contains("BlockEntityTag") ? root.getCompound("BlockEntityTag") : root;
            int legacyBase = bet.contains("Base") ? bet.getInt("Base") : 0;
            DyeColor baseColor = DyeColor.byId(15 - legacyBase);
            ItemStack stack = new ItemStack(BannerBlock.byColor(baseColor).asItem(), 1);
            if (!bet.contains("Patterns")) {
               return stack;
            }

            ListTag patList = bet.getList("Patterns", 10);
            RegistryLookup<BannerPattern> patternLookup = registryAccess.lookupOrThrow(Registries.BANNER_PATTERN);
            List<Layer> layers = new ArrayList<>(patList.size());

            for (int i = 0; i < patList.size(); i++) {
               CompoundTag pt = patList.getCompound(i);
               String code = pt.getString("Pattern");
               int legacyColor = pt.getInt("Color");
               DyeColor dye = DyeColor.byId(15 - legacyColor);
               ResourceLocation patternId = SHORTCODE_TO_PATTERN.get(code);
               if (patternId == null) {
                  LOGGER.warn("[banner] unknown legacy pattern shortcode: '{}'", code);
               } else {
                  Holder<BannerPattern> holder = (Holder<BannerPattern>)patternLookup.get(ResourceKey.create(Registries.BANNER_PATTERN, patternId))
                     .orElse(null);
                  if (holder == null) {
                     LOGGER.warn("[banner] pattern {} missing from registry (asset: {})", code, patternId);
                  } else {
                     layers.add(new Layer(holder, dye));
                  }
               }
            }

            if (!layers.isEmpty()) {
               stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
            }

            return stack;
         } catch (Exception e) {
            LOGGER.warn("[banner] failed to parse '{}': {}", legacyNbt, e.getMessage());
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static ItemStack fallbackBanner() {
      return new ItemStack(Items.WHITE_BANNER);
   }
}
