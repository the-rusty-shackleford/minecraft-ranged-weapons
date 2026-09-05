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
left to event dispatch order:

1. **A profile.** Any datapack can describe any item in the data map
   `rangedweapons:weapons`, at `data/<namespace>/data_maps/item/weapons.json`.
   No code. Entries from every pack merge, and an entry may carry a
   `neoforge:mod_loaded` condition so one file can cover guns from several
   mods without breaking when one is absent. An item with a profile is
   operated by the **fallback tier**: `ProfiledWeapon` keeps its round count
   in the `rangedweapons:rounds` component and fires `ProfiledBullet`, a
   straight, gravity-free tracer that deals the profile's damage exactly and
   is gone after its lifetime. That is a working weapon with no gun mod
   present at all.
2. *(next)* **A capability**, so a gun mod -- or a bridge on its behalf -- can
   supply its own projectiles, ammunition items and effects for native
   fidelity. Code that knows the item beats data describing it.

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
- `./gradlew runGameTestServer` -- the fallback tier on a real headless
  server: resolution, rounds on the stack, a bullet hitting for exactly the
  profile's damage, expiring on time, and every projectile of a multi-shot
  launched. The gametest source set ships its own `weapons.json` giving three
  vanilla items profiles. **The server's exit code is not the assertion** --
  it is also zero when no test ran -- so the task reads the framework's own
  "All N required tests passed" line from `run/logs/latest.log` and fails
  without it. `-PskipGameTests` leaves it out of `check` for fast iteration
  on the pure tests.

## License

AGPL-3.0-or-later. The contract is licensed the same way as everything built
on it: use it and publish what you make. A mod that cannot accept that stays
compatible at the datapack tier, which touches no code.
