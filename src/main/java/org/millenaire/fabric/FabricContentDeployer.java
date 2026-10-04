package org.millenaire.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class FabricContentDeployer {
    private static final System.Logger LOGGER = System.getLogger("Millenaire");
    private static final List<String> CONTENT_ROOTS = List.of("millenaire", "millenaire-custom");

    private FabricContentDeployer() {
    }

    public static void deploy(ModContainer modContainer) {
        Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();

        for (String contentRoot : CONTENT_ROOTS) {
            String bundledPath = "todeploy/" + contentRoot;
            Path destination = modsDirectory.resolve(contentRoot).normalize();

            if (!destination.startsWith(modsDirectory)) {
                LOGGER.log(System.Logger.Level.ERROR, "Rejected content destination outside mods directory: " + destination);
                continue;
            }

            var sourcePath = modContainer.findPath(bundledPath);
            if (sourcePath.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG, "No bundled content found at " + bundledPath);
                continue;
            }

            try {
                int copiedFiles = mergeMissingFiles(sourcePath.get(), destination);
                LOGGER.log(System.Logger.Level.INFO, "Seeded " + copiedFiles + " Millenaire content files in " + destination);
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not seed Millenaire content from " + bundledPath, exception);
            }
        }
    }

    static int mergeMissingFiles(Path bundledRoot, Path destinationRoot) throws IOException {
        Path source = bundledRoot.toAbsolutePath().normalize();
        Path destination = destinationRoot.toAbsolutePath().normalize();

        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }

        Files.createDirectories(destination.getParent());
        ensureSafeDirectory(destination, destination);

        int copiedFiles = 0;
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path bundledFile : paths.toList()) {
                if (bundledFile.equals(source) || Files.isSymbolicLink(bundledFile)
                        || !Files.isRegularFile(bundledFile, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                Path relativePath = source.relativize(bundledFile);
                Path target = destination.resolve(relativePath).normalize();
                if (!target.startsWith(destination)) {
                    LOGGER.log(System.Logger.Level.WARNING, "Rejected bundled path outside destination: " + relativePath);
                    continue;
                }

                ensureSafeDirectory(destination, target.getParent());
                try {
                    Files.copy(bundledFile, target);
                    copiedFiles++;
                } catch (FileAlreadyExistsException ignored) {
                    // Existing user content is preserved.
                }
            }
        }

        return copiedFiles;
    }

    private static void ensureSafeDirectory(Path destinationRoot, Path directory) throws IOException {
        Path destination = destinationRoot.toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(destination)) {
            throw new IOException("Directory escapes content destination: " + target);
        }

        Path current = destination;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            verifyDirectory(current);
        } else {
            Files.createDirectory(current);
        }

        for (Path part : destination.relativize(target)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                verifyDirectory(current);
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static void verifyDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to write through a non-directory or symbolic link: " + directory);
        }
    }
}
