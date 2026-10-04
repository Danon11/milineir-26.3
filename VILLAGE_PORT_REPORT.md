# Village starting-layout stage

This stage adds a manual planner and group placement mechanism. It does not yet add naturally generated or functioning Millenaire villages.

## Implemented rules

| Legacy input | Current behavior |
| --- | --- |
| `centre` | Select one initial centre plan; use legacy centre facing plus the plan's building orientation. |
| Repeated `start` | Preserve the order and create one building for every entry, including repeated houses. |
| Plan `weight` | Choose among sorted level-zero variants using a deterministic seed. Zero-weight variants are excluded; negative or all-zero weights are reported. |
| Village `radius` | Search within a square of the declared radius; default 80, operation limit 256. |
| `mindistance`, `maxdistance` | Limit the square-ring search in the same fractional radius units as the original generator. |
| Clear areas | Reserve rotated footprints with symmetric or asymmetric clear areas and an extra edge block; footprints cannot intersect. |
| Orientation | Face starting buildings toward the centre, or use the declared fixed direction, then add `buildingorientation`. |
| `altitudeoffset` | Apply to a common ground plane at the command's height. |
| `farfromtag`, `closetotag` | Check horizontal distance to previously selected tagged buildings. Later rules for the same tag replace earlier values, as in the original map. |

The weighted variant selection and orientation rules were checked against `BuildingPlanSet.getRandomStartingPlan`, `BuildingPlan.testSpot`, `BuildingPlan.computeOrientation`, and `ValueIO.DirectionIO` in the recovered Forge source. The search is deterministic and independent of terrain; it does not reproduce the old terrain scanner or reachability map.

## Commands

All commands require the same game-master permission as the existing Millenaire developer commands.

```text
/millenaire village plan <seed> <culture:type>
/millenaire village check <seed> <culture:type>
/millenaire village checkreplace <seed> <culture:type>
/millenaire village place <seed> <culture:type>
/millenaire village replace <seed> <culture:type>
/millenaire village settlements
```

`plan` previews geometry and reports unsupported building elements. `check` checks the same group against empty/replaceable space. `checkreplace` checks terrain replacement. Neither check writes blocks. `place` and `replace` compile every building before checking every destination and writing the group as one operation. Existing block entities, bedrock, unloaded chunks, world borders, height limits, and reserved areas of saved groups prevent placement. `replace` explicitly allows occupied terrain to be replaced.

For inspection, use `/millenaire village plan 1234 norman:agricole`. Its required walls and special building points currently block placement. The planner does not substitute blocks or omit required structures to make a village appear supported.

## Persistence and failures

Successful groups are saved in `millenaire:settlements` with their type, display name, seed, origin, radius, building plan IDs, rotations, reserved areas, and service positions. The individual building records are also published to the existing building list. These are placed-structure records, not active village entities. Existing village markers remain in their original separate saved data.

A palette error in any house or a destination error anywhere prevents all writes. A later write or block-entity initialization failure triggers restoration of block states across the entire modified group. Chest and panel bindings are initialized for each building before successful records are published. As with the individual building tool, surrounding fluid and neighbor effects are outside this rollback guarantee. No failed or previewed operation publishes a settlement record.

Operation limits are 64 starting buildings, radius 256, 32,768 decoded cells per building, and 131,072 combined block operations.

## Verification

Java 25 `gradlew.bat clean build --no-daemon` passes with 55 tests. New tests cover repeated starts, initial-only weighted variants, deterministic ordering, rotated clearance, tag constraints, fixed directions, altitude offsets, missing plans, operation bounds, group preflight, failure rollback, unsupported extra generators, and saved-data round trips and overlap rejection.

The geometry audit uses bundled definitions, seed 1234, and origin `(0,64,0)`:

| Result | Village types |
| --- | --- |
| Complete geometric layout | 46 of 52 |
| Custom player-defined centre required | `byzantines:customcontrolled`, `indian:indian_customcontrolled`, `japanese:japanese_customcontrolled`, `mayan:mayan_customcontrolled`, `norman:customcontrolled` |
| No room for one required starting plan with this layout | `norman:hameau_abbatiale`: `cattlefarm_A0`, radius 50 |

The 46 complete geometric layouts are not a count of placeable or playable villages. Palette conversion, special points, extra structures, and world destinations are additional gates. Group placement tests use small synthetic plans and an in-memory world; block physics and commands have not yet been exercised in a running Minecraft client.

## Remaining work

Chest ownership integration and starting inventory, dynamic village panels and their old renderers/screens, additional palette states, starting sub-buildings, wall and hamlet generators, terrain adaptation, foundation extension, automatic discovery and world generation, roads, construction queues and upgrades, residents and AI, trade, quests, and active village simulation remain to be ported.
