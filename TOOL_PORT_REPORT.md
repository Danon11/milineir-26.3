# Plain tool migration

The twelve tools registered in recovered `MillItems` now use Minecraft 26.3 components. IDs, recipes and handheld models are preserved. Weapons, armor, bows and special equipment are separate unfinished work.

## Materials

| Culture / ID prefix | Legacy harvest level | Modern drop restriction | Durability | Mining speed | Damage bonus | Tool enchantability |
| --- | --- | --- | --- | --- | --- | --- |
| Norman / `norman` | 2 | Iron | 1561 | 10 | 4 | 10 |
| Byzantine / `byzantine` | 2 | Iron | 1561 | 12 | 3 | 15 |
| Mayan / `mayan` | 3 | Diamond | 1561 | 6 | 2 | 25 |

Each prefix has `pickaxe`, `axe`, `shovel` and `hoe`. Mining speed applies to matching vanilla tool block tags. Durability does not determine harvest tier. Custom legacy materials had no ingredient repair configured; the repair ingredient set stays empty. Vanilla same-item repair remains available.

## Combat and wear

All values below are main-hand attribute modifiers, before the player's base attributes.

| Tool | Damage modifier | Attack speed modifier | Wear per attack | Wear per mined block |
| --- | --- | --- | --- | --- |
| Pickaxe | 1 + material bonus | -2.8 | 2 | 1 |
| Axe | 8, overrides material bonus | -3 | 2 | 1 |
| Shovel | 1.5 + material bonus | -3 | 2 | 1 |
| Hoe | 0 | material bonus - 3 | 1 | 0 |

Hoes mine at ordinary speed 1 and keep the original lack of enchanting-table enchantability. Other tools keep their material enchantability. Appended vanilla tool category tags provide modern book/enchantment eligibility. Axe, shovel and hoe interactions delegate to the modern vanilla block transformers, including stripping, flattening, extinguishing and modern tilling targets. Modern axes use vanilla shield-disabling behavior. These are version adaptations, not an assertion of identical 1.12 interactions.

## Reference checks

Local sources: `reference/forge-1.12.2-src/src/main/java/org/millenaire/common/item/MillItems.java` and `ItemMillenairePickaxe`, `ItemMillenaireAxe`, `ItemMillenaireShovel`, `ItemMillenaireHoe`.

Base game behavior was checked against:

- [1.12 ItemTool](https://raw.githubusercontent.com/WangTingZheng/mcp940/master/src/minecraft/net/minecraft/item/ItemTool.java)
- [1.12 ItemHoe](https://raw.githubusercontent.com/WangTingZheng/mcp940/master/src/minecraft/net/minecraft/item/ItemHoe.java)
- [Forge 1.12 ItemAxe patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.12.x/patches/minecraft/net/minecraft/item/ItemAxe.java.patch)
- [Forge custom ToolMaterial repair extension](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.12.x/patches/minecraft/net/minecraft/item/Item.java.patch)

Modern signatures and component behavior were inspected from the cached Minecraft 26.3 classes. Components resolve at registry loading rather than invoking the bootstrap-only vanilla material factories after registries freeze.

## Verification scope

Five tests resolve actual configured component initializers against the vanilla registry provider. They check all twelve IDs, durability, stack limit, enchantability, empty repair ingredients, main/off-hand damage and speed, mining speed, iron-versus-diamond drop eligibility, hoe wear and block transformers. Representative block tags are explicitly bound and restored in the test fixture. Resource tests check that all four tool category tags append the expected IDs.

Loaded-world mining, combat, tool transformations, enchantments and anvil operations still need validation. Tests do not measure physical interaction or neighbor updates. Mining tags for Millenaire's own blocks remain to be audited.

Clean build passed with 154 tests and zero failures/errors. A dedicated-server bootstrap registered the mod and indexed its catalog, then stopped at eula=false before world loading. This checks initialization only. Windows OSHI emitted performance-counter warnings; they did not prevent bootstrap. No EULA consent was changed.
