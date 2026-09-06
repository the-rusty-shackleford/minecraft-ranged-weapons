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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Falloff's rule.
 *
 * Partitions: distance zero / under start / at start / between / at end /
 * past end; start equal to end; floor zero / one; the RI's four edges.
 */
class FalloffTest {
    private static final Falloff SHOTGUN = new Falloff(5.0f, 18.0f, 0.2f);

    @Test
    void fullDamageUpToStart() {
        assertEquals(1.0f, SHOTGUN.factor(0.0f));
        assertEquals(1.0f, SHOTGUN.factor(4.9f));
        assertEquals(1.0f, SHOTGUN.factor(5.0f), "at start, still full");
    }

    @Test
    void floorFromEndOn() {
        assertEquals(0.2f, SHOTGUN.factor(18.0f), 1e-6, "at end, the floor");
        assertEquals(0.2f, SHOTGUN.factor(100.0f), 1e-6);
    }

    @Test
    void straightLineBetween() {
        assertEquals(0.6f, SHOTGUN.factor(11.5f), 1e-6, "halfway between start and end, halfway between 1 and the floor");
        assertEquals(0.8f, SHOTGUN.factor(8.25f), 1e-6, "a quarter of the way");
    }

    @Test
    void aStepWhenStartEqualsEnd() {
        Falloff step = new Falloff(10.0f, 10.0f, 0.5f);
        assertEquals(1.0f, step.factor(9.99f));
        assertEquals(0.5f, step.factor(10.0f));
    }

    @Test
    void floorOneMeansNoFalloffAndZeroMeansNothingAtRange() {
        assertEquals(1.0f, new Falloff(1.0f, 2.0f, 1.0f).factor(50.0f));
        assertEquals(0.0f, new Falloff(1.0f, 2.0f, 0.0f).factor(2.0f));
    }

    @Test
    void theRepresentationInvariantIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new Falloff(-1.0f, 5.0f, 0.5f), "negative start");
        assertThrows(IllegalArgumentException.class, () -> new Falloff(6.0f, 5.0f, 0.5f), "end before start");
        assertThrows(IllegalArgumentException.class, () -> new Falloff(1.0f, Float.POSITIVE_INFINITY, 0.5f), "infinite end");
        assertThrows(IllegalArgumentException.class, () -> new Falloff(1.0f, 5.0f, 1.5f), "floor above one");
        assertThrows(IllegalArgumentException.class, () -> new Falloff(1.0f, 5.0f, Float.NaN), "floor not a number");
    }
}
