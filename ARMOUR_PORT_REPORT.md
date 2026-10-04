# Armour migration

The recovered Forge project registers 29 armor items: seven four-piece sets plus the Seljuk turban. They now use Minecraft 26.3 humanoid armor components with the corresponding head, chest, legs and feet slots. Norman, Byzantine, Japanese blue/red/guard and Seljuk use modern iron armor material; Fur and the Seljuk turban use leather material as the closest vanilla equipment behavior.

The old Forge sets had custom defense arrays, durability factors, equip sounds, toughness and texture paths. This stage preserves the item IDs, slots, stack/durability/equipment behavior and existing Millenaire texture assets. A follow-up stage must define custom `ArmorMaterial` records and asset keys where exact defense, toughness, knockback resistance, sounds and texture overlays are required. Quest crown and other armor materials declared in `MillItems` are separate entries that are not registered by that file's item event and remain pending.

Tests cover all 29 IDs, slots, type mapping, material assignment, and rejection of unknown names. Loaded-world equip, damage reduction, rendering and resource-pack behavior remain unverified.
