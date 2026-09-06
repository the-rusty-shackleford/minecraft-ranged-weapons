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
package com.nfx.rangedweapons.client.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The merge behind the exclusion list.
 *
 * Partitions: existing empty / not; wanted empty / all new / all present /
 * mixed; case differs; duplicates within wanted; order of the result.
 */
class HoldMyItemsTest {

    @Test
    void addsOnlyWhatIsMissingInOrder() {
        assertEquals(List.of("a:x", "b:y", "c:z"),
                HoldMyItems.merge(List.of("a:x"), List.of("b:y", "a:x", "c:z")));
    }

    @Test
    void emptyEitherWay() {
        assertEquals(List.of("b:y"), HoldMyItems.merge(List.of(), List.of("b:y")));
        assertEquals(List.of("a:x"), HoldMyItems.merge(List.of("a:x"), List.of()));
    }

    @Test
    void presenceIsCaseInsensitiveAndKeepsTheExistingSpelling() {
        assertEquals(List.of("Mod:Gun"), HoldMyItems.merge(List.of("Mod:Gun"), List.of("mod:gun")));
    }

    @Test
    void duplicatesInWantedAppearOnce() {
        assertEquals(List.of("b:y"), HoldMyItems.merge(List.of(), List.of("b:y", "B:Y", "b:y")));
    }
}
