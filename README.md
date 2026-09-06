# Ranged Weapons

A protocol between gun mods and the mobs that use them. NeoForge 1.21.1.

## What it is

A mob that is handed a gun cannot use it: every gun mod's own API takes a
`Player`, and vanilla AI only knows crossbows. Ranged Weapons defines what a
gun is from the point of view of a mob's AI -- how many rounds it holds, how
fast it fires, how hard it hits, how it launches a projectile -- as an
interface any gun mod can implement and any mob-arming mod can consume,
without either naming the other.

The contract is `com.nfx.rangedweapons.api`:

| Type            | What it is                                                                 |
|-----------------|----------------------------------------------------------------------------|
| `RangedWeapon`  | A gun in the hands of any `LivingEntity`: profile, per-stack stats, spend a round, fire a shot. |
| `AmmoStore`     | Anything that holds rounds -- a gun's magazine, a detachable one, a quiver. Separate from the weapon because loot magazines are stores and not weapons. |
| `WeaponStats`   | The nine numbers an AI fights with, per stack. The rep invariant is enforced in the constructor and names the field it refuses. |
| `WeaponProfile` | Per-item facts: class, default stats, ammo, magazine, sounds -- by id, resolved at use. |
| `WeaponClass`   | An open, interned category (`sidearm`, `rifle`, `shotgun`, `automatic`, ...) for consumer policy. Any mod may mint one. |
| `Shot`          | One trigger pull, fully specified, built by the consumer after its own multipliers. |

Two rules the whole design rests on. Nothing takes a `Player`. Nothing reads
the shooter's look angle -- aim is always the explicit vector in the `Shot`,
because on a mob the look angle is body yaw and lags the head while strafing.

## How an item becomes a weapon

A consumer asks one question of a stack, `RangedWeapons.resolve(stack)`, and
gets back the `RangedWeapon` that operates it or null. The answer comes from
tiers in a fixed precedence, written out in that one function rather than
left to event dispatch order. **Code that knows the item beats data
describing it** -- the precedence NeoForge itself uses for an item's own burn
time over the furnace-fuels data map.

1. **A capability.** A gun mod -- or a bridge on its behalf -- implements
   `RangedWeapon` and registers it on its items with
   `RegisterCapabilitiesEvent.registerItem(RangedWeapons.WEAPON, provider, items...)`.
   Its own projectiles, ammunition items and effects: native fidelity. A
   provider may return null for a stack it declines, and the next tier
   answers. `RangedWeapons.AMMO_STORE` is the same for something that holds
   rounds without being a weapon, a detachable magazine.
2. **A profile.** Any datapack can describe any item in the data map
   `rangedweapons:weapons`, at `data/rangedweapons/data_maps/item/weapons.json`.
   No code. Entries from every pack merge, and an entry may carry a
   `neoforge:mod_loaded` condition so one file can cover guns from several
   mods without breaking when one is absent. An item with a profile and no
   provider is operated by the **fallback tier**: `ProfiledWeapon` keeps its
   round count in the `rangedweapons:rounds` component and fires
   `ProfiledBullet`, a straight, gravity-free tracer that deals the profile's
   damage exactly and is gone after its lifetime. That is a working weapon
   with no gun mod present at all.

A natively supported gun still wants a profile: it is where a consumer reads
the class, the ammunition to drop and the sounds to play, and where a pack
retunes spread and range without touching the gun mod.

A profile's shape is flat: an optional `class`, the nine `WeaponStats` fields
in snake_case, and four optional ids -- `ammo`, `magazine`, `shot_sound`,
`far_shot_sound`. A number out of range is refused at load, naming the field.
An id that resolves to nothing is reported once per reload, naming the
weapon, the field and what will silently not happen.

```json
{ "values": { "minecraft:stick": {
    "class": "sidearm",
    "capacity": 4, "reload_ticks_per_round": 5, "fire_rate_ticks": 10,
    "damage": 5.0, "projectiles_per_shot": 1, "spread": 0.0,
    "engagement_range": 16.0, "projectile_speed": 2.0,
    "projectile_lifetime_ticks": 100 } } }
```

Spread is defined by the protocol, not by any gun mod: each projectile's
direction is the aim plus an independent uniform offset in `[-spread, +spread]`
on each axis, renormalised. The fallback tier implements exactly that
(`Spread.jitter`); a native implementation is expected to.

## What a bullet does to a block

The fallback bullet leaves a mark. Every hit throws debris of the block's own
texture back from the face, sparks too if the block is stone or metal, and
plays the block's hit sound. Then, if the shooter is allowed to, the hit
counts toward breaking the block. A block shot to pieces is destroyed, not
mined: it drops nothing (a container still spills what it held).

A block's health is its hardness times `healthPerHardness` (15 by default),
so the numbers the game already has decide: glass at 0.3 shatters at one
round of five, stone at 1.5 takes five, an iron block at 5 fifteen. Damage is
remembered per block for twenty seconds after the last hit, showing as the
vanilla crack stages, then the block heals. Blocks at or above
`bulletproofHardness` (20: obsidian, ancient debris, netherite, an ender
chest), blocks with negative hardness (bedrock, command blocks) and anything
in `#rangedweapons:bulletproof` never break; anything in
`#rangedweapons:shatters` (glass, panes, ice, glowstone) goes on the first
hit whatever its hardness. Both tags are a datapack's to extend.

Whose bullets may break anything is `breakBlocks` in
`config/rangedweapons-common.toml`: `PLAYERS` (the default; each break is
first offered to protection and claim mods as the ordinary block-break event,
and spawn protection holds), `EVERYONE` (mobs too, under the `mobGriefing`
rule), or `NOBODY` (debris and sound only). A mob armed through the protocol
shatters no windows unless the server says so.

## Holding a gun in first person

Hold My Items, if installed, takes over first-person rendering of every held
item and poses it in its own arm animation; a rifle comes out sideways, which
is why that mod's own default exclusion list is gun mods. The protocol's
client offers `HoldMyItems.excludeItems(items)`: a gun mod calls it once at
client setup with its guns, and if Hold My Items is present they are written
into its per-item exclusion list and saved, so they are held the way their
models say. Reached by reflection, absent on most installs, and every failure
is one log line and the old behaviour.

## Building

```
./gradlew build
./gradlew publishToMavenLocal
```

Consumers depend on `com.nfx.rangedweapons:rangedweapons` from Maven Local and
nest it Jar-in-Jar; the loader deduplicates it to one copy.

## Testing

Two tiers, both run by `./gradlew check` (and so by `build`):

- `./gradlew test` -- plain JUnit against everything pure: the value types,
  the codecs, the spread function. Nothing boots Minecraft. Partitions are
  written at the top of each test class.
- `./gradlew runGameTestServer` -- the tiers on a real headless server.
  Precedence against real registrations: the gametest source set registers
  the capabilities on a few vanilla items the way a gun mod would, and ships
  its own `weapons.json` giving others profiles. The fallback tier end to
  end: resolution, rounds on the stack, a bullet hitting for exactly the
  profile's damage, expiring on time, and every projectile of a multi-shot
  launched. **The server's exit code is not the assertion** --
  it is also zero when no test ran -- so the task reads the framework's own
  "All N required tests passed" line from `run/logs/latest.log` and fails
  without it. `-PskipGameTests` leaves it out of `check` for fast iteration
  on the pure tests.

## License

AGPL-3.0-or-later. The contract is licensed the same way as everything built
on it: use it and publish what you make. A mod that cannot accept that stays
compatible at the datapack tier, which touches no code.
