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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Impact's rules.
 *
 * Partitions:
 *   bulletproof: hardness negative / NaN / zero / below ceiling / at ceiling / above
 *   health:      hardness zero / positive; perHardness invalid
 *   crackStage:  dealt zero / a tenth / just under health / rounding at the top; invalid inputs
 *   remembered:  within window / at the edge / past it / time backwards
 *   breakerId:   negative for every key; equal keys equal ids; a real spread of ids
 * Worked numbers at the shipped defaults (15 per hardness, ceiling 20), so a
 * change to the defaults changes these on purpose.
 */
class ImpactTest {

    @Test
    void negativeHardnessIsBulletproof() {
        assertTrue(Impact.bulletproof(-1.0f, 20.0f), "bedrock");
        assertTrue(Impact.bulletproof(Float.NaN, 20.0f), "not a number");
    }

    @Test
    void hardnessBelowTheCeilingIsNot() {
        assertFalse(Impact.bulletproof(0.0f, 20.0f), "instant-break blocks");
        assertFalse(Impact.bulletproof(19.99f, 20.0f), "just under");
    }

    @Test
    void hardnessAtOrAboveTheCeilingIs() {
        assertTrue(Impact.bulletproof(20.0f, 20.0f), "at");
        assertTrue(Impact.bulletproof(50.0f, 20.0f), "obsidian");
    }

    @Test
    void healthIsHardnessTimesTheRate() {
        assertEquals(4.5f, Impact.health(0.3f, 15.0f), 1e-6, "glass");
        assertEquals(22.5f, Impact.health(1.5f, 15.0f), 1e-6, "stone");
        assertEquals(75.0f, Impact.health(5.0f, 15.0f), 1e-6, "iron block");
        assertEquals(0.0f, Impact.health(0.0f, 15.0f), "an instant-break block");
    }

    @Test
    void healthRejectsBadInputs() {
        assertThrows(IllegalArgumentException.class, () -> Impact.health(-1.0f, 15.0f));
        assertThrows(IllegalArgumentException.class, () -> Impact.health(1.0f, 0.0f));
    }

    @Test
    void shippedNumbersGiveTheIntendedHitCounts() {
        float round = 5.0f;
        assertEquals(1, hitsToBreak(Impact.health(0.3f, 15.0f), round), "glass in one");
        assertEquals(5, hitsToBreak(Impact.health(1.5f, 15.0f), round), "stone in five");
        assertEquals(15, hitsToBreak(Impact.health(5.0f, 15.0f), round), "an iron block in fifteen");
    }

    @Test
    void crackStageIsTheTenthReached() {
        assertEquals(0, Impact.crackStage(0.0f, 10.0f));
        assertEquals(0, Impact.crackStage(0.99f, 10.0f));
        assertEquals(1, Impact.crackStage(1.0f, 10.0f));
        assertEquals(5, Impact.crackStage(5.0f, 10.0f));
        assertEquals(9, Impact.crackStage(9.99f, 10.0f), "never past the last texture");
    }

    @Test
    void crackStageRejectsBadInputs() {
        assertThrows(IllegalArgumentException.class, () -> Impact.crackStage(10.0f, 10.0f), "dealt at health is a break, not a stage");
        assertThrows(IllegalArgumentException.class, () -> Impact.crackStage(-1.0f, 10.0f));
        assertThrows(IllegalArgumentException.class, () -> Impact.crackStage(0.0f, 0.0f));
    }

    @Test
    void damageIsRememberedWithinTheWindowAndForgottenAfter() {
        assertEquals(7.0f, Impact.remembered(7.0f, 100, 100, 400), "the same tick");
        assertEquals(7.0f, Impact.remembered(7.0f, 100, 500, 400), "at the edge");
        assertEquals(0.0f, Impact.remembered(7.0f, 100, 501, 400), "past it");
        assertEquals(0.0f, Impact.remembered(7.0f, 100, 99, 400), "time went backwards");
    }

    @Test
    void breakerIdsAreNegativeAndStable() {
        long[] keys = {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 0x123456789abcdefL};
        for (long key : keys) {
            assertTrue(Impact.breakerId(key) < 0, "negative for " + key);
            assertEquals(Impact.breakerId(key), Impact.breakerId(key), "stable for " + key);
        }
        assertTrue(Impact.breakerId(1L) != Impact.breakerId(2L), "neighbouring keys differ");
    }

    private static int hitsToBreak(float health, float damage) {
        int hits = 0;
        float dealt = 0.0f;
        while (dealt < health) {
            dealt += damage;
            hits++;
        }
        return hits;
    }
}
