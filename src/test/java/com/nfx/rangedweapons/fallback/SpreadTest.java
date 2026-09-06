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
package com.nfx.rangedweapons.fallback;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Spread}.
 *
 * <p>Partitions. Spread: zero / small / at least one. Draws: all at the
 * midpoint (no offset) / at the bottom of the range / near the top / mixed.
 * Result: identical to the aim / deviates on the expected side per axis /
 * unit length regardless / the degenerate cancel. The random overload:
 * draws x, y, z in that order; draws nothing at zero spread.
 */
final class SpreadTest {

    private static final Vec3 PLUS_X = new Vec3(1, 0, 0);
    private static final double EPS = 1e-9;

    @Test
    void zeroSpreadReturnsTheAimItself() {
        assertSame(PLUS_X, Spread.jitter(PLUS_X, 0.0f, 0.0, 0.999, 0.3));
    }

    @Test
    void midpointDrawsLeaveTheAimUnchanged() {
        Vec3 out = Spread.jitter(PLUS_X, 0.25f, 0.5, 0.5, 0.5);
        assertVecEquals(PLUS_X, out);
    }

    @Test
    void aDrawAboveTheMidpointPushesThatAxisPositive() {
        Vec3 out = Spread.jitter(PLUS_X, 0.25f, 0.5, 0.999, 0.5);
        assertTrue(out.y > 0, "y should be positive, was " + out.y);
        assertEquals(0.0, out.z, EPS);
    }

    @Test
    void aDrawBelowTheMidpointPushesThatAxisNegative() {
        Vec3 out = Spread.jitter(PLUS_X, 0.25f, 0.5, 0.5, 0.0);
        assertTrue(out.z < 0, "z should be negative, was " + out.z);
        assertEquals(0.0, out.y, EPS);
    }

    @Test
    void offsetIsExactlySpreadTimesTwoRMinusOnePerAxisBeforeNormalising() {
        // aim (1,0,0), spread 0.5, draws (0.5, 1.0-ish, 0): offset (0, +0.5, -0.5)
        // -> (1, 0.5, -0.5), length sqrt(1.5).
        Vec3 out = Spread.jitter(PLUS_X, 0.5f, 0.5, 0.99999999, 0.0);
        double len = Math.sqrt(1.5);
        assertEquals(1.0 / len, out.x, 1e-6);
        assertEquals(0.5 / len, out.y, 1e-6);
        assertEquals(-0.5 / len, out.z, 1e-6);
    }

    @Test
    void resultIsUnitLengthWhateverTheDraws() {
        double[] draws = {0.0, 0.1, 0.5, 0.9, 0.999};
        for (double rx : draws) {
            for (double ry : draws) {
                for (double rz : draws) {
                    Vec3 out = Spread.jitter(PLUS_X, 0.7f, rx, ry, rz);
                    assertEquals(1.0, out.length(), EPS, "draws " + rx + "," + ry + "," + rz);
                }
            }
        }
    }

    @Test
    void anOffsetThatCancelsTheAimFallsBackToTheAim() {
        // spread 1, rx 0 -> offset -1 on x exactly cancels (1,0,0).
        Vec3 out = Spread.jitter(PLUS_X, 1.0f, 0.0, 0.5, 0.5);
        assertSame(PLUS_X, out);
    }

    @Test
    void randomOverloadDrawsXThenYThenZ() {
        RandomSource a = RandomSource.create(42L);
        RandomSource b = RandomSource.create(42L);
        Vec3 viaRandom = Spread.jitter(PLUS_X, 0.3f, a);
        Vec3 viaDraws = Spread.jitter(PLUS_X, 0.3f, b.nextDouble(), b.nextDouble(), b.nextDouble());
        assertVecEquals(viaDraws, viaRandom);
    }

    @Test
    void randomOverloadDrawsNothingAtZeroSpread() {
        RandomSource used = RandomSource.create(7L);
        RandomSource fresh = RandomSource.create(7L);
        assertSame(PLUS_X, Spread.jitter(PLUS_X, 0.0f, used));
        assertEquals(fresh.nextDouble(), used.nextDouble(), 0.0, "the stream must be untouched");
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS, "x");
        assertEquals(expected.y, actual.y, EPS, "y");
        assertEquals(expected.z, actual.z, EPS, "z");
    }
}
