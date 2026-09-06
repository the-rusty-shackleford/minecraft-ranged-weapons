---
title: Ranged Weapons — project
type: overview
layer: store
tags: [overview]
---

# Ranged Weapons

## What this is

A NeoForge 1.21.1 mod that is mostly an interface: what a gun is from the
point of view of a mob's AI, so that a mod which arms mobs works with any gun
mod, and a gun mod works with any mob-arming mod, without either naming the
other. It ships nested inside its consumers' jars and the loader deduplicates
it to one copy.

## Why it exists

Armed Pillagers bound mobs to one gun mod at 33 call sites. Every gun mod's own
API is player-bound (`shoot(Player, ...)`), so a consumer reimplements firing
against the mod's internals and breaks on every update; and a second gun mod
means a second rewrite. The protocol makes the consumer generic once and moves
the per-gun-mod knowledge into a bridge, a datapack, or the gun mod itself.

## Shape

Three tiers behind one function, `RangedWeapons.resolve(stack)`, in fixed
precedence (`decisions/D-0001.md`):

1. A capability a gun mod registers on its items — native fidelity.
2. A datapack profile in the `rangedweapons:weapons` data map — no code; the
   protocol's own fallback tier operates the item (`decisions/D-0002.md`).
3. Neither — not a weapon.

The contract is `com.nfx.rangedweapons.api`: `RangedWeapon`, `AmmoStore`,
`WeaponStats`, `WeaponProfile`, `WeaponClass`, `Shot`. Nothing takes a
`Player`; nothing reads the shooter's look angle. Value types are records with
their rep invariant enforced in the constructor and DFU codecs carrying the
same bounds, so a bad datapack is refused at load naming the field.

## How it is verified

`./gradlew check`: plain JUnit against everything pure (no game booted) and
gametests on a headless server for everything that needs a registry — the
fallback bullet hitting for exactly the profile's damage, what it does to
glass, stone, ice and obsidian, which rounds a weapon accepts by family,
precedence against real capability registrations. The gametest task fails without the framework's
own "All N required tests passed" line; the exit code alone is not trusted.

## Consumers and providers

- Consumer: `minecraft-armed-pillagers` (nests this jar).
- Provider: `minecraft-armed-pillagers/bridges/agm` (a bridge for Another Gun
  Mod; nests this jar too).
- A gun mod written to this protocol implements `RangedWeapon` directly and
  registers `RangedWeapons.WEAPON` on its items; that is its whole integration.

## License

AGPL-3.0-or-later, deliberately, including on the interface
(`decisions/D-0003.md`).
