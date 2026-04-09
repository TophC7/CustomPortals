# Custom Portals Foxified - TODO

## Will Fix

- [ ] Rune models render as flat decals - need per-pixel shaped models or opaque textures with solid backgrounds for visible 3D sides. Current textures use transparency so sides are invisible. Options: (a) custom shaped models per rune, (b) solid background textures for square plate look, (c) accept flat decal look
- [ ] Gate Rune should be uncraftable/hidden when `allowCrossDimension` is false (disable recipe + remove from creative tab)

## Next Up

- [ ] Nether-style dimension counterpart portals
- [ ] Portal definition destination modes beyond `linked_pair`
- [ ] Coordinate transform rules per destination mode and dimension pair
- [ ] Search for an existing counterpart portal near the transformed destination
- [ ] Safe counterpart portal placement when no existing destination portal is found
- [ ] Clear failure behavior when a safe counterpart portal cannot be found or created

## Backlog (later)

- [ ] Optional config knobs for counterpart search radius and terrain modification policy
- [ ] Horizontal counterpart portals if we decide to support them
- [ ] Fixed-destination portals after counterpart portal behavior is settled
