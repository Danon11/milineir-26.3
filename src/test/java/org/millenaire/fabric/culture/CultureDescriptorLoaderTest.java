package org.millenaire.fabric.culture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultureDescriptorLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsNormanCultureAndRetainsRepeatedFieldsInOrder() throws IOException {
        Path gameDirectory = gameDirectoryWithCulture("norman");

        CultureDescriptor norman = CultureDescriptorLoader.load(gameDirectory, "norman");

        assertEquals("norman", norman.id());
        assertEquals(List.of("sapling_appletree"), norman.values("knownCrop"));
        assertEquals(List.of("villager", "lonevillager", "visitor", "leader", "marvelvillager"),
                norman.values("travelBookVillagerCategory"));
        assertEquals(List.of("normanshovel", "purse", "normanhelmet", "sapling", "rosette"),
                norman.values("travelBookCategoryIcon").subList(0, 5).stream()
                        .map(value -> value.substring(value.indexOf(',') + 1)).toList());
    }

    @Test
    void loadsJapaneseCultureAndKeepsItsDistinctRepeatedValues() throws IOException {
        Path gameDirectory = gameDirectoryWithCulture("japanese");

        CultureDescriptor japanese = CultureDescriptorLoader.load(gameDirectory, "japanese");

        assertEquals(List.of("rice", "sapling_sakura"), japanese.values("knownCrop"));
        assertEquals("sake", japanese.firstValue("icon").orElseThrow());
        assertEquals(16, japanese.values("travelBookCategoryIcon").size());
        assertTrue(japanese.values("travelBookCategoryIcon").contains("weapons,tachisword"));
    }

    @Test
    void loadAllSortsCultureDirectoriesAndRejectsInvalidCultureIds() throws IOException {
        Path gameDirectory = gameDirectoryWithCulture("japanese");
        copyDescriptor("norman", culturesDirectory(gameDirectory).resolve("norman/culture.txt"));

        assertEquals(List.of("japanese", "norman"), CultureDescriptorLoader.loadAll(gameDirectory)
                .stream().map(CultureDescriptor::id).toList());
        assertThrows(IllegalArgumentException.class,
                () -> CultureDescriptorLoader.load(gameDirectory, "../norman"));
        assertThrows(IllegalArgumentException.class,
                () -> CultureDescriptorLoader.validateCultureId("Norman"));
    }

    private Path gameDirectoryWithCulture(String cultureId) throws IOException {
        Path gameDirectory = temporaryDirectory.resolve("game");
        copyDescriptor(cultureId, culturesDirectory(gameDirectory).resolve(cultureId).resolve("culture.txt"));
        return gameDirectory;
    }

    private Path culturesDirectory(Path gameDirectory) {
        return gameDirectory.resolve("mods/millenaire/cultures");
    }

    private void copyDescriptor(String cultureId, Path destination) throws IOException {
        String resource = "todeploy/millenaire/cultures/" + cultureId + "/culture.txt";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing test descriptor resource: " + resource);
            }
            Files.createDirectories(destination.getParent());
            Files.copy(input, destination);
        }
    }
}
