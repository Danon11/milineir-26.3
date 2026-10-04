package org.millenaire.content;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;

public final class CustomContentIndex {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final List<String> CONTENT_TYPE_DIRS = List.of("cultures", "languages", "gathering_type", "quests");
   private static final Set<String> LEGACY_CONTENT_DIRS = Set.copyOf(CONTENT_TYPE_DIRS);
   private static final Set<String> SILENT_RESERVED_DIRS = Set.of("exports", "exported");
   private static final String EXPORTED_DIR = "exported";
   private static final String CONVERTED_SUFFIX = "_converted";
   private static final Pattern SUBMOD_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
   private static volatile CustomContentIndex current;
   private static boolean warnedLazy;
   private static final Set<String> warnedLegacyNames = new HashSet<>();
   private static final Set<String> warnedInvalidSubmodNames = new HashSet<>();
   private static final Set<String> warnedExportsHasCultures = new HashSet<>();
   private static final Set<String> warnedDroppedAtCap = new HashSet<>();
   private static final Set<String> warnedPackIdCollision = new HashSet<>();
   private final List<SubmodRoot> roots;
   private final Map<String, List<SubmodRoot>> rootsPerCulture;
   private final Map<String, SubmodRoot> customCultureOwners;
   private final Map<String, List<SubmodRoot>> rootsByContentType;
   private final ContentFs contentFs;
   private final ContentFs exportedContentFs;

   private CustomContentIndex(
      List<SubmodRoot> roots,
      Map<String, List<SubmodRoot>> rootsPerCulture,
      Map<String, SubmodRoot> customCultureOwners,
      Map<String, List<SubmodRoot>> rootsByContentType,
      ContentFs contentFs,
      ContentFs exportedContentFs
   ) {
      this.roots = List.copyOf(roots);
      this.rootsPerCulture = Collections.unmodifiableMap(rootsPerCulture);
      this.customCultureOwners = Collections.unmodifiableMap(customCultureOwners);
      Map<String, List<SubmodRoot>> frozen = rootsByContentType.entrySet()
         .stream()
         .collect(Collectors.toUnmodifiableMap(Entry::getKey, e -> List.copyOf(e.getValue())));
      this.rootsByContentType = frozen;
      this.contentFs = contentFs != null ? contentFs : new OverlayBuilder().build();
      this.exportedContentFs = exportedContentFs != null ? exportedContentFs : new OverlayBuilder().build();
   }

   public static CustomContentIndex empty() {
      return new CustomContentIndex(List.of(), Map.of(), Map.of(), Map.of(), new OverlayBuilder().build(), new OverlayBuilder().build());
   }

   public static CustomContentIndex jarOnly(Path classpathRoot) {
      OverlayBuilder overlayBuilder = new OverlayBuilder();
      if (classpathRoot != null) {
         overlayBuilder.addLayer(ClasspathLayer.scan(classpathRoot));
      }

      ContentFs contentFs = overlayBuilder.build();
      return new CustomContentIndex(List.of(), Map.of(), Map.of(), Map.of(), contentFs, new OverlayBuilder().build());
   }

   public static CustomContentIndex jarOnly() {
      return jarOnly(ClasspathLayer.resolveDefaultRoot());
   }

   public static CustomContentIndex rebuild(Collection<String> builtInCultures) {
      return rebuild(builtInCultures, false);
   }

   public static CustomContentIndex rebuild(Collection<String> builtInCultures, boolean silent) {
      CustomContentIndex index = build(builtInCultures, silent);
      current = index;
      return index;
   }

   public static CustomContentIndex current() {
      CustomContentIndex snapshot = current;
      if (snapshot != null) {
         return snapshot;
      }

      synchronized (CustomContentIndex.class) {
         snapshot = current;
         if (snapshot != null) {
            return snapshot;
         }

         if (!warnedLazy) {
            warnedLazy = true;
            LOGGER.warn(
               "CustomContentIndex.current() called before rebuild(); performing lazy rebuild with BuiltInCultures.IDS. If this fires on the server side, it's a lifecycle bug."
            );
         }

         return !ContentDirectoryManager.isInitialized() ? empty() : rebuild(BuiltInCultures.IDS);
      }
   }

   public static void resetForTesting() {
      synchronized (CustomContentIndex.class) {
         current = null;
         warnedLazy = false;
         warnedLegacyNames.clear();
         warnedInvalidSubmodNames.clear();
         warnedExportsHasCultures.clear();
         warnedDroppedAtCap.clear();
         warnedPackIdCollision.clear();
      }
   }

   public List<SubmodRoot> roots() {
      return this.roots;
   }

   public List<SubmodRoot> rootsForCulture(String culture) {
      return this.rootsPerCulture.getOrDefault(culture, List.of());
   }

   public List<SubmodRoot> rootsWithLanguages() {
      return this.rootsByContentType.getOrDefault("languages", List.of());
   }

   public List<SubmodRoot> rootsWithGatheringType() {
      return this.rootsByContentType.getOrDefault("gathering_type", List.of());
   }

   public List<SubmodRoot> rootsWithQuests() {
      return this.rootsByContentType.getOrDefault("quests", List.of());
   }

   public Set<String> customCultureIds() {
      return this.customCultureOwners.keySet();
   }

   public Set<String> indexedCultures() {
      return this.rootsPerCulture.keySet();
   }

   public ContentFs root() {
      return this.contentFs;
   }

   public ContentFs exportedFs() {
      return this.exportedContentFs;
   }

   public ContentFs forCulture(String culture) {
      if (culture == null) {
         throw new IllegalArgumentException("culture is null");
      } else {
         return this.contentFs.sub("cultures/" + culture);
      }
   }

   public ContentFs forGlobalContent(String contentType) {
      if (contentType == null) {
         throw new IllegalArgumentException("contentType is null");
      } else {
         return this.contentFs.sub(contentType);
      }
   }

   private static CustomContentIndex build(Collection<String> builtInCultures) {
      return build(builtInCultures, false);
   }

   private static CustomContentIndex build(Collection<String> builtInCultures, boolean silent) {
      if (!ContentDirectoryManager.isInitialized()) {
         OverlayBuilder ob = new OverlayBuilder(silent);
         Path jarRoot = ClasspathLayer.resolveDefaultRoot();
         if (jarRoot != null) {
            ob.addLayer(ClasspathLayer.scan(jarRoot));
         }

         return new CustomContentIndex(List.of(), Map.of(), Map.of(), Map.of(), ob.build(), new OverlayBuilder().build());
      } else {
         Path customDir = ContentDirectoryManager.getCustomDir();
         Set<String> builtInLower = lowercaseSet(builtInCultures);
         List<SubmodRoot> roots = new ArrayList<>();
         Map<String, List<SubmodRoot>> rootsPerCulture = new TreeMap<>();
         Map<String, SubmodRoot> customCultureOwners = new TreeMap<>();
         Map<String, List<SubmodRoot>> rootsByContentType = new HashMap<>();
         Set<String> converted = collectConvertedNames(customDir);

         for (String name : listDirectChildDirs(customDir)) {
            if (LEGACY_CONTENT_DIRS.contains(name)) {
               warnLegacyLayoutOnce(customDir, name);
            } else if (SILENT_RESERVED_DIRS.contains(name)) {
               warnIfMisplacedExportLayoutOnce(customDir, name);
            } else if (!SUBMOD_NAME_PATTERN.matcher(name).matches()) {
               warnInvalidSubmodNameOnce(name);
            } else if (!name.endsWith("_converted") && converted.contains(name + "_converted")) {
               LOGGER.info("Ignoring legacy source sub-mod '{}' — converted sibling '{}' present", name, name + "_converted");
            } else {
               Path submodPath = customDir.resolve(name);
               if (qualifiesAsSubmod(submodPath)) {
                  registerRoot(new SubmodRoot(name, submodPath), builtInLower, roots, rootsPerCulture, customCultureOwners, rootsByContentType);
               }
            }
         }

         if (customCultureOwners.size() > 50) {
            int over = customCultureOwners.size() - 50;
            List<String> ordered = new ArrayList<>(customCultureOwners.keySet());
            List<String> droppedNames = ordered.subList(50, ordered.size());
            boolean firstHeader;
            synchronized (CustomContentIndex.class) {
               firstHeader = warnedDroppedAtCap.add("__header__");
            }

            if (firstHeader) {
               LOGGER.warn(
                  "Discovered {} custom cultures; capping at MAX_CUSTOM_CULTURES={} and dropping {} (alpha-last) cultures",
                  new Object[]{customCultureOwners.size(), 50, over}
               );
            }

            for (String dropped : droppedNames) {
               customCultureOwners.remove(dropped);
               rootsPerCulture.remove(dropped);
               boolean firstForName;
               synchronized (CustomContentIndex.class) {
                  firstForName = warnedDroppedAtCap.add(dropped);
               }

               if (firstForName) {
                  LOGGER.warn("  dropped custom culture beyond cap: '{}'", dropped);
               }
            }
         }

         Map<SubmodRoot, Set<String>> rejectedByRoot = rejectPackIdCollisions(rootsPerCulture, customCultureOwners);
         OverlayBuilder overlayBuilder = new OverlayBuilder(silent);
         Path classpathRoot = ClasspathLayer.resolveDefaultRoot();
         if (classpathRoot != null) {
            overlayBuilder.addLayer(ClasspathLayer.scan(classpathRoot));
         }

         overlayBuilder.addLayer(StandardLayer.scan(ContentDirectoryManager.getStandardDir()));

         for (SubmodRoot root : roots) {
            List<LayerEntry> entries = SubmodLayer.scanIncludingOversize(root.root(), SubmodLayer.labelFor(root.name()));
            Set<String> rejectedCultures = rejectedByRoot.get(root);
            if (rejectedCultures != null && !rejectedCultures.isEmpty()) {
               entries = filterRejectedCulturePaths(entries, rejectedCultures);
            }

            overlayBuilder.addSubmodLayer(entries);
         }

         ContentFs contentFs = overlayBuilder.build();
         ContentFs exportedFs = buildExportedFs(customDir, silent);
         return new CustomContentIndex(roots, rootsPerCulture, customCultureOwners, rootsByContentType, contentFs, exportedFs);
      }
   }

   private static ContentFs buildExportedFs(Path customDir, boolean silent) {
      OverlayBuilder builder = new OverlayBuilder(silent);
      Path exportedDir = customDir.resolve("exported");
      if (Files.isDirectory(exportedDir)) {
         builder.addSubmodLayer(SubmodLayer.scanIncludingOversize(exportedDir, SubmodLayer.labelFor("exported")));
      }

      return builder.build();
   }

   private static void warnLegacyLayoutOnce(Path customDir, String name) {
      synchronized (CustomContentIndex.class) {
         if (!warnedLegacyNames.add(name)) {
            return;
         }
      }

      LOGGER.warn(
         "Ignoring legacy flat-root directory '{}/{}/' — wrap your content in a sub-mod directory (e.g. millenaire-custom/my-pack/{}/...).",
         new Object[]{customDir.getFileName(), name, name}
      );
   }

   private static void warnInvalidSubmodNameOnce(String name) {
      synchronized (CustomContentIndex.class) {
         if (!warnedInvalidSubmodNames.add(name)) {
            return;
         }
      }

      LOGGER.warn(
         "Ignoring sub-mod directory '{}' — name must match {} so it can be combined with culture ids without ambiguity.", name, SUBMOD_NAME_PATTERN.pattern()
      );
   }

   private static void warnIfMisplacedExportLayoutOnce(Path customDir, String name) {
      if ("exports".equals(name)) {
         Path culturesDir = customDir.resolve(name).resolve("cultures");
         if (Files.isDirectory(culturesDir)) {
            synchronized (CustomContentIndex.class) {
               if (!warnedExportsHasCultures.add(name)) {
                  return;
               }
            }

            LOGGER.warn(
               "Found '{}/{}/cultures/' — '{}/' is the legacy flat Import Table sink ('{}/<id>.json'). Files under '{}/cultures/' are NOT overlaid into the runtime; move them into a regular sub-mod (e.g. 'millenaire-custom/my-pack/cultures/<c>/...') to become active.",
               new Object[]{customDir.getFileName(), name, name, name, name}
            );
         }
      }
   }

   private static boolean qualifiesAsSubmod(Path root) {
      for (String type : CONTENT_TYPE_DIRS) {
         if (Files.isDirectory(root.resolve(type))) {
            return true;
         }
      }

      return false;
   }

   private static void registerRoot(
      SubmodRoot root,
      Set<String> builtInLower,
      List<SubmodRoot> roots,
      Map<String, List<SubmodRoot>> rootsPerCulture,
      Map<String, SubmodRoot> customCultureOwners,
      Map<String, List<SubmodRoot>> rootsByContentType
   ) {
      roots.add(root);
      Path culturesDir = root.root().resolve("cultures");
      if (Files.isDirectory(culturesDir)) {
         for (String culture : listDirectChildDirs(culturesDir)) {
            if (!CultureIdPolicy.PATTERN.matcher(culture).matches()) {
               LOGGER.warn("Ignoring culture folder with invalid id '{}' in sub-mod '{}'", culture, root.displayName());
            } else {
               rootsPerCulture.computeIfAbsent(culture, k -> new ArrayList<>()).add(root);
               if (!builtInLower.contains(culture.toLowerCase(Locale.ROOT))) {
                  SubmodRoot existing = customCultureOwners.putIfAbsent(culture, root);
                  if (existing != null) {
                     LOGGER.info(
                        "Custom culture '{}' declared by multiple sub-mods — primary owner '{}', additional contributor '{}'",
                        new Object[]{culture, existing.displayName(), root.displayName()}
                     );
                  }
               }
            }
         }
      }

      for (String contentType : CONTENT_TYPE_DIRS) {
         if (!"cultures".equals(contentType) && Files.isDirectory(root.root().resolve(contentType))) {
            rootsByContentType.computeIfAbsent(contentType, k -> new ArrayList<>()).add(root);
         }
      }
   }

   private static String buildPackId(String submodName, String culture) {
      return "millenaire_custom_" + submodName + "_" + culture;
   }

   private static Map<SubmodRoot, Set<String>> rejectPackIdCollisions(
      Map<String, List<SubmodRoot>> rootsPerCulture, Map<String, SubmodRoot> customCultureOwners
   ) {
      Map<String, List<CustomContentIndex.PackIdEntry>> byPackId = new TreeMap<>();

      for (Entry<String, List<SubmodRoot>> entry : rootsPerCulture.entrySet()) {
         String culture = entry.getKey();

         for (SubmodRoot submod : entry.getValue()) {
            String packId = buildPackId(submod.name(), culture);
            byPackId.computeIfAbsent(packId, k -> new ArrayList<>()).add(new CustomContentIndex.PackIdEntry(submod, culture));
         }
      }

      Map<SubmodRoot, Set<String>> rejectedByRoot = new HashMap<>();

      for (Entry<String, List<CustomContentIndex.PackIdEntry>> e : byPackId.entrySet()) {
         List<CustomContentIndex.PackIdEntry> participants = e.getValue();
         if (participants.size() > 1) {
            String packId = e.getKey();
            boolean firstWarn;
            synchronized (CustomContentIndex.class) {
               firstWarn = warnedPackIdCollision.add(packId);
            }

            if (firstWarn) {
               StringBuilder rendered = new StringBuilder();

               for (CustomContentIndex.PackIdEntry p : participants) {
                  if (rendered.length() > 0) {
                     rendered.append(", ");
                  }

                  rendered.append("(submod='").append(p.submod().name()).append("', culture='").append(p.culture()).append("')");
               }

               LOGGER.warn(
                  "Pack-id collision '{}' — rejecting {} participants: {}. Rename a sub-mod or culture to disambiguate.",
                  new Object[]{packId, participants.size(), rendered}
               );
            }

            for (CustomContentIndex.PackIdEntry p : participants) {
               rejectedByRoot.computeIfAbsent(p.submod(), k -> new HashSet<>()).add(p.culture());
               List<SubmodRoot> contributors = rootsPerCulture.get(p.culture());
               if (contributors != null) {
                  contributors.remove(p.submod());
                  if (contributors.isEmpty()) {
                     rootsPerCulture.remove(p.culture());
                  }
               }

               SubmodRoot owner = customCultureOwners.get(p.culture());
               if (owner != null && owner.equals(p.submod())) {
                  List<SubmodRoot> remaining = rootsPerCulture.get(p.culture());
                  if (remaining != null && !remaining.isEmpty()) {
                     customCultureOwners.put(p.culture(), remaining.get(0));
                  } else {
                     customCultureOwners.remove(p.culture());
                  }
               }
            }
         }
      }

      return rejectedByRoot;
   }

   private static List<LayerEntry> filterRejectedCulturePaths(List<LayerEntry> entries, Set<String> rejectedCultures) {
      List<String> prefixes = new ArrayList<>(rejectedCultures.size());

      for (String c : rejectedCultures) {
         prefixes.add(("cultures/" + c + "/").toLowerCase(Locale.ROOT));
      }

      List<LayerEntry> kept = new ArrayList<>(entries.size());

      for (LayerEntry e : entries) {
         String rel = e.relPath();
         boolean drop = false;

         for (String prefix : prefixes) {
            if (rel.startsWith(prefix)) {
               drop = true;
               break;
            }
         }

         if (!drop) {
            kept.add(e);
         }
      }

      return kept;
   }

   private static Set<String> collectConvertedNames(Path customDir) {
      Set<String> out = new HashSet<>();

      try (Stream<Path> stream = Files.list(customDir)) {
         stream.filter(x$0 -> Files.isDirectory(x$0)).forEach(p -> {
            String name = p.getFileName().toString();
            if (name.endsWith("_converted")) {
               out.add(name);
            }
         });
      } catch (IOException e) {
         LOGGER.error("Failed to scan custom dir for converted sub-mods: {}", e.getMessage(), e);
      }

      return out;
   }

   private static List<String> listDirectChildDirs(Path dir) {
      List<String> names = new ArrayList<>();

      try (Stream<Path> stream = Files.list(dir)) {
         stream.filter(x$0 -> Files.isDirectory(x$0)).map(p -> p.getFileName().toString()).forEach(names::add);
      } catch (IOException e) {
         LOGGER.error("Failed to list direct children of {}: {}", new Object[]{dir, e.getMessage(), e});
         return List.of();
      }

      names.sort(String::compareTo);
      return names;
   }

   private static Set<String> lowercaseSet(Collection<String> values) {
      Set<String> out = new TreeSet<>();
      if (values == null) {
         return out;
      }

      for (String v : values) {
         if (v != null) {
            out.add(v.toLowerCase(Locale.ROOT));
         }
      }

      return out;
   }

   private record PackIdEntry(SubmodRoot submod, String culture) {
   }
}
