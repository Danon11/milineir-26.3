# Millénaire — NeoForge Fork

> **This is a fork of [MoonCutter2B](https://github.com/MoonCutter2B)'s Millénaire port, updated and maintained for NeoForge 1.21.1.**

Millénaire is a Minecraft mod that adds living NPC villages to your world. Each village belongs to one of several historical cultures — Normans, Indians, Byzantines, Mayans, Japanese, Seljuks, and Inuits — and develops over time through construction, trading, and its own economy.

---

## Features

- **7 unique cultures**, each with their own buildings, villager types, language, and progression system
- Villages **grow dynamically** — villagers build new structures as resources and reputation allow
- **Trading system** with culture-specific goods, coins, and shop mechanics
- **Reputation & diplomacy** — your standing with a village affects what you can buy, sell, and unlock
- **Quest system** with culture-specific and world quests
- **Travel book** for navigation and village discovery
- Custom blocks, items, crops, and entities per culture

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |

---

## Installation

1. Download the latest JAR from [Releases](https://github.com/Leviaria/Millenaire/releases)
2. Place it in your `mods/` folder
3. Launch Minecraft with NeoForge 21.1.x

---

## Contributing

Contributions are welcome. Here's how to get started as a developer.

### Prerequisites

- **JDK 21** (Eclipse Temurin recommended)
- **Gradle** (via the included wrapper — no separate install needed)

### Setup

```bash
git clone https://github.com/Leviaria/Millenaire.git
cd Millenaire
./gradlew eclipse   # or: ./gradlew idea
```

Then import into your IDE:
- **Eclipse**: `File → Import → Gradle → Existing Gradle Project`
- **IntelliJ**: `File → Open` and select the `build.gradle`

### Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

### Running in dev

```bash
./gradlew runClient   # launches Minecraft client with the mod loaded
./gradlew runServer   # launches a dedicated server
```

### Project structure

```
src/main/java/org/millenaire/    ← all mod source code
src/main/resources/
  assets/millenaire/             ← textures, models, blockstates, lang
  data/millenaire/               ← recipes, loot tables, advancements, tags
  millenaire/                    ← cultures, quests, visit goals, languages
  META-INF/neoforge.mods.toml
```

### What to work on

Check the [Issues](https://github.com/Leviaria/Millenaire/issues) tab for open bugs and feature requests. Before opening a pull request:

---

## License

This project is licensed under **GPL-3.0 license**.  
It is a fork of [MoonCutter2B](https://github.com/MoonCutter2B)'s Millénaire port, which is itself based on the original Millénaire mod by Kinniken — both released under the MIT License.
