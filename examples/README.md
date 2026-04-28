# Custom Portals Foxified — Datapacks

This folder documents the datapack format Custom Portals Foxified accepts, and ships two ready-to-use example packs you can drop into a world.

- [`datapacks/stick_leaves_brown_portal/`](datapacks/stick_leaves_brown_portal/) — brown portals built from any leaf block, lit with sticks. Demonstrates **block tags** as a frame, an **allowlist/blocklist**, and the standard `linked_pair` link mode.
- [`datapacks/nether_counterpart_portal/`](datapacks/nether_counterpart_portal/) — crying obsidian portals lit with flint and steel that auto-generate a counterpart in the Nether. Demonstrates the `scaled_dimension_counterpart` link mode and the `damage` catalyst use mode.

Drop either folder into your world's `datapacks/` directory (or zip it up) and run `/reload`.

---

## Where definitions live

Portal definitions are JSON files placed at:

```
data/<namespace>/custom_portals_foxified/portal_definitions/<name>.json
```

The `<namespace>` is your own — pick anything (`mypack`, `cpf_examples`, etc.). The mod scans every namespace.

Frame allowlist / blocklist tags use the mod's own namespace:

```
data/custom_portals_foxified/tags/block/portal_frame_allowlist.json
data/custom_portals_foxified/tags/block/portal_frame_blocklist.json
```

A datapack also needs a `pack.mcmeta` at its root (see the example packs).

---

## Portal definition schema

Minimal definition:

```json
{
  "color": "brown",
  "frame": { "tag": "minecraft:leaves" },
  "catalyst": { "item": "minecraft:stick" }
}
```

Full schema (every field with notes):

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `color` | string | yes | — | One of the 16 vanilla dye colors: `white`, `orange`, `magenta`, `light_blue`, `yellow`, `lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`, `red`, `black`. |
| `frame` | object | yes | — | See [Frame selector](#frame-selector). |
| `catalyst` | object | yes | — | See [Catalyst selector](#catalyst-selector). |
| `link_mode` | string | no | `linked_pair` | Either `linked_pair` (matching pairs find each other) or `scaled_dimension_counterpart` (auto-generates the other side). |
| `destination` | object | when `link_mode` is `scaled_dimension_counterpart` | — | See [Counterpart route](#counterpart-route). Forbidden for `linked_pair`. |
| `priority` | int | no | `0` | When more than one definition matches a frame + catalyst, the highest priority wins. Ties break on selector specificity (exact block beats tag, exact item beats tag). |

### Frame selector

Pick **one** of:

```json
{ "block": "minecraft:crying_obsidian" }
```

or

```json
{ "tag": "minecraft:leaves" }
```

A `tag` lets multiple block types qualify, but each individual frame still has to be built out of a single block type — you can't mix-and-match leaf species in one frame.

Optional flag:

| Field | Type | Default | Notes |
|---|---|---|---|
| `exclusive` | bool | `false` | When `true`, this frame block is reserved for this portal: the built-in 16 colored portals can no longer use it. |

### Catalyst selector

Pick **one** of:

```json
{ "item": "minecraft:stick" }
```

or

```json
{ "tag": "minecraft:swords" }
```

Optional fields control how the catalyst is consumed when the portal is lit:

| Field | Type | Default | Notes |
|---|---|---|---|
| `use` | string | `consume` | `consume` (shrinks the stack), `damage` (durability hit, breaks on zero), or `none` (catalyst is unaffected). |
| `amount` | int | `1` | How many to consume, or how much durability to deduct. `0` is allowed and means "match-only, no cost". |
| `return_on_break` | bool | `true` | If the portal is broken (frame destroyed), the catalyst is dropped back. Only applies when `use` is `consume`. |

### Counterpart route

Required when `link_mode` is `scaled_dimension_counterpart`. The portal will auto-generate its counterpart on the other side when first lit, similar to vanilla nether portals.

| Field | Type | Default | Notes |
|---|---|---|---|
| `dimension_a` | string (dimension id) | — | One end of the route. |
| `dimension_b` | string (dimension id) | — | The other end. Must differ from `dimension_a`. |
| `coordinate_scale` | number | `1.0` | Multiplier from `dimension_a` to `dimension_b`. Vanilla nether is `8.0`. Must be positive. |
| `search_radius` | int | `16` | Horizontal blocks to search for an existing portal before generating one. |
| `vertical_search_radius` | int | `8` | Vertical blocks to search. |
| `max_autogen_portal_width` | int | `5` | Cap on auto-generated portal width. |
| `max_autogen_portal_height` | int | `5` | Cap on auto-generated portal height. |
| `max_autogen_portal_area` | int | `25` | Cap on width × height. |

The route is bidirectional: a portal lit in `dimension_a` searches `dimension_b` and vice versa, with the scale inverted automatically.

---

## Restricting the built-in 16 colored portals

By default, the built-in catalyst-lit portals work with any solid block. Two tags and the `exclusive` flag let you control this:

### Blocklist

`data/custom_portals_foxified/tags/block/portal_frame_blocklist.json`

Any block (or block tag) added here **cannot** be used as a frame for the built-in portals.

```json
{
  "replace": false,
  "values": [
    "minecraft:bedrock",
    "minecraft:oak_leaves",
    "#minecraft:fire"
  ]
}
```

### Allowlist

`data/custom_portals_foxified/tags/block/portal_frame_allowlist.json`

If this tag has **any** entries, the built-in portals can **only** be built from blocks in it. An empty allowlist is treated as "no restriction".

```json
{
  "replace": false,
  "values": [
    "minecraft:polished_diorite",
    "minecraft:obsidian",
    "#minecraft:logs"
  ]
}
```

### Exclusive custom portals

Setting `"exclusive": true` on a custom portal's `frame` claims that block (or every block in that tag) for the custom portal — the built-in 16 colors stop accepting it. Useful when you want, say, every leaf block to *only* form your brown leaf portal.

---

## Match priority

When a frame + catalyst combination could be claimed by more than one definition (custom or built-in), the mod picks a winner:

1. Highest `priority` wins.
2. On a tie, more specific selectors win — exact block beats block tag, exact item beats item tag.
3. The built-in 16 colored portals participate at priority `0`, which is why custom portals using `priority: 10` or higher reliably take precedence.

---

## Examples in this folder

### `stick_leaves_brown_portal`

```json
{
  "color": "brown",
  "frame": { "tag": "minecraft:leaves", "exclusive": true },
  "catalyst": { "item": "minecraft:stick", "use": "consume", "amount": 1, "return_on_break": false },
  "link_mode": "linked_pair",
  "priority": 10
}
```

- Any leaf block forms the frame (the `minecraft:leaves` tag).
- A stick lights it; the stick is consumed and is **not** returned if the portal is broken.
- `exclusive` reserves leaf blocks — the 16 built-in colored portals will refuse to work with leaves once this pack is loaded.
- Pack also ships an example allowlist + blocklist so you can see how those interact.

### `nether_counterpart_portal`

```json
{
  "color": "red",
  "frame": { "block": "minecraft:crying_obsidian", "exclusive": true },
  "catalyst": { "item": "minecraft:flint_and_steel", "use": "damage", "amount": 1, "return_on_break": false },
  "link_mode": "scaled_dimension_counterpart",
  "destination": {
    "dimension_a": "minecraft:overworld",
    "dimension_b": "minecraft:the_nether",
    "coordinate_scale": 8.0,
    "search_radius": 16,
    "vertical_search_radius": 8,
    "max_autogen_portal_width": 5,
    "max_autogen_portal_height": 5,
    "max_autogen_portal_area": 25
  },
  "priority": 20
}
```

- Crying obsidian frame, lit with flint and steel.
- `use: damage` consumes 1 durability per light instead of removing an item.
- Behaves like a vanilla nether portal: light one in the Overworld, the counterpart auto-generates in the Nether at 1/8 the coordinates (and vice versa).
