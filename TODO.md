# Custom Portals Foxified - TODO

## Critical

- [ ] `CustomPortalBlockEntity.tick()` polls every tick per portal block just to check LIT state — O(portalBlocks) lookups/tick/portal. Replace with event-driven push: update LIT in `link()`/`unlink()`/redstone change, remove the ticker entirely

## Will Fix

- [ ] Rune models render as flat decals — need per-pixel shaped models or opaque textures with solid backgrounds for visible 3D sides. Current textures use transparency so sides are invisible. Options: (a) custom shaped models per rune, (b) solid background textures for square plate look, (c) accept flat decal look

## Backlog (Beta+)

- [ ] Colored particles in `animateTick` (currently TODO in code)
- [ ] Loading screen customization during transition
- [ ] Frame whitelist/blacklist config
- [ ] Mob spawning from portals (`randomTick`)
