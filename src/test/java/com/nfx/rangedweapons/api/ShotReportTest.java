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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pure rule in {@link ShotReport}; the playback itself needs a
 * level and is exercised by the consumers' gametests.
 *
 * <p>Partitions. Distance: inside the near radius / exactly at it / just
 * outside it / exactly at the far radius / beyond it. Arguments: negative /
 * NaN for each of the three.
 */
final class ShotReportTest {

    private static final double NEAR = 16.0;
    private static final double FAR = 64.0;

    @Test
    void insideTheNearRadiusHearsTheCloseReport() {
        assertFalse(ShotReport.hearsFarReport(100.0, NEAR, FAR));
    }

    @Test
    void exactlyAtTheNearRadiusStillHearsTheCloseReport() {
        assertFalse(ShotReport.hearsFarReport(NEAR * NEAR, NEAR, FAR));
    }

    @Test
    void justOutsideTheNearRadiusHearsTheFarReport() {
        assertTrue(ShotReport.hearsFarReport(NEAR * NEAR + 1.0, NEAR, FAR));
    }

    @Test
    void exactlyAtTheFarRadiusStillHearsTheFarReport() {
        assertTrue(ShotReport.hearsFarReport(FAR * FAR, NEAR, FAR));
    }

    @Test
    void beyondTheFarRadiusHearsNothing() {
        assertFalse(ShotReport.hearsFarReport(FAR * FAR + 1.0, NEAR, FAR));
    }

    @Test
    void negativeArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShotReport.hearsFarReport(-1.0, NEAR, FAR));
        assertThrows(IllegalArgumentException.class, () -> ShotReport.hearsFarReport(100.0, -NEAR, FAR));
        assertThrows(IllegalArgumentException.class, () -> ShotReport.hearsFarReport(100.0, NEAR, -FAR));
    }

    @Test
    void nanIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShotReport.hearsFarReport(Double.NaN, NEAR, FAR));
    }

    @Test
    void theDefaultRadiiAreTheOnesTheGunMobsWereTunedWith() {
        assertTrue(ShotReport.NEAR_RANGE == 16.0 && ShotReport.FAR_RANGE == 64.0);
    }
}
