# Bertie Black Hole

Turns Forbidden & Arcanus' Black Hole into a levelled machine. NeoForge 1.21.1, soft-depends on
F&A 2.6.1. Everything is driven by one JSON file: `config/bertie_blackhole.json`, written on first
launch from the bundled default.

## What is left alone

The base black hole is untouched — same ±5 block pull, same 60 XP → 1 Xpetrified Orb conversion,
same 4.0 magic damage to arrows and other affected entities, same placement recipe (drop Dark
Matter next to Corrupti Dust on air).

The single behaviour taken over is the destruction of **item** entities. F&A shreds any item that
gets within 0.6 blocks of the centre; that call site is instead where absorption happens. The
hole's own pull is the delivery mechanism, so this mod has no sweep, no timer and no radius of its
own — an item is dealt with the instant it arrives.

## How a hole levels

Each item that reaches the centre falls into one of three buckets:

1. **A counter item.** Counters are *named*, so several items can feed one counter at different
   weights — that is what lets `matter` take Dark Matter at +100 and dirt at +5. The `requires`
   block on a level entry is what must be **full** to reach that level, so the level-1 entry is
   what a freshly placed hole is collecting.
2. **An exchange input.** Banked into a buffer for that exchange.
3. **Unlisted.** Voided if `eatUnlisted` is on, otherwise left alone.

When every counter on the next level's `requires` is full, the hole transforms: the level's sound
plays, its colour takes over the particles and the aura ring, the counters reset to zero, and its
exchanges come online. Counters win over exchanges when an item appears in both lists.

A full counter **ignores** that item — not absorbed, not moved, not destroyed, it just hangs at the
centre until it despawns. `acceptOverCap` (global, overridable per counter) makes it keep absorbing
instead. A hole sitting at the highest configured level stops accepting counter items entirely.

## Exchanges

Cumulative — a level-2 hole still offers the level-1 conversions. Inputs bank up in a buffer that
survives world reload. Once the buffer covers the cost, the first output lands
`firstOutputDelayTicks` later and each further one `subsequentOutputDelayTicks` after that, until
the buffer drops below the cost. Whatever does not divide evenly stays banked — feed it 12 when
the recipe wants 8 and 4 are still there afterwards.

## Config

```jsonc
{
  "firstOutputDelayTicks": 60,        // goal reached -> first output
  "subsequentOutputDelayTicks": 20,   // and every one after that
  "acceptOverCap": false,             // true: a full counter keeps eating
  "eatUnlisted": false,               // true: restores vanilla "void everything"

  "levels": [
    {
      "level": 1,
      "requires": {
        "matter": {                       // counter name, free-form
          "max": 64,
          "acceptOverCap": false,         // optional, overrides the global
          "items": { "forbidden_arcanus:dark_matter": 1 }
        }
      },
      "sound": "minecraft:block.end_portal.spawn",
      "soundVolume": 2.0,                 // >1 widens the audible radius, it is not extra gain
      "soundPitch": 0.7,
      "particleColor": "#D01818",         // omit or null to keep the vanilla purple
      "particleShadeJitter": 0.45,        // per-particle brightness spread, 0..1
      "exchanges": [
        { "id": "eternal_stella",
          "input": "forbidden_arcanus:xpetrified_orb", "count": 8,
          "output": "forbidden_arcanus:eternal_stella", "outputCount": 1 }
      ]
    }
  ]
}
```

Item ids may be written as tags with a leading `#`, e.g. `"#c:raw_materials": 5`. An exchange
`output` must be a concrete item. Adding a `"level": 2` entry is all a second rung takes;
`"level": 0` is legal too if you want exchanges before any transformation.

Renaming an exchange `id` orphans whatever was banked under the old name in an existing world.

## Commands

- `/bertieblackhole reload` — re-read the config file (permission level 2). The file is also
  re-read on every world load.
- `/bertieblackhole info` — dump the nearest hole within 8 blocks: level, counter progress,
  banked exchange inputs, pending conversion. There is no GUI, so this is how you watch it work.

## Known behaviour worth deciding on

- **The hole will not eat its own output.** F&A keeps a `thrownOutItems` list so a black hole never
  re-swallows what it spat out, and items on it are skipped before the pull. So an Xpetrified Orb
  the hole produced from XP cannot feed the level-1 exchange until a player picks it up and drops
  it again. That blocks a fully automatic XP → Eternal Stella loop. If you want the loop, it needs
  an explicit opt-out in the mixin.
- **`#forbidden_arcanus:black_hole_unaffected` items can never reach a counter.** F&A skips them
  before the pull is even computed, so they never arrive at the centre and this mod never sees
  them. That tag is left fully intact — take an item off it with a datapack if you want a hole to
  eat it. The tag is also why nothing here needs its own protection list.
- **`eatUnlisted: false` is a change from vanilla F&A**, which voids every item that reaches the
  centre. It is the requested default; flip it to `true` for the old behaviour. With it off, a
  black hole stops being an incinerator: unwanted items hang at the centre until they despawn.
- **The particles are still the real portal particles**, just recoloured. The inward flight is
  PortalParticle lerping itself back to its spawn point, which no other particle type does, so
  they are spawned through `ParticleEngine.createParticle` and `setColor` is called on the
  returned instance. The black core model is deliberately untouched; only the aura ring is tinted.
- **Config is read per side.** The level is synced to clients, the colours are not — client and
  server each read their own file. Fine in a modpack, worth knowing on a dedicated server.
- **`block.end_portal.spawn` at pitch 0.7 is a placeholder.** Note that sound *volume* above 1.0
  only widens the audible radius (16 × volume blocks); it does not make the sound louder. A deep
  sound pitched far down can end up inaudible rather than impressive — `entity.warden.emerge` at
  pitch 0.5 was the first attempt and could not be heard.

## Maintenance

Everything here is injected into F&A internals, so an F&A update can move the ground:

| Injection | Target |
|---|---|
| `bbh$onReachCentre` | `Entity.hurt` call inside `BlackHoleBlockEntity.serverTick` — this is both the "spare the item" and the "absorb it" hook |
| `bbh$serverTick` | TAIL of `BlackHoleBlockEntity.serverTick` |
| `bbh$save` / `bbh$load` | TAIL of `saveAdditional` / `loadAdditional` |
| `bbh$recolour` | HEAD of `BlackHoleBlock.animateTick` |
| `bbh$tintAura` | 2nd `ModelPart.render` call in `BlackHoleRenderer.render` (ordinal 1 = aura) |

`required: true` + `defaultRequire: 1` means a moved injection point fails loudly at boot rather
than silently doing nothing. `BbhMixinPlugin` disables the whole config when F&A is absent, so the
jar is inert rather than fatal in a build that drops F&A.

F&A is a `compileOnly` dependency resolved from Modrinth; it is needed to compile the mixins but is
never bundled.

## Tests

`gradle test` covers config parsing, validation, cumulative exchanges, and NBT persistence.
`gradle clientTestJar` builds a test-only mod that verifies every F&A mixin target in a headless
client; it is excluded from releases.
