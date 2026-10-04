package org.millenaire.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public sealed interface Resource permits Resource.ClasspathResource, Resource.FileResource {
   String relPath();

   SourceLabel source();

   SourceKind kind();

   InputStream open() throws IOException;

   long size() throws IOException;

   Optional<Resource> sibling(String var1);

   static String siblingPath(String relPath, String name) {
      int slash = relPath.lastIndexOf(47);
      return slash < 0 ? name : relPath.substring(0, slash + 1) + name;
   }

   record ClasspathResource(String relPath, SourceLabel source, SourceKind kind, Path path, ContentFs owner) implements Resource {
      public ClasspathResource {
         if (relPath == null) {
            throw new IllegalArgumentException("relPath null");
         }

         if (source == null) {
            throw new IllegalArgumentException("source null");
         }

         if (kind == null) {
            throw new IllegalArgumentException("kind null");
         }

         if (path == null) {
            throw new IllegalArgumentException("path null");
         }

         if (owner == null) {
            throw new IllegalArgumentException("owner null");
         }
      }

      public InputStream open() throws IOException {
         return Files.newInputStream(this.path);
      }

      public long size() throws IOException {
         return Files.size(this.path);
      }

      public Optional<Resource> sibling(String name) {
         return this.owner.findFirst(Resource.siblingPath(this.relPath, name));
      }
   }

   record FileResource(String relPath, SourceLabel source, SourceKind kind, Path path, ContentFs owner) implements Resource {
      public FileResource {
         if (relPath == null) {
            throw new IllegalArgumentException("relPath null");
         }

         if (source == null) {
            throw new IllegalArgumentException("source null");
         }

         if (kind == null) {
            throw new IllegalArgumentException("kind null");
         }

         if (path == null) {
            throw new IllegalArgumentException("path null");
         }

         if (owner == null) {
            throw new IllegalArgumentException("owner null");
         }
      }

      public InputStream open() throws IOException {
         return Files.newInputStream(this.path);
      }

      public long size() throws IOException {
         return Files.size(this.path);
      }

      public Optional<Resource> sibling(String name) {
         return this.owner.findFirst(Resource.siblingPath(this.relPath, name));
      }
   }
}
