# Custom Portals Foxified - TODO

## Will Fix

- [ ] Rune models render as flat decals - need per-pixel shaped models or opaque textures with solid backgrounds for visible 3D sides. Current textures use transparency so sides are invisible. Options: (a) custom shaped models per rune, (b) solid background textures for square plate look, (c) accept flat decal look
- [ ] Gate Rune should be uncraftable/hidden when `allowCrossDimension` is false (disable recipe + remove from creative tab)

## Backlog (Beta+)

- [ ] Frame whitelist/blacklist config
- [ ] Extract `resolveLinkedPartner` + `unlinkPair` utilities - dedup 3-4 copy-paste unlink/relink sites across CustomPortalBlock, PortalRegistry, AbstractRuneBlock
- [ ] Add `PortalSavedData.registry(ServerLevel)` shorthand - `.get(level).getRegistry()` chain repeated 11+ times
- [ ] Per-color index in `PortalRegistry` - `Map<DyeColor, List<CustomPortal>>` for O(P_color) linking instead of O(P) full scan; matters at 500+ portals
- [ ] Replace `ArrayList` with `LinkedHashSet` in `PortalRegistry.portals` - O(1) removal instead of O(n)
- [ ] Use `MutableBlockPos` in DFS flood fill - avoids `BlockPos` allocation per neighbor (3600+ objects at max portal size)
- [ ] Make `unlink()` bilateral or rename to `clearLink()` - currently asymmetric with `link()` (bilateral), maintenance hazard
- [ ] Validate minimum portal dimensions (1w × 2h) - prevents 1-tall portals that trap entities on arrival
- [ ] Nether-to-nether range gets implicit 8x boost - both coords unscaled, design question whether this is intended

## Fixed (code review audit)

- [x] `CustomPortal.load()` - corrupted NBT (bad ResourceLocation) crashed world load; now returns null, PortalRegistry skips bad entries
- [x] `PortalEventHandler` - static `commonConfigValidated` flag survived JVM server restarts; now resets on `ServerStoppedEvent`
- [x] Rune types (haste/gate/infinity) used booleans instead of counters - removing one of N identical runes incorrectly disabled the effect; now all rune types use int counters with backward-compatible deserialization
- [x] `tryLinkAcrossAll` picked first-encountered portal, not closest - now scans all candidates and links to minimum distance
- [x] Defensive null checks on `getLinkedDimension()` in AbstractRuneBlock and CustomPortalBlock neighborChanged
