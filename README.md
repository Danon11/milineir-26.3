# Millenaire for Fabric 26.3

This is an in-progress port of Millenaire 8.1.2 from Forge 1.12.2 to Fabric for Minecraft 26.3. It includes the recovered Forge source as a migration reference, but the original village gameplay is not yet fully ported.

## Build

Requirements: Java 25 and an internet connection (cloud sessions install Java 25 through `.claude/hooks/session-start.sh`) for the first dependency download. The project includes Gradle Wrapper scripts.

In PowerShell, set `JAVA_HOME` to a Java 25 JDK, then run:

```powershell
./gradlew.bat clean build
```

The mod JAR is written to `build/libs/millenaire-8.1.2-fabric.1.jar`.

## Current behavior

- Registers 217 Millenaire blocks and 190 standalone items, including working painted-brick recoloring and crop cycles for maize, cotton, rice, turmeric, and two-block grape vines.
- Provides a three-input Fire Pit with its own menu, screen, fuel handling, and food smelting.
- Provides persistent village chests and wall labels for manual building placement. Main/locked chest markers retain facing, declared pairs, building identity, and inventory; generated chests use an owner/admin access policy and reject automation. Labels display the building name. `startinggood` rules are resolved from the legacy item catalog, generated deterministically, capacity-checked before writes, and applied to new building chests. Village ownership, resident stock/economy, and dynamic panel screens remain pending. See [STORAGE_PORT_REPORT.md](STORAGE_PORT_REPORT.md) and [PORTING_ROADMAP.md](PORTING_ROADMAP.md).
- Loads seven culture descriptors, 1,811 PNG building plans, 52 village definitions, 285 villager definitions, and 84 shops. Preserves shared goal and quest documents and custom file overrides. `/millenaire content stats`, `/millenaire content reload`, `/millenaire village types <culture>`, and `/millenaire building info <culture:plan_A0>` inspect the catalog.
- Places supported PNG building plans through `/millenaire building check|place|replace <rotation> <culture:plan_A0>` and persists their service points. Unsupported palette states stop placement; `/millenaire building list` lists stored placements.
- Plans manual starting layouts from village definitions through `/millenaire village plan <seed> <culture:type>`. Preserves repeated starting houses, chooses weighted variants, and reserves space around each building. `check`, `checkreplace`, `place`, and `replace` use the same arguments; successful group placements persist through world reloads and appear in `/millenaire village settlements`. Unsupported walls, hamlets, sub-buildings, and palette points reject the whole placement. See [VILLAGE_PORT_REPORT.md](VILLAGE_PORT_REPORT.md) for commands and limits.
- Parses and validates all 91 bundled quests and their translated texts; `/millenaire quest list` and `/millenaire quest info <group/key>` inspect them. Quests are not offered or executed yet. See [QUEST_PORT_REPORT.md](QUEST_PORT_REPORT.md).
- Spawns living villagers with legacy skins, clothing and names; they sleep at night and work, trade and give quests by day. `/millenaire village replace <seed> <culture:type>` builds a starting village with walls and residents (47 of 52 types). See [PORTING_STATUS.md](PORTING_STATUS.md).
- Persists village marker coordinates through `/millenaire village mark` and `/millenaire village list`; markers do not create villages.
- Includes 95 converted crafting recipes, block loot tables, converted models and blockstates, English names for registered content, and 23 locale files.
- Seeds bundled Millenaire content into `mods/millenaire` and `mods/millenaire-custom` without overwriting files that are already there.

Most legacy blocks and items still use generic implementations. Natural world generation, terrain adaptation, automatic village construction and upgrades, villagers and AI, executable quests and goals, trading, and most old GUIs and renderers remain to be ported. The manual starting-layout tool does not create a functioning village. See [PORTING_STATUS.md](PORTING_STATUS.md) for the current scope and [RECIPE_PORT_REPORT.md](RECIPE_PORT_REPORT.md) for recipe details. The JAR's bundled data is canonical where the recovered source and `Public-master` snapshot differ.



