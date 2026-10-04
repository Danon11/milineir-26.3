package org.millenaire.building;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class NbtPaletteHelper {
   private NbtPaletteHelper() {
   }

   @Nullable
   public static ListTag resolvePaletteTag(CompoundTag nbt) {
      if (nbt.contains("palette", 9)) {
         return nbt.getList("palette", 10);
      }

      if (nbt.contains("palettes", 9)) {
         ListTag palettesTag = nbt.getList("palettes", 9);
         if (!palettesTag.isEmpty()) {
            return palettesTag.getList(0);
         }
      }

      return null;
   }
}
