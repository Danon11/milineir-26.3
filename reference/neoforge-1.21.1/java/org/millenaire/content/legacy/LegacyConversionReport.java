package org.millenaire.content.legacy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class LegacyConversionReport {
   private final Map<String, Map<LegacyConversionReport.Kind, int[]>> perCulture = new TreeMap<>();
   private final Map<LegacyConversionReport.Kind, int[]> globalCounts = new LinkedHashMap<>();
   private final List<String> unmappedItems = new ArrayList<>();
   private final List<String> unmappedBiomes = new ArrayList<>();
   private final List<LegacyConversionReport.SkippedEntry> skipped = new ArrayList<>();
   private final Set<String> unmappedSpecialPoints = new LinkedHashSet<>();
   private final Map<Integer, LegacyConversionReport.UnmappedColour> unmappedColours = new LinkedHashMap<>();
   private final List<LegacyLayoutDetector.Normalisation> normalisations = new ArrayList<>();
   private int outOfScopeTxtCount = 0;
   private final List<LegacyConversionReport.UnknownKey> unknownKeys = new ArrayList<>();
   private final List<LegacyConversionReport.BrokenRef> brokenRefs = new ArrayList<>();
   private final List<LegacyConversionReport.InvalidValue> invalidValues = new ArrayList<>();
   private final List<LegacyConversionReport.DuplicateEntry> duplicates = new ArrayList<>();
   private final List<LegacyConversionReport.MissingRequiredKey> missingRequiredKeys = new ArrayList<>();
   private final List<LegacyConversionReport.MalformedRow> malformedRows = new ArrayList<>();
   private final List<LegacyConversionReport.UnparseableLine> unparseableLines = new ArrayList<>();
   private final List<LegacyConversionReport.EmptyList> emptyLists = new ArrayList<>();
   private final List<LegacyConversionReport.EmptyNamelist> emptyNamelists = new ArrayList<>();
   private final List<LegacyConversionReport.InvalidResourceLocation> invalidResourceLocations = new ArrayList<>();
   private final List<LegacyConversionReport.FilenameNormalised> filenameNormalisations = new ArrayList<>();
   private final List<LegacyConversionReport.QuestStructural> questStructurals = new ArrayList<>();
   private final List<LegacyConversionReport.MissingCulturePrefix> missingCulturePrefixes = new ArrayList<>();
   private final List<LegacyConversionReport.UnresolvedTag> unresolvedBuildingTags = new ArrayList<>();
   private final List<LegacyConversionReport.UnresolvedTag> unresolvedVillagerTags = new ArrayList<>();
   private final List<LegacyConversionReport.ChainGap> unreachableInputs = new ArrayList<>();
   private final List<LegacyConversionReport.ChainGap> orphanedOutputs = new ArrayList<>();
   private final List<LegacyConversionReport.SuspiciousPriority> suspiciousPriorities = new ArrayList<>();
   private final Clock clock;

   public LegacyConversionReport() {
      this(Clock.systemUTC());
   }

   public LegacyConversionReport(Clock clock) {
      this.clock = clock;
   }

   public void recordConverted(String culture, LegacyConversionReport.Kind kind) {
      this.counts(culture, kind)[0]++;
   }

   public void recordSkipped(String culture, LegacyConversionReport.Kind kind, String path, String reason) {
      this.recordSkipped(culture, kind, path, reason, null);
   }

   void recordSkipped(String culture, LegacyConversionReport.Kind kind, String path, String reason, String fixHint) {
      this.counts(culture, kind)[1]++;
      this.skipped.add(new LegacyConversionReport.SkippedEntry(culture, kind, path, reason, fixHint, false));
   }

   public void recordPreserved(String culture, LegacyConversionReport.Kind kind, String path, String reason) {
      this.counts(culture, kind)[1]++;
      this.skipped.add(new LegacyConversionReport.SkippedEntry(culture, kind, path, reason, null, true));
   }

   public void recordUnmappedItem(String culture, String legacyName) {
      this.unmappedItems.add((culture == null ? "<global>" : culture) + "::" + legacyName);
   }

   public void recordUnmappedBiome(String culture, String legacyName) {
      this.unmappedBiomes.add((culture == null ? "<global>" : culture) + "::" + legacyName);
   }

   public void recordOutOfScope(int n) {
      this.outOfScopeTxtCount += n;
   }

   public void recordUnmappedSpecialPoints(Collection<String> signatures) {
      this.unmappedSpecialPoints.addAll(signatures);
   }

   public Set<String> unmappedSpecialPoints() {
      return Collections.unmodifiableSet(this.unmappedSpecialPoints);
   }

   public void recordUnmappedColour(int rgb, String plan, String variant, int level) {
      LegacyConversionReport.UnmappedColour existing = this.unmappedColours.get(rgb);
      if (existing == null) {
         this.unmappedColours.put(rgb, new LegacyConversionReport.UnmappedColour(rgb, plan, variant, level, 1));
      } else {
         this.unmappedColours
            .put(rgb, new LegacyConversionReport.UnmappedColour(existing.rgb(), existing.plan(), existing.variant(), existing.level(), existing.count() + 1));
      }
   }

   public Collection<LegacyConversionReport.UnmappedColour> unmappedColours() {
      return Collections.unmodifiableCollection(this.unmappedColours.values());
   }

   public void recordNormalisation(LegacyLayoutDetector.Normalisation n) {
      if (n != null) {
         this.normalisations.add(n);
      }
   }

   public List<LegacyLayoutDetector.Normalisation> normalisations() {
      return Collections.unmodifiableList(this.normalisations);
   }

   public void recordUnknownKey(String culture, String filePath, String key) {
      for (LegacyConversionReport.UnknownKey existing : this.unknownKeys) {
         if (Objects.equals(existing.culture(), culture) && Objects.equals(existing.filePath(), filePath) && existing.key().equals(key)) {
            return;
         }
      }

      this.unknownKeys.add(new LegacyConversionReport.UnknownKey(culture, filePath, key));
   }

   public List<LegacyConversionReport.UnknownKey> unknownKeys() {
      return Collections.unmodifiableList(this.unknownKeys);
   }

   public void recordBrokenRef(String culture, String filePath, String key, String refValue, String refType) {
      this.brokenRefs.add(new LegacyConversionReport.BrokenRef(culture, filePath, key, refValue, refType));
   }

   public List<LegacyConversionReport.BrokenRef> brokenRefs() {
      return Collections.unmodifiableList(this.brokenRefs);
   }

   public void recordInvalidNumeric(String culture, String filePath, String key, String value, String expected) {
      this.invalidValues.add(new LegacyConversionReport.InvalidValue(culture, filePath, key, value, expected, "numeric"));
   }

   public void recordInvalidFormat(String culture, String filePath, String key, String value, String expectedFormat) {
      this.invalidValues.add(new LegacyConversionReport.InvalidValue(culture, filePath, key, value, expectedFormat, "format"));
   }

   public void recordInvalidEnum(String culture, String filePath, String key, String value, String allowedJoined) {
      this.invalidValues.add(new LegacyConversionReport.InvalidValue(culture, filePath, key, value, allowedJoined, "enum"));
   }

   public List<LegacyConversionReport.InvalidValue> invalidValues() {
      return Collections.unmodifiableList(this.invalidValues);
   }

   public void recordDuplicate(String culture, String filePath, String key, String value) {
      this.duplicates.add(new LegacyConversionReport.DuplicateEntry(culture, filePath, key, value));
   }

   public List<LegacyConversionReport.DuplicateEntry> duplicates() {
      return Collections.unmodifiableList(this.duplicates);
   }

   public void recordMissingRequiredKey(String culture, String filePath, String key) {
      this.missingRequiredKeys.add(new LegacyConversionReport.MissingRequiredKey(culture, filePath, key));
   }

   public List<LegacyConversionReport.MissingRequiredKey> missingRequiredKeys() {
      return Collections.unmodifiableList(this.missingRequiredKeys);
   }

   public void recordMalformedRow(String culture, String filePath, int line, String reason) {
      this.malformedRows.add(new LegacyConversionReport.MalformedRow(culture, filePath, line, reason));
   }

   public List<LegacyConversionReport.MalformedRow> malformedRows() {
      return Collections.unmodifiableList(this.malformedRows);
   }

   public void recordUnparseableLine(String culture, String filePath, int line, String content) {
      this.unparseableLines.add(new LegacyConversionReport.UnparseableLine(culture, filePath, line, content));
   }

   public List<LegacyConversionReport.UnparseableLine> unparseableLines() {
      return Collections.unmodifiableList(this.unparseableLines);
   }

   public void recordEmptyList(String culture, String filePath, String key) {
      this.emptyLists.add(new LegacyConversionReport.EmptyList(culture, filePath, key));
   }

   public List<LegacyConversionReport.EmptyList> emptyLists() {
      return Collections.unmodifiableList(this.emptyLists);
   }

   public void recordEmptyNamelist(String culture, String namelistName) {
      this.emptyNamelists.add(new LegacyConversionReport.EmptyNamelist(culture, namelistName));
   }

   public List<LegacyConversionReport.EmptyNamelist> emptyNamelists() {
      return Collections.unmodifiableList(this.emptyNamelists);
   }

   public void recordInvalidResourceLocation(String culture, String filePath, String invalidChars) {
      this.invalidResourceLocations.add(new LegacyConversionReport.InvalidResourceLocation(culture, filePath, invalidChars));
   }

   public List<LegacyConversionReport.InvalidResourceLocation> invalidResourceLocations() {
      return Collections.unmodifiableList(this.invalidResourceLocations);
   }

   public void recordFilenameNormalised(String culture, String filePath, String originalStem, String canonicalStem) {
      this.filenameNormalisations.add(new LegacyConversionReport.FilenameNormalised(culture, filePath, originalStem, canonicalStem));
   }

   public List<LegacyConversionReport.FilenameNormalised> filenameNormalisations() {
      return Collections.unmodifiableList(this.filenameNormalisations);
   }

   public void recordQuestStructural(String questKey, String filePath, int step, String problem) {
      this.questStructurals.add(new LegacyConversionReport.QuestStructural(questKey, filePath, step, problem));
   }

   public List<LegacyConversionReport.QuestStructural> questStructurals() {
      return Collections.unmodifiableList(this.questStructurals);
   }

   public void recordMissingCulturePrefix(String culture, String filePath, String key, String value) {
      this.missingCulturePrefixes.add(new LegacyConversionReport.MissingCulturePrefix(culture, filePath, key, value));
   }

   public List<LegacyConversionReport.MissingCulturePrefix> missingCulturePrefixes() {
      return Collections.unmodifiableList(this.missingCulturePrefixes);
   }

   public void recordUnresolvedBuildingTag(String culture, String tag, String requiredBy) {
      this.unresolvedBuildingTags.add(new LegacyConversionReport.UnresolvedTag(culture, tag, requiredBy));
   }

   public List<LegacyConversionReport.UnresolvedTag> unresolvedBuildingTags() {
      return Collections.unmodifiableList(this.unresolvedBuildingTags);
   }

   public void recordUnresolvedVillagerTag(String culture, String tag, String requiredBy) {
      this.unresolvedVillagerTags.add(new LegacyConversionReport.UnresolvedTag(culture, tag, requiredBy));
   }

   public List<LegacyConversionReport.UnresolvedTag> unresolvedVillagerTags() {
      return Collections.unmodifiableList(this.unresolvedVillagerTags);
   }

   public void recordUnreachableInput(String culture, String item, String requiredBy) {
      this.unreachableInputs.add(new LegacyConversionReport.ChainGap(culture, item, requiredBy));
   }

   public List<LegacyConversionReport.ChainGap> unreachableInputs() {
      return Collections.unmodifiableList(this.unreachableInputs);
   }

   public void recordOrphanedOutput(String culture, String item, String producedBy) {
      this.orphanedOutputs.add(new LegacyConversionReport.ChainGap(culture, item, producedBy));
   }

   public List<LegacyConversionReport.ChainGap> orphanedOutputs() {
      return Collections.unmodifiableList(this.orphanedOutputs);
   }

   public void recordSuspiciousPriority(String culture, String filePath, int level, int priority, String reason) {
      this.suspiciousPriorities.add(new LegacyConversionReport.SuspiciousPriority(culture, filePath, level, priority, reason));
   }

   public List<LegacyConversionReport.SuspiciousPriority> suspiciousPriorities() {
      return Collections.unmodifiableList(this.suspiciousPriorities);
   }

   public int totalConverted() {
      int total = 0;

      for (Map<LegacyConversionReport.Kind, int[]> m : this.perCulture.values()) {
         for (int[] pair : m.values()) {
            total += pair[0];
         }
      }

      for (int[] pair : this.globalCounts.values()) {
         total += pair[0];
      }

      return total;
   }

   public int totalSkipped() {
      return this.skipped.size();
   }

   public int totalOutOfScope() {
      return this.outOfScopeTxtCount;
   }

   public int cultureCount() {
      return this.perCulture.size();
   }

   public List<String> unmappedItems() {
      return Collections.unmodifiableList(this.unmappedItems);
   }

   public List<String> unmappedBiomes() {
      return Collections.unmodifiableList(this.unmappedBiomes);
   }

   public List<LegacyConversionReport.SkippedEntry> skipped() {
      return Collections.unmodifiableList(this.skipped);
   }

   public boolean hasBlockingAmbiguities() {
      for (LegacyConversionReport.SkippedEntry e : this.skipped) {
         if (e.fixHint() != null) {
            return true;
         }
      }

      return false;
   }

   public int totalPreserved() {
      int n = 0;

      for (LegacyConversionReport.SkippedEntry e : this.skipped) {
         if (e.preserved()) {
            n++;
         }
      }

      return n;
   }

   public String render() {
      StringBuilder sb = new StringBuilder();
      int converted = this.totalConverted();
      int skippedCount = this.totalSkipped();
      int unmapped = this.unmappedItems.size();
      if (this.hasBlockingAmbiguities()) {
         this.renderConvertHeader(sb);
      }

      sb.append("Millénaire converted ")
         .append(converted)
         .append(" legacy-format files from your millenaire-custom/ folder.")
         .append(" Your server should work normally after restart.\n\n");
      if (unmapped > 0) {
         sb.append(unmapped)
            .append(" item reference")
            .append(unmapped == 1 ? "" : "s")
            .append(" from the pack could not be mapped to modern Minecraft items.")
            .append(" These are usually items that came from a companion mod — install the")
            .append(" companion mod or ignore the warnings if you don't need those items.\n\n");
      }

      long skippedWorldMarvel = this.skipped
         .stream()
         .filter(sx -> sx.kind() == LegacyConversionReport.Kind.QUEST)
         .filter(sx -> sx.reason() != null && sx.reason().contains("is out of scope") && sx.reason().contains("family '"))
         .count();
      long skippedOtherQuests = this.skipped.stream().filter(sx -> sx.kind() == LegacyConversionReport.Kind.QUEST).count() - skippedWorldMarvel;
      if (skippedWorldMarvel > 0L) {
         sb.append(skippedWorldMarvel)
            .append(" world/marvel quest")
            .append(skippedWorldMarvel == 1L ? " was" : "s were")
            .append(" skipped. Those quest types are not yet supported and will not be")
            .append(" available to players.\n\n");
      }

      if (skippedOtherQuests > 0L) {
         sb.append(skippedOtherQuests)
            .append(" quest")
            .append(skippedOtherQuests == 1L ? " was" : "s were")
            .append(" skipped for other reasons (hand-maintained JSON preserved, parse error,")
            .append(" or missing family directory). See the Skipped files section below.\n\n");
      }

      if (this.outOfScopeTxtCount > 0) {
         sb.append(this.outOfScopeTxtCount)
            .append(" file(s) for out-of-scope features (walls, help, banners) were left")
            .append(" alone. These features are not yet ported to Millénaire 9.\n\n");
      }

      if (!this.unmappedSpecialPoints.isEmpty()) {
         int n = this.unmappedSpecialPoints.size();
         sb.append(n)
            .append(" special point type")
            .append(n == 1 ? " was" : "s were")
            .append(" dropped from building plans because Millénaire 9")
            .append(" doesn't recognise them (e.g. custom spawners or one-off markers).")
            .append(" The surrounding building still converts; only these points are lost.\n\n");
      }

      long nameCollisions = this.skipped.stream().filter(sx -> sx.reason() != null && sx.reason().startsWith("name collides with ")).count();
      if (nameCollisions > 0L) {
         sb.append(nameCollisions)
            .append(" file")
            .append(nameCollisions == 1L ? "" : "s")
            .append(" had name collisions (two TXTs that would produce the same JSON).")
            .append(" The first occurrence was kept and the duplicate skipped.")
            .append(" Rename one of the sources to resolve.\n\n");
      }

      sb.append("Technical details follow below.\n\n");
      sb.append("=== Per-culture summary ===\n");
      if (this.perCulture.isEmpty()) {
         sb.append("  (no culture-scoped content)\n");
      } else {
         for (Entry<String, Map<LegacyConversionReport.Kind, int[]>> e : this.perCulture.entrySet()) {
            sb.append("  ").append(e.getKey()).append(":\n");

            for (Entry<LegacyConversionReport.Kind, int[]> kc : e.getValue().entrySet()) {
               sb.append("    ").append(kc.getKey().display()).append(": ").append(kc.getValue()[0]).append(" converted");
               if (kc.getValue()[1] > 0) {
                  sb.append(", ").append(kc.getValue()[1]).append(" skipped");
               }

               sb.append('\n');
            }
         }
      }

      if (!this.globalCounts.isEmpty()) {
         sb.append("\n=== Global content ===\n");

         for (Entry<LegacyConversionReport.Kind, int[]> e : this.globalCounts.entrySet()) {
            sb.append("  ").append(e.getKey().display()).append(": ").append(e.getValue()[0]).append(" converted");
            if (e.getValue()[1] > 0) {
               sb.append(", ").append(e.getValue()[1]).append(" skipped");
            }

            sb.append('\n');
         }
      }

      if (this.hasBlockingAmbiguities()) {
         this.renderAmbiguitiesSection(sb);
      }

      if (!this.normalisations.isEmpty()) {
         sb.append("\n=== Normalisations ===\n");

         for (LegacyLayoutDetector.Normalisation n : this.normalisations) {
            String original = n.original().getFileName().toString();
            String canonical = n.canonical().getFileName().toString();
            switch (n.outcome()) {
               case RENAMED:
                  sb.append("Renamed culture directory '").append(original).append("' → '").append(canonical).append("'\n");
               case ALREADY_CANONICAL:
               default:
                  break;
               case REJECTED_NON_FOLDABLE:
                  sb.append("Rejected culture directory '").append(original).append("' (not case-foldable — rename manually)\n");
                  break;
               case REJECTED_COEXISTENCE:
                  sb.append("Rejected culture directory '").append(original).append("' (coexists with '").append(canonical).append("' — merge manually)\n");
            }
         }
      }

      if (!this.unmappedItems.isEmpty()) {
         sb.append("\n=== Unmapped items (").append(this.unmappedItems.size()).append(") ===\n");

         for (String s : this.unmappedItems) {
            sb.append("  ").append(s).append('\n');
         }
      }

      if (!this.unmappedBiomes.isEmpty()) {
         sb.append("\n=== Unmapped biomes (").append(this.unmappedBiomes.size()).append(") ===\n");

         for (String s : this.unmappedBiomes) {
            sb.append("  ").append(s).append('\n');
         }
      }

      if (!this.unmappedSpecialPoints.isEmpty()) {
         sb.append("\n=== Unmapped special-point types (").append(this.unmappedSpecialPoints.size()).append(") ===\n");

         for (String s : this.unmappedSpecialPoints) {
            sb.append("  ").append(s).append('\n');
         }
      }

      if (!this.skipped.isEmpty()) {
         sb.append("\n=== Skipped files (").append(skippedCount).append(") ===\n");

         for (LegacyConversionReport.SkippedEntry s : this.skipped) {
            sb.append("  [")
               .append(s.culture() == null ? "global" : s.culture())
               .append("] ")
               .append(s.kind().display())
               .append(" ")
               .append(s.path())
               .append(" — ")
               .append(s.reason())
               .append('\n');
         }
      }

      this.renderValidationSections(sb);
      return sb.toString();
   }

   private void renderValidationSections(StringBuilder sb) {
      if (!this.unknownKeys.isEmpty()) {
         sb.append("\n=== Unknown legacy keys (").append(this.unknownKeys.size()).append(") ===\n");

         for (LegacyConversionReport.UnknownKey u : this.unknownKeys) {
            sb.append("  [")
               .append(u.culture() == null ? "global" : u.culture())
               .append("] ")
               .append(u.filePath())
               .append(": '")
               .append(u.key())
               .append("' is not a recognised legacy key\n");
         }
      }

      if (!this.brokenRefs.isEmpty()) {
         sb.append("\n=== Broken cross-references (").append(this.brokenRefs.size()).append(") ===\n");

         for (LegacyConversionReport.BrokenRef b : this.brokenRefs) {
            sb.append("  [")
               .append(b.culture() == null ? "global" : b.culture())
               .append("] ")
               .append(b.filePath())
               .append(": ")
               .append(b.refType())
               .append(" '")
               .append(b.refValue())
               .append("' (in ")
               .append(b.key())
               .append(") not found\n");
         }
      }

      if (!this.invalidValues.isEmpty()) {
         sb.append("\n=== Invalid values (").append(this.invalidValues.size()).append(") ===\n");

         for (LegacyConversionReport.InvalidValue v : this.invalidValues) {
            sb.append("  [")
               .append(v.culture() == null ? "global" : v.culture())
               .append("] ")
               .append(v.filePath())
               .append(": '")
               .append(v.key())
               .append("=")
               .append(v.value())
               .append("' — expected ")
               .append(v.expected())
               .append(" (")
               .append(v.category())
               .append(")\n");
         }
      }

      if (!this.duplicates.isEmpty()) {
         sb.append("\n=== Duplicates (").append(this.duplicates.size()).append(") ===\n");

         for (LegacyConversionReport.DuplicateEntry d : this.duplicates) {
            sb.append("  [")
               .append(d.culture() == null ? "global" : d.culture())
               .append("] ")
               .append(d.filePath())
               .append(": '")
               .append(d.key())
               .append("' has duplicate entry '")
               .append(d.value())
               .append("'\n");
         }
      }

      if (!this.missingRequiredKeys.isEmpty()) {
         sb.append("\n=== Missing required keys (").append(this.missingRequiredKeys.size()).append(") ===\n");

         for (LegacyConversionReport.MissingRequiredKey m : this.missingRequiredKeys) {
            sb.append("  [")
               .append(m.culture() == null ? "global" : m.culture())
               .append("] ")
               .append(m.filePath())
               .append(": missing '")
               .append(m.key())
               .append("'\n");
         }
      }

      if (!this.malformedRows.isEmpty()) {
         sb.append("\n=== Malformed rows (").append(this.malformedRows.size()).append(") ===\n");

         for (LegacyConversionReport.MalformedRow r : this.malformedRows) {
            sb.append("  [").append(r.culture() == null ? "global" : r.culture()).append("] ").append(r.filePath());
            if (r.line() > 0) {
               sb.append(" line ").append(r.line());
            }

            sb.append(": ").append(r.reason()).append('\n');
         }
      }

      if (!this.unparseableLines.isEmpty()) {
         sb.append("\n=== Unparseable lines (").append(this.unparseableLines.size()).append(") ===\n");

         for (LegacyConversionReport.UnparseableLine u : this.unparseableLines) {
            sb.append("  [").append(u.culture() == null ? "global" : u.culture()).append("] ").append(u.filePath());
            if (u.line() > 0) {
               sb.append(" line ").append(u.line());
            }

            sb.append(": ").append(u.content()).append('\n');
         }
      }

      if (!this.emptyLists.isEmpty()) {
         sb.append("\n=== Empty lists (").append(this.emptyLists.size()).append(") ===\n");

         for (LegacyConversionReport.EmptyList e : this.emptyLists) {
            sb.append("  [")
               .append(e.culture() == null ? "global" : e.culture())
               .append("] ")
               .append(e.filePath())
               .append(": '")
               .append(e.key())
               .append("' is empty\n");
         }
      }

      if (!this.emptyNamelists.isEmpty()) {
         sb.append("\n=== Empty namelists (").append(this.emptyNamelists.size()).append(") ===\n");

         for (LegacyConversionReport.EmptyNamelist e : this.emptyNamelists) {
            sb.append("  [").append(e.culture() == null ? "global" : e.culture()).append("] ").append(e.namelistName()).append('\n');
         }
      }

      if (!this.invalidResourceLocations.isEmpty()) {
         sb.append("\n=== Invalid resource locations (").append(this.invalidResourceLocations.size()).append(") ===\n");

         for (LegacyConversionReport.InvalidResourceLocation r : this.invalidResourceLocations) {
            sb.append("  [")
               .append(r.culture() == null ? "global" : r.culture())
               .append("] ")
               .append(r.filePath())
               .append(": invalid characters: ")
               .append(r.invalidChars())
               .append('\n');
         }
      }

      if (!this.filenameNormalisations.isEmpty()) {
         sb.append("\n=== Filename normalisations (").append(this.filenameNormalisations.size()).append(") ===\n");

         for (LegacyConversionReport.FilenameNormalised n : this.filenameNormalisations) {
            sb.append("  [")
               .append(n.culture() == null ? "global" : n.culture())
               .append("] ")
               .append(n.filePath())
               .append(": '")
               .append(n.originalStem())
               .append("' → '")
               .append(n.canonicalStem())
               .append("'\n");
         }
      }

      if (!this.questStructurals.isEmpty()) {
         sb.append("\n=== Quest structural issues (").append(this.questStructurals.size()).append(") ===\n");

         for (LegacyConversionReport.QuestStructural q : this.questStructurals) {
            sb.append("  [quest ").append(q.questKey()).append("] ").append(q.filePath());
            if (q.step() > 0) {
               sb.append(" step ").append(q.step());
            }

            sb.append(": ").append(q.problem()).append('\n');
         }
      }

      if (!this.missingCulturePrefixes.isEmpty()) {
         sb.append("\n=== Villager refs missing culture prefix (").append(this.missingCulturePrefixes.size()).append(") ===\n");

         for (LegacyConversionReport.MissingCulturePrefix m : this.missingCulturePrefixes) {
            sb.append("  [")
               .append(m.culture() == null ? "global" : m.culture())
               .append("] ")
               .append(m.filePath())
               .append(": '")
               .append(m.key())
               .append("=")
               .append(m.value())
               .append("' missing 'culture/' prefix\n");
         }
      }

      if (!this.unresolvedBuildingTags.isEmpty()) {
         sb.append("\n=== Unresolved building tags (").append(this.unresolvedBuildingTags.size()).append(") ===\n");

         for (LegacyConversionReport.UnresolvedTag t : this.unresolvedBuildingTags) {
            sb.append("  [")
               .append(t.culture())
               .append("] tag '")
               .append(t.tag())
               .append("' required by ")
               .append(t.requiredBy())
               .append(" — no building sets it\n");
         }
      }

      if (!this.unresolvedVillagerTags.isEmpty()) {
         sb.append("\n=== Unresolved villager tags (").append(this.unresolvedVillagerTags.size()).append(") ===\n");

         for (LegacyConversionReport.UnresolvedTag t : this.unresolvedVillagerTags) {
            sb.append("  [")
               .append(t.culture())
               .append("] tag '")
               .append(t.tag())
               .append("' required by ")
               .append(t.requiredBy())
               .append(" — no villager sets it\n");
         }
      }

      if (!this.unreachableInputs.isEmpty()) {
         sb.append("\n=== Unreachable inputs (").append(this.unreachableInputs.size()).append(") ===\n");

         for (LegacyConversionReport.ChainGap g : this.unreachableInputs) {
            sb.append("  [")
               .append(g.culture())
               .append("] '")
               .append(g.item())
               .append("' needed by ")
               .append(g.reference())
               .append(" — not produced, bought, or in traded_goods\n");
         }
      }

      if (!this.orphanedOutputs.isEmpty()) {
         sb.append("\n=== Orphaned outputs (").append(this.orphanedOutputs.size()).append(") ===\n");

         for (LegacyConversionReport.ChainGap g : this.orphanedOutputs) {
            sb.append("  [")
               .append(g.culture())
               .append("] '")
               .append(g.item())
               .append("' produced by ")
               .append(g.reference())
               .append(" — never consumed, sold, or required\n");
         }
      }

      if (!this.suspiciousPriorities.isEmpty()) {
         sb.append("\n=== Suspicious priorities (").append(this.suspiciousPriorities.size()).append(") ===\n");

         for (LegacyConversionReport.SuspiciousPriority p : this.suspiciousPriorities) {
            sb.append("  [")
               .append(p.culture() == null ? "global" : p.culture())
               .append("] ")
               .append(p.filePath())
               .append(" level ")
               .append(p.level())
               .append(" prio=")
               .append(p.priority())
               .append(": ")
               .append(p.reason())
               .append('\n');
         }
      }
   }

   private void renderConvertHeader(StringBuilder sb) {
      int itemCount = 0;
      int biomeCount = 0;
      int colourCount = this.unmappedColours.size();
      int otherCount = 0;

      for (LegacyConversionReport.SkippedEntry e : this.skipped) {
         if (e.fixHint() != null) {
            String reason = e.reason() == null ? "" : e.reason();
            if (reason.startsWith("unmapped item ")) {
               itemCount++;
            } else if (reason.startsWith("unknown biome ")) {
               biomeCount++;
            } else if (!reason.startsWith("unknown colour ")) {
               otherCount++;
            }
         }
      }

      sb.append("CONVERT mode: this pack is NOT ready to ship.\n\n");
      sb.append("  - ").append(itemCount).append(" unmapped item reference(s) — see _conversion_unmapped.itemlist.txt\n");
      sb.append("  - ").append(colourCount).append(" unknown PNG colour(s)     — see _conversion_unmapped.blocklist.txt\n");
      sb.append("  - ").append(biomeCount).append(" unmapped biome(s)         — see _conversion_unmapped.biome_map.json\n");
      sb.append("  - ").append(otherCount).append(" other ambiguity(ies)      — see \"Ambiguities requiring action\" below\n\n");
      sb.append("Fix the entries in the three stub files above by filling in their\n");
      sb.append("TODO placeholders, copy them into the real config files, then rerun\n");
      sb.append("/millenaire dev convert-addon. Repeat until all stubs are empty.\n\n");
      sb.append("Separately:\n\n");
   }

   private void renderAmbiguitiesSection(StringBuilder sb) {
      sb.append("\n=== Ambiguities requiring action ===\n");

      for (LegacyConversionReport.SkippedEntry e : this.skipped) {
         if (e.fixHint() != null) {
            sb.append("  [")
               .append(e.culture() == null ? "global" : e.culture())
               .append("] ")
               .append(e.kind().display())
               .append(" ")
               .append(e.path())
               .append('\n');
            sb.append("    reason:   ").append(e.reason()).append('\n');
            sb.append("    fix hint: ").append(e.fixHint()).append('\n');
         }
      }
   }

   public String oneLineSummary() {
      return String.format(Locale.ROOT, "Millénaire: legacy content auto-converted (%d files, %d cultures).", this.totalConverted(), this.cultureCount());
   }

   private int[] counts(String culture, LegacyConversionReport.Kind kind) {
      Map<LegacyConversionReport.Kind, int[]> target = culture == null
         ? this.globalCounts
         : this.perCulture.computeIfAbsent(culture, k -> new LinkedHashMap<>());
      return target.computeIfAbsent(kind, k -> new int[]{0, 0});
   }

   public record BrokenRef(String culture, String filePath, String key, String refValue, String refType) {
   }

   public record ChainGap(String culture, String item, String reference) {
   }

   public record DuplicateEntry(String culture, String filePath, String key, String value) {
   }

   public record EmptyList(String culture, String filePath, String key) {
   }

   public record EmptyNamelist(String culture, String namelistName) {
   }

   public record FilenameNormalised(String culture, String filePath, String originalStem, String canonicalStem) {
   }

   public record InvalidResourceLocation(String culture, String filePath, String invalidChars) {
   }

   public record InvalidValue(String culture, String filePath, String key, String value, String expected, String category) {
   }

   public enum Kind {
      BUILDING_PLAN("building plan"),
      VILLAGER_TYPE("villager type"),
      VILLAGE_TYPE("village type"),
      SHOP("shop"),
      TRADED_GOOD("traded good"),
      CULTURE("culture"),
      GATHERING_TYPE("gathering type"),
      QUEST("quest");

      private final String display;

      Kind(String display) {
         this.display = display;
      }

      public String display() {
         return this.display;
      }
   }

   public record MalformedRow(String culture, String filePath, int line, String reason) {
   }

   public record MissingCulturePrefix(String culture, String filePath, String key, String value) {
   }

   public record MissingRequiredKey(String culture, String filePath, String key) {
   }

   public record QuestStructural(String questKey, String filePath, int step, String problem) {
   }

   public record SkippedEntry(String culture, LegacyConversionReport.Kind kind, String path, String reason, String fixHint, boolean preserved) {
   }

   public record SuspiciousPriority(String culture, String filePath, int level, int priority, String reason) {
   }

   public record UnknownKey(String culture, String filePath, String key) {
   }

   public record UnmappedColour(int rgb, String plan, String variant, int level, int count) {
   }

   public record UnparseableLine(String culture, String filePath, int line, String content) {
   }

   public record UnresolvedTag(String culture, String tag, String requiredBy) {
   }
}
