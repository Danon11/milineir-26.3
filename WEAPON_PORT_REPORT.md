# Weapon migration

The six `ItemMillenaireSword` registrations from recovered `MillItems` now have modern weapon components and preserve their old material choices:

- `normanbroadsword`: Norman material, default material enchantability.
- `tachisword`: obsidian/Mayan material, default material enchantability.
- `seljukscimitar`: better-steel material (durability 1,561, speed 5, bonus 3, enchantability 10).
- `mayanmace` and `byzantinemace`: vanilla iron material.
- `inuittrident`: vanilla iron material with enchantability 20.

All use durability 1,561, a main-hand attack damage modifier equal to `3 + material bonus`, and -2.4 attack-speed modifier, matching the old `ItemSword` defaults. The old `byzantinemace` starts with Knockback II; Fabric stores this through a deferred `ENCHANTMENTS` component keyed to the modern Knockback registry entry. The component is created when the item registry resolves its initializer, after bootstrap registries are available.

The source check used `ItemMillenaireSword.java` and `MillItems.java` in `reference/forge-1.12.2-src`. The custom weapon advancement, legacy `onLeftClickEntity` hook, and loaded-world combat remain pending. `inuittrident` is currently represented as a weapon item; projectile trident behavior is separate work.

Verification covers all six IDs, materials, durability, stack limit, main-hand/off-hand attributes, enchantability and weapon components. Full clean build passed with 156 tests.
