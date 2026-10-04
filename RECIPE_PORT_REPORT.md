# Recipe Port Report

## Converted data

The project contains 95 Minecraft 26.3 crafting recipe files in `src/main/resources/data/millenaire/recipe/`:

- 80 shaped recipes (`minecraft:crafting_shaped`)
- 15 shapeless recipes (`minecraft:crafting_shapeless`)

The files use the 26.3 format with string ingredient identifiers and `result.id` / `result.count`. All files parse as JSON and pass checks for recipe type, ingredients, patterns, and results.

## Identifier conversion

Legacy metadata and item-list aliases are resolved to modern IDs where the recovered data provides a mapping. Examples include:

- `stone_deco@0` -> `millenaire:mudbrick`
- `wood_deco@0` -> `millenaire:timberframeplain`
- `wood_deco@2` -> `millenaire:thatch`
- `millenaire:paint_bucket_white` -> `millenaire:paintbucketwhite`
- `millenaire:painted_brick_white` -> `millenaire:paintedbrickwhite`
- legacy `stonebrick` -> `minecraft:stone_bricks`
- legacy `snow` -> `minecraft:snow_block`
- legacy `wool` -> `minecraft:white_wool`
- legacy silver dye -> `minecraft:light_gray_dye`

Legacy output counts and crafting layouts are retained where the modern crafting schema can represent them.

## Runtime coverage

Every custom Millenaire ID referenced as an ingredient or result is registered. The runtime has 217 registered blocks, 190 standalone items, and 217 corresponding block items. The four previously missing basic slab IDs and all colored painted-brick families, including the 15 decorated color variants from the Forge registry, are present.

The recipes are data-pack definitions. They do not port custom villager crafting goals or other legacy crafting behavior.

## Build check

With Java 25, `gradlew.bat clean build --no-daemon` passes, including all 12 tests and resource processing.
