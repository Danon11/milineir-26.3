# Village chest and wall-label stage

This stage replaces the generic locked/main chest and panel blocks with real block entities and connects them to manual building/group placement. It does not yet connect an active village economy, ownership system, or the original panel interfaces.

## Implemented

- `locked_chest` and `mainchest` use persistent 27-slot inventories, vanilla chest menus, lids, sounds, and rendering. Declared adjacent pairs in a building plan use a 54-slot double chest.
- Locked chests retain hardness 50 and effective blast resistance 1200; the `mainchest` creator block retains survival-unbreakable hardness. Locked chests and panels do not drop their block item, matching the legacy `quantityDropped` rules. Inventory contents use the inherited chest removal behavior.
- Special `mainchest*` and `lockedchest*` palette points produce the legacy locked-chest block. The main-chest role remains in the saved binding and service positions, as in the old construction process.
- Legacy special directions map `Top` to west, `Bottom` to east, `Left` to south, and `Right` to north before plan rotation. Guessed chest facing uses planned walls and open sides. Rows longer than two aligned chests reject the plan.
- Each generated chest/panel saves its building origin, plan ID, native name, and access policy. Inventory and binding data survive block-entity NBT round trips. Building records also expose canonical `chests`, `mainChests`, and `panels` service positions alongside original marker names.
- Locked chest access allows the stored owner, creative players, and players with the game-master permission. A generated chest currently has no owner and is locked; ownership, reputation, and village-control rules still need integration. Player-placed unbound chests are accessible. Player placement keeps chests separate to avoid merging different access policies.
- Vanilla hopper access is blocked through the worldly-container interface and block container holder. Fabric Transfer API queries return explicit empty storage, including queries without a side.
- `signwallGuess` becomes a Millenaire wall panel; `plainSignGuess` becomes an oak wall sign. Planned wall support determines facing. Generated panels display a waxed, persistent building label; vanilla sign packets synchronize the text. The full name is retained in the binding even when the visible label is shortened.
- Setup is part of the placement operation: all destination checks run first, all blocks are written, chest/panel bindings are initialized, and then neighbor updates run. A setup failure triggers the existing block-state rollback. Group placement initializes every building before publishing saved records.

## Remaining behavior

Chests and labels currently use vanilla chest/oak-sign visuals. The original custom textures remain in the project, but their renderers are not ported. Dynamic village statistics, panel-specific menus, read-only views of locked chests, access through village ownership/reputation, and resident stock handling remain pending. Building plans with `startinggood` now resolve legacy aliases, simulate deterministic contents, reject unsupported or over-capacity inventories before any world write, and apply prepared stacks to new chests.

The access policy governs opening and continued menu access. It does not yet implement all legacy theft, block-breaking, or village protection rules. Existing block entities cannot be overwritten by manual placement. Rollback covers states in the modified footprint, not all surrounding neighbor/fluid effects.

## Verification

Java 25 `gradlew.bat clean build --no-daemon` passes all 55 tests. Storage-specific coverage includes actual block-entity inventory/binding serialization, owner/admin/unlocked policy, sided insertion/extraction rejection, panel text/wax persistence, chest-pair connectivity across all four rotations, guessed wall support, starting-stock alias resolution and capacity preflight, setup rollback, and bindings across a village group. Placement tests use vanilla block stand-ins and an in-memory world; storage serialization tests exercise the actual custom block-entity classes with vanilla type stand-ins.

An actual Fabric dedicated-server bootstrap initialized the mod and loaded the complete content catalog without registry/class errors. It stopped at the EULA check before loading a world. Chest interaction, client rendering, hopper/Fabric transfer behavior in a running world, and placement physics still require an in-game test.
