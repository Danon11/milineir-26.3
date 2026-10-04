# Quest definition port

Stage 12, first part: the legacy quest files are now loaded as data. Quests are not yet offered, tracked or rewarded.

## What is ported

- `org.millenaire.fabric.quest.QuestDefinitionParser` reads `quests/<group>/<key>.txt` through `LegacyDocument`, so UTF-8/UTF-16/Windows-1252 files and old Mac `\r` line endings work. Key semantics follow the NeoForge 1.21.1 `QuestDefinitionConverter` (`reference/neoforge-1.21.1/`):
  - quest level: `chanceperhour`, `maxsimultaneous` (default 5), `minreputation`, `required/forbidden` `player/global` tags. These are accepted anywhere in the file; several bundled quests declare `minreputation` after the last step.
  - `definevillager`: `key`, repeated `type=culture/type`, `relatedto` + `relation` (`samehouse`, `samevillage`, `nearbyvillage`, `anyvillage`), `requiredtag`, `forbiddentag`.
  - step: `villager`, `duration`, `showrequiredgoods`, `requiredgood`/`rewardgood` (repeated aliases are summed), `rewardmoney`, `rewardreputation`, `penaltyreputation`, `set/clear` villager, player and global tags for success and failure, step-level tag conditions, `setactiondatasuccess`, `relationchange`, `bedrockbuilding`.
  - numbers may be products (`2*64*64`); overflow is rejected.
- The parser is stricter than the converter: unknown keys, missing step villager or duration, undeclared villager references (step, tag outcomes, relation changes, `relatedto`), duplicate villager keys, and bad booleans or counts reject the whole quest with `file:line` diagnostics instead of dropping single lines.
- `QuestCatalog.from(LegacyContentCatalog)` checks references against the loaded content: villager types (`culture/type` must exist under `cultures/<culture>/villagers`), goods (`itemlist.txt` aliases), and `bedrockbuilding` targets (`villages/` or `lonebuildings/`). Custom files at the same relative path replace bundled ones.
- `enchantedsword` is not an itemlist alias. It is accepted as a rule-based good (`QuestCatalog.SPECIAL_GOODS`); the quest text says "show the Sadhu an enchanted sword", so the planned rule is any sword with at least one enchantment. The 1.12 source is not in this upload, so this rule still needs checking against the original before quest execution uses it.
- `QuestTexts` loads `languages/<lang>/quests_*.txt` (`<quest>_<step>_label|description|description_success|description_refuse|description_timeup|listing`), with English fallback, and renders `$placeholder$` tokens, leaving unknown ones visible.

## Bundled data result

- 91 of 91 quest files load: 43 in `*basic`, `common` and `marvel-norman`, and 48 in the `worldquest-*` chains. 41 of the world-quest files use action data or bedrock buildings (`QuestDefinition.requiresWorldActions()`).
- Every step of every quest has an English label.
- One expected diagnostic: `inuitbasic/fishingfrenzy` and `japanesebasic/fishingfrenzy` share the text key `fishingfrenzy`, as in the original data.

## Not yet ported

Quest offering by villagers (`chanceperhour`, `maxsimultaneous`, reputation), player/global tag storage, quest instances and save data, durations and time-outs, goods transfer, money and reputation rewards, villager tag outcomes, relation changes, action-data driven world quests (Sadhu, Alchemist, Fallen King, Norman marvel), bedrock building generation, and the quest GUI and travel-book listing.

## Verification

`QuestDefinitionParserTest` covers field parsing, Mac line endings, summed goods, products, outcomes, tag-only villagers, placeholder rendering, and 15 rejection cases. `QuestCatalogBundleTest` loads the real bundle (91 quests, per-group counts, world-quest count, English labels, French translation with fallback, language-name validation) and checks custom overrides and broken references.

`gradlew clean build` passes with Java 25 against Minecraft 26.3 and Fabric API 0.161.0+26.3: 173 tests, 0 failures, including the quest command wiring.
