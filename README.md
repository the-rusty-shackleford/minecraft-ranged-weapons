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

## How a gun mod becomes compatible

Not yet -- this repository currently holds the contract only. Arriving next,
in order:

1. **A data map**, `rangedweapons:weapons`, so a datapack JSON file can describe
   any item as a weapon with zero code.
2. **A fallback tier**: the protocol's own projectile and round counter, so that
   JSON description alone makes the item fire.
3. **Capabilities**, so a gun mod (or a bridge on its behalf) can supply its own
   projectiles and ammo handling for native fidelity.

Until then the one consumer, Armed Pillagers, binds Another Gun Mod to the
contract itself.

## Building

```
./gradlew build
./gradlew publishToMavenLocal
```

Consumers depend on `com.nfx.rangedweapons:rangedweapons` from Maven Local and
nest it Jar-in-Jar; the loader deduplicates it to one copy.

## Testing

`./gradlew test` runs plain JUnit against the contract's value types. Nothing
boots Minecraft. Partitions are written at the top of each test class.

## License

AGPL-3.0-or-later. The contract is licensed the same way as everything built
on it: use it and publish what you make. A mod that cannot accept that stays
compatible at the datapack tier, which touches no code.
