# Custom Portals Foxified

![Banner, diorite portal in floating island](public/banner.png)

Build portals out of any block. Create two matching frames, light them with a colored catalyst, and travel between linked portals.

A NeoForge re-implementation of [Custom Portals](https://modrinth.com/mod/custom-portals) (Fabric).

<!-- TODO: screenshot/gif of portal in action -->

## Portals

Build a rectangular frame using any solid block, then right-click the inside with a **Portal Catalyst**. Create a second frame out of the exact same block, light it with the matching catalyst color, and they link up instantly. Frames can be built upright (vertical portals) or flat on the ground/ceiling (horizontal portals).

- Any block works as a frame; stone, wood, diamond blocks, whatever you want
- Vertical or horizontal portals work, you can build frames on walls, floors, ceilings, even floating.
- 16 catalyst colors to organize your portal network
- Portals link by matching **color + frame material**
- Same-dimension and cross-dimension travel (with Gate Rune)
- Colored particles matching your catalyst color
- Baaaahhh 🐑

<!-- TODO: gif showing portal creation + linking -->

### Catalysts

Shaped recipe: **4 ender pearls + 4 eyes of ender + 1 dye** -> 2 portal catalysts

Catalysts can also be recolored: any catalyst + a dye -> 1 catalyst of the new color (shapeless).

<!-- TODO: recipe screenshot -->

## Runes

Place rune blocks on the portal's frame to enhance it. The rune must be mounted on a block that is directly adjacent to a portal block.

| Rune | Effect | Key Ingredients |
|---|---|---|
| **Haste** | Instant teleportation (1 tick) | feathers, rabbit foot, amethyst shard |
| **Gate** | Enables cross-dimension linking | weeping vines, twisting vines, amethyst shard |
| **Enhancer** | Increases linking range to 1,000 blocks | lapis lazuli, amethyst shard |
| **Strong Enhancer** | Increases linking range to 10,000 blocks | prismarine, enhancer rune |
| **Infinity** | Unlimited linking range | popped chorus fruit, netherite ingot, strong enhancer rune |

Without runes, portals link within 100 blocks in the same dimension. Haste on either end of a linked pair grants instant teleportation for both sides.

<!-- TODO: rune placement screenshot -->

## Redstone

When enabled (default), a redstone signal **turns off** a portal. The portal unlinks while powered and automatically relinks when the signal is removed. This works per-portal, so you can selectively disable one end of a link and the freed partner will find a new match if one exists.

This is intentional. It follows vanilla redstone logic IMO (signal = active effect on the block) and enables creative builds. Think of something like ABC portal switching, redstone-controlled travel networks, and multiplayer engineering. There are edge cases with multi-portal setups, and that's by design. Work with them, build around them. It's more fun this way.

Redstone can be fully disabled in config if you don't want it.

<!-- TODO: gif showing redstone toggle -->

## Linking

Portals link automatically to the nearest compatible unlinked portal (same color + same frame block). When a portal is destroyed or disabled, its partner becomes unlinked and will try to find a new match 🥀

This system is intentionally simple and deterministic. In multiplayer or complex setups with many same-color portals, there are edge cases where relinking may not pick the frame you expected. This is by design; I think it rewards thoughtful portal placement and enables creative network topologies that a stricter system wouldn't allow.

## Configuration

All settings are in `custom_portals_foxified-common.toml`:

| Setting | Default | Description |
|---|---|---|
| `maxPortalSize` | 64 | Maximum portal blocks a frame can enclose |
| `baseRange` | 100 | Linking range with no enhancer runes |
| `enhancedRange` | 1,000 | Linking range with weak enhancer rune |
| `strongRange` | 10,000 | Linking range with strong enhancer rune |
| `allowCrossDimension` | true | Whether Gate Runes can link across dimensions |
| `muteSounds` | false | Mute portal ambient and teleport sounds |
| `redstoneDisables` | true | Redstone signal turns off adjacent portals |

## Differences from the Original

### Why port?

The original mod works with Sinytra Connector, but connector can break mods and adds overhead. More importantly, the original mod uses mixins to rewrite Minecraft's teleportation sequence, which can cause crashes or data loss with backpack/inventory mods that attach data to players. It's not the original dev's fault, it's just how modding goes.

This port uses NeoForge's native `Portal` interface instead. Vanilla handles all player data and dimension hopping. No mixins, no data loss, better compatibility.

Beyond compatibility, this port also includes a number of improvements over the original:

<details>
<summary>Improvements over the original</summary>

>**Performance**
> - Removed per-tick block entity polling - the original mod ticks every portal block every game tick to check state. This port is fully event-driven (neighborChanged, link/unlink, rune changes)
> - No block entity at all - portal state lives in world saved data with spatial indexing
>
>**Compatibility**
> - No mixins - the original mod uses EntityMixin, ServerPlayerMixin, and LocalPlayerMixin to rewrite teleportation. This port uses NeoForge's native `Portal` interface so vanilla handles all entity data and dimension transitions cleanly
>
>**Gameplay**
> - Redstone reworked - the original has three modes (OFF/ON/NONE) that only control the visual LIT state. This port uses a single toggle where redstone actually unlinks the portal and relinks when the signal is removed, enabling real redstone contraptions like ABC portal switching
> - Rune removal revalidates links - in the original, removing a gate rune from a cross-dimension pair leaves them linked. This port properly breaks incompatible links when runes are removed
>
</details>

### No Private Portals

The original mod's private portals feature is not included. The implementation didn't make practical sense in multiplayer. If you want to travel with a friend, what happens then? If there's real demand for portal access control, I can implement it; but a proper integration would be with something like FTB Teams.

### Redstone Reworked

The original mod had three redstone modes (off, on, no effect). This port simplifies it to a single toggle: redstone signal disables the portal. It's more intuitive (matches how vanilla redstone interacts with blocks), more useful (enables actual redstone contraptions), and the unlink/relink behavior makes multi-portal setups genuinely interesting. Again, IMO.

## Compatibility

- Requires NeoForge 1.21.1 (21.1.219+)

## Credits

Original [Custom Portals](https://modrinth.com/mod/custom-portals) mod by [Palyon](https://modrinth.com/user/Palyon-dev) (MIT licensed). This is a NeoForge reimplementation of their legendary work.

## License

GPL-3.0-or-later
