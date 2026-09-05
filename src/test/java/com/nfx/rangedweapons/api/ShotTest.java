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
package com.nfx.rangedweapons.api;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Shot}.
 *
 * <p>Partitions. RI, per field: direction at unit length / just inside the
 * tolerance / outside it; count 0 / 1; speed 0 / negative / infinite /
 * positive; spread negative / infinite / 0; damage negative / 0; lifetime
 * 0 / 1; null origin / null direction. {@link Shot#of}: a long direction is
 * normalised; the zero direction is refused; every number is carried from
 * the stats unchanged.
 */
final class ShotTest {

    private static final Vec3 ORIGIN = new Vec3(1, 2, 3);
    private static final Vec3 UNIT = new Vec3(0, 0, 1);

    private static WeaponStats stats() {
        return new WeaponStats(6, 13, 15, 6.0f, 3, 0.045f, 16.0f, 4.0f, 100);
    }

    @Test
    void aUnitDirectionIsAccepted() {
        Shot shot = new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, 0.0f, 1);
        assertEquals(UNIT, shot.direction());
    }

    @Test
    void aDirectionJustInsideTheToleranceIsAccepted() {
        new Shot(ORIGIN, new Vec3(0, 0, 1.0005), 1, 1.0f, 0.0f, 0.0f, 1);
    }

    @Test
    void aDirectionOutsideTheToleranceIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Shot(ORIGIN, new Vec3(0, 0, 1.01), 1, 1.0f, 0.0f, 0.0f, 1));
        assertTrue(e.getMessage().contains("unit length"), e.getMessage());
    }

    @Test
    void countBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 0, 1.0f, 0.0f, 0.0f, 1));
        new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, 0.0f, 1);
    }

    @Test
    void speedMustBeFiniteAndPositive() {
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 1, 0.0f, 0.0f, 0.0f, 1));
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 1, -1.0f, 0.0f, 0.0f, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Shot(ORIGIN, UNIT, 1, Float.POSITIVE_INFINITY, 0.0f, 0.0f, 1));
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 1, Float.NaN, 0.0f, 0.0f, 1));
        new Shot(ORIGIN, UNIT, 1, Float.MIN_VALUE, 0.0f, 0.0f, 1);
    }

    @Test
    void spreadMustBeFiniteAndNonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 1, 1.0f, -0.1f, 0.0f, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Shot(ORIGIN, UNIT, 1, 1.0f, Float.POSITIVE_INFINITY, 0.0f, 1));
        new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, 0.0f, 1);
    }

    @Test
    void damageMustBeFiniteAndNonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, -1.0f, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, Float.NEGATIVE_INFINITY, 1));
        new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, 0.0f, 1);
    }

    @Test
    void lifetimeBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, 0.0f, 0));
        new Shot(ORIGIN, UNIT, 1, 1.0f, 0.0f, 0.0f, 1);
    }

    @Test
    void nullVectorsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Shot(null, UNIT, 1, 1.0f, 0.0f, 0.0f, 1));
        assertThrows(IllegalArgumentException.class, () -> new Shot(ORIGIN, null, 1, 1.0f, 0.0f, 0.0f, 1));
    }

    @Test
    void ofNormalisesALongDirection() {
        Shot shot = Shot.of(stats(), ORIGIN, new Vec3(0, 30, 40));
        assertEquals(0.0, shot.direction().x, 1e-12);
        assertEquals(0.6, shot.direction().y, 1e-12);
        assertEquals(0.8, shot.direction().z, 1e-12);
        assertEquals(ORIGIN, shot.origin());
    }

    @Test
    void ofRefusesTheZeroDirection() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Shot.of(stats(), ORIGIN, Vec3.ZERO));
        assertTrue(e.getMessage().contains("zero"), e.getMessage());
    }

    @Test
    void ofCarriesEveryNumberFromTheStats() {
        WeaponStats stats = stats();
        Shot shot = Shot.of(stats, ORIGIN, UNIT);
        assertEquals(stats.projectilesPerShot(), shot.count());
        assertEquals(stats.projectileSpeed(), shot.speed());
        assertEquals(stats.spread(), shot.spread());
        assertEquals(stats.damage(), shot.damage());
        assertEquals(stats.projectileLifetimeTicks(), shot.lifetimeTicks());
    }
}
