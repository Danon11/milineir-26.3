package org.millenaire.map;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.millenaire.culture.Culture;
import org.slf4j.Logger;

public final class VillageMapDecorationTypes {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final DeferredRegister<MapDecorationType> REGISTER = DeferredRegister.create(Registries.MAP_DECORATION_TYPE, "millenaire");
   public static final ResourceLocation GENERIC_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "village_generic");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> NORMAN = decoration("village_norman");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> INDIAN = decoration("village_indian");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> MAYAN = decoration("village_mayan");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> BYZANTINE = decoration("village_byzantine");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> JAPANESE = decoration("village_japanese");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> SELJUK = decoration("village_seljuk");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> INUIT = decoration("village_inuit");
   public static final DeferredHolder<MapDecorationType, MapDecorationType> GENERIC = decoration("village_generic");

   private VillageMapDecorationTypes() {
   }

   public static void register(IEventBus modEventBus) {
      REGISTER.register(modEventBus);
   }

   private static DeferredHolder<MapDecorationType, MapDecorationType> decoration(String name) {
      return REGISTER.register(name, () -> new MapDecorationType(ResourceLocation.fromNamespaceAndPath("millenaire", name), true, -1, false, false));
   }

   public static ResourceLocation resolveId(Culture culture) {
      return culture.mapIcon().orElse(GENERIC_ID);
   }

   public static Holder<MapDecorationType> resolveHolder(Culture culture) {
      ResourceLocation id = resolveId(culture);
      ResourceKey<MapDecorationType> key = ResourceKey.create(Registries.MAP_DECORATION_TYPE, id);
      return BuiltInRegistries.MAP_DECORATION_TYPE.getHolder(key).map(h -> (Holder<MapDecorationType>)h).orElseGet(() -> {
         LOGGER.warn("MapDecorationType {} not registered, falling back to {}", id, GENERIC_ID);
         return GENERIC;
      });
   }
}
