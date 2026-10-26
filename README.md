# Hollow Hours + Helper Buddy — Forge 1.20.1

Two Java Forge mods remade from the Bedrock add-ons, built automatically by GitHub Actions.
(สองมอด Java Forge ที่ทำใหม่จากแอดออน Bedrock — GitHub build ให้อัตโนมัติ)

| Mod | Folder | Status |
|---|---|---|
| **Hollow Hours**: horror ghosts, hiding, jumpscares, kill cinematics, sell-out-a-friend choice | `hollow-hours/` | 🚧 work in progress |
| **Helper Buddy**: helper NPC that fights, gathers, crafts and builds | `helper-buddy/` | 🚧 work in progress |

## Requirements
- Minecraft Java **1.20.1**
- **Forge 47.x** (not NeoForge)
- GeckoLib is bundled inside the jar; nothing else to install.

## Getting the jars
Open the **Actions** tab → latest green **Build mods** run → download `hollow-hours-jar` / `helper-buddy-jar`.
Pushing a tag such as `v2.0.0` also publishes a GitHub Release with both jars.

## Building locally
```
cd hollow-hours   # or helper-buddy
./gradlew build   # jar ends up in build/libs/
```
