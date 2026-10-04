package org.millenaire.village.panel;

import java.util.List;
import javax.annotation.Nullable;

public record PanelContent(PanelType type, String title, List<PanelLine> lines, boolean titleTranslatable, @Nullable String[] titleArgs) {
   public PanelContent(PanelType type, String title, List<PanelLine> lines) {
      this(type, title, lines, false, null);
   }
}
