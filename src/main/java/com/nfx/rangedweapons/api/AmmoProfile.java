/*
 * Ranged Weapons - a protocol between gun mods and the mobs that use them.
 * Copyright (C) 2026 nfx and Rusty Shackleford
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.nfx.rangedweapons.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * What a round of ammunition changes about the weapon that fires it: the
 * {@code rangedweapons:ammo} data map, keyed by the ammunition item.
 *
 * <p>A weapon's profile describes it with its native round loaded. A round
 * of the same family with an entry here overrides the numbers it names --
 * a slug fires one heavy projectile from the same shotgun that fires six
 * pellets of buckshot -- and leaves the rest as the weapon has them. Every
 * field is optional; an empty entry changes nothing.
 *
 * <p>RI: no field null (an absent value is an empty Optional).
 *
 * @param damage             per projectile
 * @param projectilesPerShot projectiles launched per trigger pull
 * @param spread             angular scatter per projectile, as {@link WeaponStats#spread()}
 * @param engagementRange    the range a mob closes to, in blocks
 * @param projectileSpeed    blocks per tick
 * @param projectileLifetime ticks before a projectile that hits nothing is gone
 * @param knockback          the push a full hit gives, as {@link WeaponStats#knockback()}
 * @param falloff            damage falloff with distance, replacing the weapon's
 */
public record AmmoProfile(Optional<Float> damage, Optional<Integer> projectilesPerShot, Optional<Float> spread,
                          Optional<Float> engagementRange, Optional<Float> projectileSpeed,
                          Optional<Integer> projectileLifetime, Optional<Float> knockback, Optional<Falloff> falloff) {

    public static final Codec<AmmoProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("damage").forGetter(AmmoProfile::damage),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("projectiles_per_shot").forGetter(AmmoProfile::projectilesPerShot),
            Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("spread").forGetter(AmmoProfile::spread),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("engagement_range").forGetter(AmmoProfile::engagementRange),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("projectile_speed").forGetter(AmmoProfile::projectileSpeed),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("projectile_lifetime_ticks").forGetter(AmmoProfile::projectileLifetime),
            Codec.floatRange(0.0f, Float.MAX_VALUE).optionalFieldOf("knockback").forGetter(AmmoProfile::knockback),
            Falloff.CODEC.optionalFieldOf("damage_falloff").forGetter(AmmoProfile::falloff)
    ).apply(i, AmmoProfile::new));

    /** An entry that changes nothing. */
    public static final AmmoProfile NONE = new AmmoProfile(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public AmmoProfile {
        if (damage == null || projectilesPerShot == null || spread == null || engagementRange == null
                || projectileSpeed == null || projectileLifetime == null || knockback == null || falloff == null) {
            throw new IllegalArgumentException("no field of an AmmoProfile may be null");
        }
    }

    /**
     * effects: returns {@code stats} with every number this entry names
     * replaced by it, the others as they were
     */
    public WeaponStats applyTo(WeaponStats stats) {
        return new WeaponStats(stats.capacity(), stats.reloadTicksPerRound(), stats.fireRateTicks(),
                damage.orElse(stats.damage()), projectilesPerShot.orElse(stats.projectilesPerShot()),
                spread.orElse(stats.spread()), engagementRange.orElse(stats.engagementRange()),
                projectileSpeed.orElse(stats.projectileSpeed()), projectileLifetime.orElse(stats.projectileLifetimeTicks()),
                knockback.orElse(stats.knockback()));
    }

    /** effects: returns this entry's falloff if it names one, else {@code weapons} */
    public Optional<Falloff> falloffOr(Optional<Falloff> weapons) {
        return falloff.isPresent() ? falloff : weapons;
    }

    /** effects: returns whether this entry changes anything at all */
    public boolean isEmpty() {
        return this.equals(NONE);
    }
}
