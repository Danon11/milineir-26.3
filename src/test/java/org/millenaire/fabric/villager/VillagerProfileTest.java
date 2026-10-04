package org.millenaire.fabric.villager;

import org.junit.jupiter.api.Test;
import org.millenaire.fabric.content.LegacyCatalogLoader;
import org.millenaire.fabric.content.LegacyContentCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VillagerProfileTest {
    private static Path bundle() throws Exception {
        return Path.of(VillagerProfileTest.class.getResource("/todeploy/millenaire/blocklist.txt").toURI()).getParent();
    }

    @Test
    void everyBundledVillagerTypeHasExistingSkinsAndClothes() throws Exception {
        LegacyContentCatalog catalog = LegacyCatalogLoader.load(bundle());
        List<String> diagnostics = new ArrayList<>();
        var profiles = VillagerProfile.all(catalog, diagnostics);
        assertEquals(List.of(), diagnostics);
        assertEquals(catalog.count("villagers"), profiles.size());
        Path assets = Path.of(VillagerProfileTest.class.getResource("/assets/millenaire").toURI());
        Set<String> missing = new TreeSet<>();
        for (var profile : profiles.values()) {
            for (String texture : profile.textures()) if (!Files.isRegularFile(assets.resolve(texture))) missing.add(profile.id() + " " + texture);
            profile.clothes().values().forEach(layers -> layers.forEach(layer -> layer.forEach(texture -> {
                if (!Files.isRegularFile(assets.resolve(texture))) missing.add(profile.id() + " " + texture);
            })));
        }
        assertEquals(Set.of(), missing);
        assertTrue(profiles.values().stream().anyMatch(p -> p.model() == VillagerProfile.Model.FEMALE_ASYMMETRICAL));
    }

    @Test
    void rollsSkinClothesScaleAndNamesFromCultureLists() throws Exception {
        LegacyContentCatalog catalog = LegacyCatalogLoader.load(bundle());
        var profiles = VillagerProfile.all(catalog, new ArrayList<>());
        var carpenter = profiles.get("norman/carpenter");
        assertNotNull(carpenter);
        assertEquals(VillagerProfile.Model.MALE, carpenter.model());
        var names = VillagerProfile.nameLists(catalog.cultures().get("norman"));
        var appearance = carpenter.roll(new SplittableRandom(7), names);
        assertTrue(carpenter.textures().contains(appearance.texture()));
        assertEquals(Optional.of("textures/entity/norman/male/clothes/nor_carpenter_0.png"), appearance.cloth0());
        assertTrue(names.get("men_names").contains(appearance.firstName()), appearance.firstName());
        assertTrue(names.get("family_names").contains(appearance.familyName()), appearance.familyName());
        assertTrue(appearance.scale() >= 0.8F * carpenter.baseScale() && appearance.scale() <= 0.89F * carpenter.baseScale());
        assertTrue(carpenter.goals().contains("gorest"));
        assertEquals(appearance, carpenter.roll(new SplittableRandom(7), names), "deterministic for a seed");
    }
}
