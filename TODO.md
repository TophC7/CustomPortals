# Custom Portals Foxified - TODO

## Critical

- [x] Rune block models use `cube_all` - renders as full opaque cubes instead of thin slabs. Need thin plate models (like `minecraft:block/button` or custom)
- [ ] Breaking the portal frame does not break the portal
- [ ] In creative you can break a singular portal block without breaking the whole portal
- [x] `LIT=false` has no visual - 16 `*_portal_inactive.png` textures exist but blockstate never references them. Add multipart entries for `lit=false` with inactive texture models
- [x] Orphaned `models/block/custom_portal.json` - old single-model file, no longer referenced. Delete along with `textures/block/custom_portal.png` and `.mcmeta`

## Will Fix

- [ ] Debug log spam in `PortalRegistry.tryLinkAcrossAll` - verbose log on every candidate check. Remove or downgrade to trace
- [ ] `ScreenTransitionPayload` registered but never sent - dead code. Remove or implement transition screen logic
- [ ] `CustomPortal.getTeleportDelay()` is dead code - replaced by `getPortalTransitionTime` on the block. Remove
- [ ] 3 no-op overrides in `AbstractRuneBlock` - `getStateForPlacement`, `canSurvive`, `updateShape` just call super. Remove
- [ ] Overlay progress hardcoded to `/80.0F` - wrong for creative (1 tick). Query actual transition time or clamp
- [ ] `MUTE_SOUNDS` config declared but never read anywhere
- [ ] `.randomTicks()` on portal block but no `randomTick()` override - either remove or add mob spawning logic
- [ ] Recipe ingredients use `"item"` key (1.20.x format) - results correctly use `"id"` (1.21.1) but ingredients are inconsistent
- [ ] Orphaned `textures/item/weak_enhancer_rune.png` - unreferenced by any model

## Backlog (Beta+)

- [ ] Colored particles in `animateTick` (currently TODO in code)
- [ ] Test rune blocks in-game (haste, gate, enhancer, strong enhancer, infinity)
- [ ] Test cross-dimension teleportation with gate rune
- [ ] Loading screen customization during transition
- [ ] Frame whitelist/blacklist config
- [ ] Mob spawning from portals (`randomTick`)
