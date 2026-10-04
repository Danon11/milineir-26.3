package org.millenaire.culture;

import net.minecraft.resources.ResourceLocation;

public final class CrossFolderConflictException extends RuntimeException {
   private final ResourceLocation culture;

   public CrossFolderConflictException(ResourceLocation culture, String message) {
      super(message);
      this.culture = culture;
   }

   public ResourceLocation culture() {
      return this.culture;
   }
}
