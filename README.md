# Millenaire for Fabric 26.3

This is an in-progress port of Millenaire 8.1.2 from Forge 1.12.2 to Fabric for Minecraft 26.3. It includes the recovered Forge source as a migration reference. Villages generate, grow and live; several systems of the original (player-controlled villages, most old screens, village relations) are still missing.

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
- Places PNG building plans through `/millenaire building check|place|replace <rotation> <culture:plan_A0>` and persists their service points; all 1,811 plans pass `/millenaire content audit`. `/millenaire building list` lists stored placements.
- Plans manual starting layouts from village definitions through `/millenaire village plan <seed> <culture:type>`. Preserves repeated starting houses, chooses weighted variants, and reserves space around each building. `check`, `checkreplace`, `place`, and `replace` use the same arguments; successful group placements persist through world reloads and appear in `/millenaire village settlements`. Village commands load the chunks under the layout first. See [VILLAGE_PORT_REPORT.md](VILLAGE_PORT_REPORT.md) for commands and limits.
- Runs the 91 bundled quests: villagers offer them by day, players accept or refuse in chat, and steps, rewards, penalties and time limits apply. World quests place their buildings in the wild and track when you reach them. See [QUEST_PORT_REPORT.md](QUEST_PORT_REPORT.md).
- Generates villages in newly loaded terrain (`config.txt` distances and biomes) and grows them: villagers gather resources, construct new buildings and upgrades.
- Spawns living villagers with legacy skins, clothing, names and weapons; they sleep at night and by day work (farming, mining, woodcutting, crafting, cooking, carrying goods between houses and shops, tending animals), trade, give quests, defend the village or flee. Mothers have children who grow up, and dead residents return. `/millenaire village replace <seed> <culture:type>` builds a starting village with walls and residents (the player-controlled types are founded with the summoning wand on a block of gold, see below). See [PORTING_STATUS.md](PORTING_STATUS.md).
- Player villages: the summoning wand on a block of gold founds a village you own (chat buttons choose the type); on a sign in your village it registers a building you built yourself; elsewhere in the village it lists buildings to order.
- Hire fighters (sneak-use), join pujas at Indian and Mayan temples, read parchments as books, follow raids and diplomacy between villages (`/millenaire_village diplomacy`, `/millenaire_village journal`).
- `./gradlew runClientGameTest` starts a client, renders villagers and villages and saves screenshots (see `docs/screenshots/`).
- Persists village marker coordinates through `/millenaire village mark` and `/millenaire village list`; markers do not create villages.
- Includes 95 converted crafting recipes, block loot tables, converted models and blockstates, English names for registered content, and 23 locale files.
- Seeds bundled Millenaire content into `mods/millenaire` and `mods/millenaire-custom` without overwriting files that are already there.

Many legacy blocks and items still use generic implementations, and most old GUIs (traveller's book, village panels) remain to be ported. See [PORTING_STATUS.md](PORTING_STATUS.md) for the current scope and [RECIPE_PORT_REPORT.md](RECIPE_PORT_REPORT.md) for recipe details. The JAR's bundled data is canonical where the recovered source and `Public-master` snapshot differ.



