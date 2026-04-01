# Custom Portals Foxified

> **WIP** | This mod is in early alpha. Core portal creation and teleportation work, but some features (rune visuals, particles, cross-dimension travel) are still being polished.

Build portals out of any block. Create two matching frames, light them with a colored catalyst, and travel between linked portals ezpc

A NeoForge re-implementation of [Custom Portals](https://modrinth.com/mod/custom-portals) (Fabric).

## Portals

Making a portal is simple just build a rectangular frame using any solid block, then right-click the inside with a **Portal Catalyst**. Create a second frame out of the exact same block, light it with the matching catalyst color, and they'll instantly link up.

- Any block works as a frame; stone, wood, diamond blocks, whatever you want
- 16 catalyst colors to organize your portal network
- Portals link by matching **color + frame material**
- Same-dimension and cross-dimension travel (with Gate Rune)

### Catalysts

Shapeless recipe: **ender pearl + any dye** → 1 portal catalyst

## Runes

Place rune blocks on or adjacent to a portal's frame to enhance it.

| Rune | Effect | Recipe |
|---|---|---|
| **Haste** | Instant teleportation (1 tick) | amethyst shard + sugar |
| **Gate** | Enables cross-dimension linking | amethyst shard + eye of ender |
| **Enhancer** | Increases linking range to 1,000 blocks | amethyst shard + glowstone dust |
| **Strong Enhancer** | Increases linking range to 10,000 blocks | amethyst shard + glowstone |
| **Infinity** | Unlimited linking range | amethyst shard + nether star |

Without runes, portals link within 100 blocks in the same dimension.

## Redstone

Configurable via `custom_portals_foxified-common.toml`:

- **OFF** (default) - portals are active whenever linked
- **ON** - portals require a redstone signal to activate
- **NO_EFFECT** - portals are always active regardless of link state

## The og mod works with Sinytra Connector, so why?

Well, for starters, Sinytra Connector can sometimes break mods, and just adds overhead; so a native port is always better. More importantly, the original mod had to use "mixins" to rewrite Minecraft's teleportation sequence. Unfortunately, that sometimes causes weird behavior, crashes, or data loss with other mods, especially backpack or custom inventory mods that attach data to players. It's not the original dev's fault; just how modding goes sometimes.

This port solves that by reimplementing the core features using NeoForge's native `Portal` interface. Vanilla Minecraft automatically handles all the player data, and dimension hopping. No mixins, no data loss, and so hopefully better compatibility with other mods.

## Compatibility

- Requires NeoForge 1.21.1 (21.1.219+)

## License

GPL-3.0-or-later
