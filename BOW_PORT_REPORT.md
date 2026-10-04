# Bow migration

`yumibow`, `inuitbow` and `seljukbow` now register as vanilla `BowItem` instances. They keep one-item stacks, durability 384 and enchantability 1, 20 and 20 from `ItemMillenaireBow`. The recovered `speedFactor` and `damageBonus` values remain in `LegacyBows.Definition` and are exposed for the pending projectile hook. Existing standby and pulling models are preserved.

The vanilla bow use flow, arrows and durability are active through Fabric/Minecraft 26.3. Exact legacy draw speed and projectile damage need a custom `BowItem` release hook. Loaded-world firing and client animation remain unverified.
