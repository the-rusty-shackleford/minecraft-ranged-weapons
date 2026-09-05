/*
 * Ranged Weapons - a protocol between gun mods and the mobs that use them.
 * Copyright (C) 2026 nfx and contributors
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
package com.nfx.rangedweapons.fallback;

import com.nfx.rangedweapons.api.WeaponStats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The protocol's spread definition as one pure function, so the fallback
 * tier scatters projectiles exactly the way {@link WeaponStats} says every
 * implementation should.
 */
public final class Spread {
    private Spread() {}

    /** Below this squared length a jittered direction has nothing to normalise. */
    private static final double DEGENERATE_LENGTH_SQR = 1e-12;

    /**
     * One projectile's direction: the aim plus an independent uniform offset
     * in {@code [-spread, +spread]} on each axis, renormalised.
     *
     * <p>requires: {@code direction} is unit length; {@code spread >= 0} and
     * finite; each of {@code rx, ry, rz} in {@code [0, 1)}<br>
     * effects: returns the unit vector along
     * {@code direction + spread * (2r - 1)} per axis; returns
     * {@code direction} itself when {@code spread} is zero, and when the
     * offset cancels the aim exactly (possible once {@code spread >= 1}),
     * because a projectile must go somewhere
     *
     * @param direction the aim, unit length
     * @param spread    the per-axis half-width of the offset
     * @param rx        a uniform draw for the x axis
     * @param ry        a uniform draw for the y axis
     * @param rz        a uniform draw for the z axis
     * @return the perturbed unit direction
     */
    public static Vec3 jitter(Vec3 direction, float spread, double rx, double ry, double rz) {
        if (spread == 0.0f) {
            return direction;
        }
        Vec3 scattered = direction.add(
                spread * (2.0 * rx - 1.0),
                spread * (2.0 * ry - 1.0),
                spread * (2.0 * rz - 1.0));
        if (scattered.lengthSqr() < DEGENERATE_LENGTH_SQR) {
            return direction;
        }
        return scattered.normalize();
    }

    /**
     * {@link #jitter(Vec3, float, double, double, double)} with three draws
     * from {@code random}; draws nothing when {@code spread} is zero.
     *
     * @param direction the aim, unit length
     * @param spread    the per-axis half-width of the offset
     * @param random    the shooter's random
     * @return the perturbed unit direction
     */
    public static Vec3 jitter(Vec3 direction, float spread, RandomSource random) {
        if (spread == 0.0f) {
            return direction;
        }
        return jitter(direction, spread, random.nextDouble(), random.nextDouble(), random.nextDouble());
    }
}
