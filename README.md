## RemoveEnderPearlDrops

This is the ZymLabs implementation of removing Ender Pearl Drops from killing Enderman and Piglin bartering, as well as restricting Skeleton spawns to selected biomes.

> The plugin class is still named `RemoveEnderPearlDrops` for historical reasons, but it now does considerably more than that. See the feature list below.
 
---
 
## Features
 
### 1. Ender pearl scarcity
 
- **Enderman drops:** When an Enderman dies, any ender pearls are removed from its drop list.
- **Piglin bartering:** When a piglin barter would yield ender pearls, each pearl in the outcome is swapped for a single block of obsidian instead.
 
### 2. Anti-skybase enderman targeting
 
A repeating task (first run after 1 second, then every 5 seconds) sweeps every Enderman in every loaded world:
 
- Endermen are pushed to target the **closest Survival/Adventure player who is on a skybase**.
 
### 3. Enderman bonus loot
 
When an Enderman dies **while targeting a player who is _not_ on a skybase**, a roll of bonus loot is added to its drops. Each entry is rolled independently, so anywhere from zero to all of them can drop in a single kill:
 
This rewards fighting endermen in normal play while giving skybase grinders nothing extra.
 
### 4. Biome-aware mob replacement
 
On any non-plugin (`CUSTOM`-reason spawns are skipped) spawn of a **Skeleton, Stray, or Wolf**, the following rules apply.
 
**"Skeleton biomes"** are the cold/frozen set.
**Wolves** are never allowed to spawn naturally.
**Outside a skeleton biome:**
- Skeletons never spawn. Though there is a rare chance one might have wandered from a snow biome.
**Inside a skeleton biome:**
- Strays are converted to Skeletons 7 times out of 8 - unless the spawn came from a freeze conversion (`FROZEN` reason), which is left alone.
- Surviving Skeletons go through the equipment roll below.
 
### 5. Skeleton equipment roll
 
Each Skeleton that survives the rules above has a **1-in-8** chance to spawn holding a random wooden tool/weapon (sword, axe, pickaxe, shovel, or hoe). The held item is given a low **8.5%** drop chance so players are not flooded with wooden hoes.
