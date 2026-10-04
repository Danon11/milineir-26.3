package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;

final class OverlayBuilder {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final LinkedHashMap<String, LayerEntry> overlayEntries = new LinkedHashMap<>();
   private final Map<String, List<LayerEntry>> historyEntries = new LinkedHashMap<>();
   private final Map<String, Integer> overrideCounts = new LinkedHashMap<>();
   private final boolean silent;

   OverlayBuilder() {
      this(false);
   }

   OverlayBuilder(boolean silent) {
      this.silent = silent;
   }

   void addLayer(List<LayerEntry> entries) {
      this.addLayer(entries, false);
   }

   void addSubmodLayer(List<LayerEntry> entriesIncludingOversize) {
      this.addLayer(entriesIncludingOversize, true);
   }

   private void addLayer(List<LayerEntry> entries, boolean enforceSizeCap) {
      for (LayerEntry entry : entries) {
         String key = entry.relPath();
         if (!enforceSizeCap || entry.size() <= 10000000L) {
            this.insert(key, entry);
         } else if (!this.silent) {
            LayerEntry existing = this.overlayEntries.get(key);
            if (existing != null) {
               LOGGER.warn(
                  "Rejected oversize override {} from {}; keeping {}",
                  new Object[]{entry.relPath(), entry.source().displayName(), existing.source().displayName()}
               );
            } else {
               LOGGER.warn(
                  "Rejected oversize file {} from {} ({} bytes > {} bytes)",
                  new Object[]{entry.relPath(), entry.source().displayName(), entry.size(), 10000000L}
               );
            }
         }
      }
   }

   private void insert(String key, LayerEntry entry) {
      LayerEntry existing = this.overlayEntries.get(key);
      if (existing != null && !this.silent) {
         if (!existing.originalRelPath().equals(entry.originalRelPath())) {
            LOGGER.warn(
               "Case-only collision: '{}' ({}) overlays '{}' ({})",
               new Object[]{entry.originalRelPath(), entry.source().displayName(), existing.originalRelPath(), existing.source().displayName()}
            );
         }

         if (!existing.source().equals(entry.source())) {
            LOGGER.debug("{}: {} overrides {}", new Object[]{key, entry.source().displayName(), existing.source().displayName()});
            String pair = entry.source().displayName() + " over " + existing.source().displayName();
            this.overrideCounts.merge(pair, 1, Integer::sum);
         }
      }

      this.overlayEntries.put(key, entry);
      this.historyEntries.computeIfAbsent(key, k -> new ArrayList<>(2)).add(0, entry);
   }

   ContentFs build() {
      if (!this.silent && !this.overrideCounts.isEmpty()) {
         for (Entry<String, Integer> e : this.overrideCounts.entrySet()) {
            LOGGER.info("Overlay: {} ({} files; -Dlog.level.OverlayBuilder=DEBUG for per-file detail)", e.getKey(), e.getValue());
         }
      }

      LinkedHashMap<String, Resource> overlay = new LinkedHashMap<>(this.overlayEntries.size());
      Map<String, List<Resource>> history = new LinkedHashMap<>(this.historyEntries.size());
      OverlayContentFs fs = new OverlayContentFs(overlay, history);

      for (Entry<String, LayerEntry> e : this.overlayEntries.entrySet()) {
         LayerEntry le = e.getValue();
         overlay.put(e.getKey(), le.materialiser().apply(le.relPath(), fs));
      }

      for (Entry<String, List<LayerEntry>> e : this.historyEntries.entrySet()) {
         List<LayerEntry> src = e.getValue();
         List<Resource> dst = new ArrayList<>(src.size());

         for (LayerEntry le : src) {
            dst.add(le.materialiser().apply(le.relPath(), fs));
         }

         history.put(e.getKey(), List.copyOf(dst));
      }

      return fs;
   }
}
