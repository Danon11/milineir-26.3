package org.millenaire.fabric.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCatalogLoaderTest {
    @TempDir Path temp;

    @Test
    void reproducesLegacyRotationOffsetsForNonSquarePlans() {
        var plan = new LegacyBuildingPlan("norman", "demo", 'A', 0, 2, 3, -1, temp.resolve("unused.png"), java.util.Map.of());
        assertEquals(new LegacyBuildingPlan.Position(9, 20, 29), plan.worldPosition(10, 20, 30, 0, 1, 0, 0));
        assertEquals(new LegacyBuildingPlan.Position(9, 20, 30), plan.worldPosition(10, 20, 30, 0, 1, 0, 1));
        assertEquals(new LegacyBuildingPlan.Position(10, 20, 30), plan.worldPosition(10, 20, 30, 0, 1, 0, 2));
        assertEquals(new LegacyBuildingPlan.Position(10, 20, 29), plan.worldPosition(10, 20, 30, 0, 1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> plan.worldPosition(0, 0, 0, 0, 0, 0, 4));
    }

    @Test
    void acceptsLegacyEncodingsWithoutLosingWesternAccents() throws Exception {
        Path latin = temp.resolve("latin.txt");
        Files.write(latin, "name=Château\n".getBytes(java.nio.charset.Charset.forName("windows-1252")));
        var document = LegacyDocument.read(latin, "latin");
        assertEquals("windows-1252", document.encoding());
        assertEquals("Château", document.first("name", ""));
        Path unicode = temp.resolve("unicode.txt");
        Files.write(unicode, "name=日本\n".getBytes(java.nio.charset.StandardCharsets.UTF_16));
        assertEquals("日本", LegacyDocument.read(unicode, "unicode").first("name", ""));
    }

    @Test
    void preservesRepeatedFieldsQuestOrderAndCustomOverrides() throws Exception {
        Path base = temp.resolve("base"), custom = temp.resolve("custom");
        write(base.resolve("cultures/norman/villagers/worker.txt"), "goal=rest\ngoal=work\ntexture=one\ntexture=two\n");
        write(base.resolve("quests/example.txt"), "step:new\nlabel:first\nstep:new\nlabel:second\n");
        write(custom.resolve("cultures/norman/villagers/worker.txt"), "goal=customwork\nnative_name=Custom\n");
        var catalog = LegacyCatalogLoader.load(base, custom);
        assertEquals(List.of("customwork"), catalog.cultures().get("norman").documents().get("villagers/worker.txt").values("goal"));
        assertEquals(List.of("step:new", "label:first", "step:new", "label:second"), catalog.globalDocuments().get("quests/example.txt").lines());
        assertThrows(UnsupportedOperationException.class, () -> catalog.cultures().clear());
    }

    @Test
    void decodesMirroredFloorsSkipsSeparatorsAndTreatsTransparencyAsEmpty() throws Exception {
        Path root = temp.resolve("base");
        write(root.resolve("blocklist.txt"), "empty;;0;;255/255/255;;;0\nred;minecraft:stone;0;false;255/0/0\nsleepingPos;;0;;0/0/255;;;0\n");
        Path metadata = root.resolve("cultures/norman/buildings/demo_A.txt");
        write(metadata, "building.width=2\nbuilding.length=1\ninitial.startlevel=-1\ninitial.tag=home\ninitial.tag=work\nupgrade1.priority=50\nupgrade1.tag=upgraded\n");
        Path png = metadata.resolveSibling("demo_A1.png");
        BufferedImage image = new BufferedImage(5, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        image.setRGB(1, 0, 0xff0000ff);
        image.setRGB(2, 0, 0xff12abcd); // separator must be ignored
        image.setRGB(3, 0, 0xff0000ff);
        image.setRGB(4, 0, 0x7fff0000); // partial alpha becomes empty in the original loader
        ImageIO.write(image, "png", png.toFile());
        var catalog = LegacyCatalogLoader.load(root);
        var plan = catalog.plans().get("norman:demo_A1");
        assertEquals(-1, plan.startLevel());
        assertEquals(List.of("home", "work", "upgraded"), plan.parameters().get("tag"));
        assertEquals(List.of("50"), plan.parameters().get("priority"));
        var decoded = plan.decode(catalog.palette());
        assertEquals(2, decoded.floors());
        assertEquals(0x0000ff, decoded.colorAt(0, 0, 0));
        assertEquals(0xff0000, decoded.colorAt(1, 0, 0));
        assertEquals(0xffffff, decoded.colorAt(0, 1, 0));
        assertEquals(0x0000ff, decoded.colorAt(1, 1, 0));
        assertTrue(decoded.unknownColors().isEmpty());
        assertEquals(List.of(new LegacyBuildingPlan.Position(0, -1, -1), new LegacyBuildingPlan.Position(0, 0, 0)),
                plan.servicePoints(decoded, catalog.palette(), 0, 0, 0, 0).get("sleepingPos"));
        assertThrows(IndexOutOfBoundsException.class, () -> decoded.colorAt(2, 0, 0));
    }

    @Test
    void readsLegacySemicolonUpgradesWithoutLosingInheritedDimensions() throws Exception {
        Path path = temp.resolve("legacy.txt");
        write(path, "length:5;width:3;startlevel:-2;tag:home;tag:inn\npriority:25;startlevel:-1\n");
        var parameters = LegacyCatalogLoader.planParameters(LegacyDocument.read(path, "legacy"), 1);
        assertEquals(List.of("3"), parameters.get("width"));
        assertEquals(List.of("home", "inn"), parameters.get("tag"));
        assertEquals(List.of("-1"), parameters.get("startlevel"));
    }

    @Test
    void indexesBundledCulturesAndDecodesActualNormanBuilding() throws Exception {
        Path bundle = Path.of(getClass().getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
        var catalog = LegacyCatalogLoader.load(bundle);
        assertEquals(7, catalog.cultures().size());
        assertTrue(catalog.plans().size() > 500);
        assertTrue(catalog.count("villagers") > 100);
        assertTrue(catalog.count("villages") > 20);
        assertEquals(catalog.count("villagers"), catalog.cultures().values().stream().mapToInt(c -> c.villagerTypes().size()).sum());
        assertEquals(catalog.count("villages"), catalog.cultures().values().stream().mapToInt(c -> c.villageTypes().size()).sum());
        var village = catalog.cultures().get("japanese").villageTypes().get("nogyo");
        assertEquals(2, village.startBuildings().stream().filter("japanesepeasanth"::equals).count());
        var carpenter = catalog.cultures().get("norman").villagerTypes().get("carpenter");
        assertEquals("male", carpenter.gender());
        assertTrue(carpenter.goals().contains("makeTimberFramePlainOak"));
        assertTrue(carpenter.textures().size() > 1);
        var fountain = catalog.plans().get("norman:fountain_A2");
        assertNotNull(fountain);
        assertEquals(7, fountain.width());
        assertEquals(-1, fountain.startLevel());
        assertEquals(List.of("65"), fountain.parameters().get("priority"));
        assertTrue(fountain.decode(catalog.palette()).floors() > 0);
        int failures = 0, unknown = 0;
        for (var plan : catalog.plans().values()) {
            try {
                if (!plan.decode(catalog.palette()).unknownColors().isEmpty()) unknown++;
            } catch (java.io.IOException error) {
                failures++;
                System.out.println(error.getMessage());
            }
        }
        System.out.println("Catalog: cultures=" + catalog.cultures().size() + ", plans=" + catalog.plans().size()
                + ", villages=" + catalog.count("villages") + ", villagers=" + catalog.count("villagers")
                + ", shops=" + catalog.count("shops") + ", global=" + catalog.globalDocuments().size()
                + ", diagnostics=" + catalog.diagnostics().size() + ", unknown-color plans=" + unknown);
        catalog.diagnostics().forEach(System.out::println);
        assertEquals(0, failures, "Bundled PNG dimensions must match their metadata");
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
