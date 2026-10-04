package org.millenaire.content.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConverterOutputManifest {
   public static final String DEFAULT_FILENAME = "_conversion_manifest.json";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final DateTimeFormatter ISO_INSTANT_SECONDS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
   private final String converterVersion;
   private final List<ConverterOutputManifest.Entry> converted;

   public ConverterOutputManifest(String converterVersion) {
      this(converterVersion, new ArrayList<>());
   }

   public ConverterOutputManifest(String converterVersion, List<ConverterOutputManifest.Entry> converted) {
      this.converterVersion = converterVersion;
      this.converted = new ArrayList<>(converted);
   }

   public String converterVersion() {
      return this.converterVersion;
   }

   public List<ConverterOutputManifest.Entry> entries() {
      return List.copyOf(this.converted);
   }

   public void addEntry(ConverterOutputManifest.Entry entry) {
      this.converted.add(entry);
   }

   public ConverterOutputManifest.Entry recordFile(Path addonRoot, Path targetAbsolute, Path sourceAbsolute) throws IOException {
      String targetRel = addonRoot.relativize(targetAbsolute).toString().replace('\\', '/');
      String sourceRel = sourceAbsolute == null ? null : addonRoot.relativize(sourceAbsolute).toString().replace('\\', '/');
      String hash = sha256OfFile(targetAbsolute);
      String timestamp = ISO_INSTANT_SECONDS.format(Instant.now());
      ConverterOutputManifest.Entry entry = new ConverterOutputManifest.Entry(targetRel, sourceRel, timestamp, "sha256:" + hash);
      this.addEntry(entry);
      return entry;
   }

   public static String readVersion(Path path) {
      if (!Files.exists(path)) {
         return null;
      }

      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         if (json == null) {
            return null;
         } else {
            return json.has("converter_version") ? json.get("converter_version").getAsString() : null;
         }
      } catch (IOException e) {
         return null;
      }
   }

   public static ConverterOutputManifest readOrEmpty(Path path, String fallbackConverterVersion) throws IOException {
      if (!Files.exists(path)) {
         return new ConverterOutputManifest(fallbackConverterVersion);
      }

      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         JsonObject json = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         if (json == null) {
            return new ConverterOutputManifest(fallbackConverterVersion);
         }

         String version = json.has("converter_version") ? json.get("converter_version").getAsString() : fallbackConverterVersion;
         List<ConverterOutputManifest.Entry> entries = new ArrayList<>();
         if (json.has("converted") && json.get("converted").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("converted")) {
               if (el.isJsonObject()) {
                  JsonObject obj = el.getAsJsonObject();
                  String entryPath = obj.get("path").getAsString();
                  String entrySource = obj.has("source") && !obj.get("source").isJsonNull() ? obj.get("source").getAsString() : null;
                  String entryTimestamp = obj.get("timestamp").getAsString();
                  String entryHash = obj.get("hash").getAsString();
                  entries.add(new ConverterOutputManifest.Entry(entryPath, entrySource, entryTimestamp, entryHash));
               }
            }
         }

         return new ConverterOutputManifest(version, entries);
      }
   }

   public void writeAtomic(Path path) throws IOException {
      Path parent = path.getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("converter_version", this.converterVersion);
      List<Map<String, Object>> entries = new ArrayList<>();

      for (ConverterOutputManifest.Entry e : this.converted) {
         Map<String, Object> map = new LinkedHashMap<>();
         map.put("path", e.path());
         if (e.source() != null) {
            map.put("source", e.source());
         }

         map.put("timestamp", e.timestamp());
         map.put("hash", e.hash());
         entries.add(map);
      }

      payload.put("converted", entries);
      Path tmp = parent != null ? parent.resolve(path.getFileName().toString() + ".tmp") : Path.of(path.getFileName().toString() + ".tmp");

      try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
         GSON.toJson(payload, w);
      }

      try {
         Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
         moveWithBoundedRetry(tmp, path);
      } catch (AccessDeniedException e) {
         moveWithBoundedRetry(tmp, path);
      }
   }

   private static void moveWithBoundedRetry(Path source, Path target) throws IOException {
      IOException last = null;

      for (int attempt = 0; attempt < 5; attempt++) {
         try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
         } catch (AccessDeniedException e) {
            last = e;

            try {
               Thread.sleep(50L * (1L + attempt));
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               throw e;
            }
         }
      }

      throw last;
   }

   public static String sha256OfFile(Path target) throws IOException {
      MessageDigest md;
      try {
         md = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 not available on this JVM", e);
      }

      byte[] bytes = Files.readAllBytes(target);
      byte[] digest = md.digest(bytes);
      StringBuilder sb = new StringBuilder(digest.length * 2);

      for (byte b : digest) {
         sb.append(String.format("%02x", b & 255));
      }

      return sb.toString();
   }

   public record Entry(String path, String source, String timestamp, String hash) {
      public Entry(String path, String source, String timestamp, String hash) {
         if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
         }

         if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("timestamp must not be blank");
         }

         if (hash != null && hash.startsWith("sha256:")) {
            this.path = path;
            this.source = source;
            this.timestamp = timestamp;
            this.hash = hash;
         } else {
            throw new IllegalArgumentException("hash must start with \"sha256:\"");
         }
      }
   }
}
