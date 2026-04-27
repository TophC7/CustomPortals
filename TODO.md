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
- [ ] Counterpart portal generation still has spaces/terrain layouts where it cannot reliably make a destination portal; revisit with behavior closer to vanilla Nether portal placement
- [ ] Counterpart placement can choose a portal too far from the expected return point; teleporting back may create/find a different portal instead of returning to the original one
- [ ] Datapack counterpart portals can currently form invalid many-to-one links; two source portals may both target one counterpart, while the counterpart returns to only one specific source
- [ ] Nearby source portals can both send players to the same Nether counterpart, causing odd asymmetric return behavior; linking should stay one-to-one or otherwise be deterministic and reversible

## Backlog (later)

- [ ] Optional config knobs for counterpart search radius and terrain modification policy
- [ ] Horizontal counterpart portals if we decide to support them
- [ ] Fixed-destination portals after counterpart portal behavior is settled
