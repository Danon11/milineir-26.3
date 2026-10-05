package org.millenaire.fabric.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VillageBooksTest {
    @Test void pagesKeepWithinLinesAndCharacters() {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < 60; i++) entries.add("Entry number " + i + " with a longer name");
        entries.add("x".repeat(500));
        var pages = VillageBooks.paginate(entries);
        assertTrue(pages.size() > 5);
        for (String page : pages) {
            assertTrue(page.length() <= 220, page);
            int lines = page.lines().mapToInt(VillageBooks::lines).sum();
            assertTrue(lines <= VillageBooks.PAGE_LINES || page.lines().count() == 1, "too many lines: " + lines);
        }
        assertEquals(String.join("", entries).length(), String.join("", pages).replace("\n", "").length());
    }

    @Test void signLinesWrapAtWords() {
        var lines = VillagePanels.lines("Village d'Agritchultchure", "12 villagers");
        assertTrue(lines.size() <= 4);
        assertTrue(lines.stream().allMatch(line -> line.length() <= VillagePanels.WIDTH), lines.toString());
        assertEquals("12 villagers", lines.getLast());
    }
}
