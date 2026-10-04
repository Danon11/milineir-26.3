package org.millenaire.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricContentDeployerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void seedsMissingFilesAndPreservesExistingFiles() throws IOException {
        Path bundled = temporaryDirectory.resolve("bundled");
        Path destination = temporaryDirectory.resolve("mods/millenaire");
        Files.createDirectories(bundled.resolve("villages"));
        Files.writeString(bundled.resolve("existing.txt"), "bundled value");
        Files.writeString(bundled.resolve("villages/new.txt"), "new value");
        Files.createDirectories(destination);
        Files.writeString(destination.resolve("existing.txt"), "user value");

        int copied = FabricContentDeployer.mergeMissingFiles(bundled, destination);

        assertEquals(1, copied);
        assertEquals("user value", Files.readString(destination.resolve("existing.txt")));
        assertEquals("new value", Files.readString(destination.resolve("villages/new.txt")));
        assertTrue(Files.isDirectory(destination));
    }
}
