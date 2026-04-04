# Custom Portals Foxified - TODO

## Will Fix

- [ ] Rune models render as flat decals - need per-pixel shaped models or opaque textures with solid backgrounds for visible 3D sides. Current textures use transparency so sides are invisible. Options: (a) custom shaped models per rune, (b) solid background textures for square plate look, (c) accept flat decal look
- [ ] Gate Rune should be uncraftable/hidden when `allowCrossDimension` is false (disable recipe + remove from creative tab)

## Code Cleanup (done)

- [x] `PortalSavedData.registry(level)` shorthand replaces `.get(level).getRegistry()` at 8 pure-lookup sites
- [x] `PortalSavedData.resolveLinkedPartner()` utility replaces 3 duplicated 5-line partner resolution patterns
- [x] `unlinkFrom(partner)` bilateral unlink added alongside unilateral `unlink()`
- [x] Per-color index in PortalRegistry (`getByColor`) - `tryLinkAcrossAll` now O(P_color) instead of O(P)
- [x] `LinkedHashSet` in PortalRegistry - O(1) portal removal
- [x] `MIN_PORTAL_SIZE` config (COMMON, default 1, range 1-900) with config screen slider

## Backlog (later)

- [ ] Frame whitelist/blacklist config
- [ ] Nether-to-nether range gets implicit 8x boost - both coords unscaled, design question whether this is intended

## Fixed (code review audit)

- [x] `CustomPortal.load()` - corrupted NBT (bad ResourceLocation) crashed world load; now returns null, PortalRegistry skips bad entries
- [x] `PortalEventHandler` - static `commonConfigValidated` flag survived JVM server restarts; now resets on `ServerStoppedEvent`
- [x] Rune types (haste/gate/infinity) used booleans instead of counters - removing one of N identical runes incorrectly disabled the effect; now all rune types use int counters with backward-compatible deserialization
- [x] `tryLinkAcrossAll` picked first-encountered portal, not closest - now scans all candidates and links to minimum distance
- [x] Defensive null checks on `getLinkedDimension()` in AbstractRuneBlock and CustomPortalBlock neighborChanged
