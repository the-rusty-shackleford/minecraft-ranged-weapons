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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AmmoProfile}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * applyTo:     an empty entry changes nothing / one field named / every field named
 *              / the magazine and rates are never the round's to change
 * falloffOr:   named / not named
 * isEmpty:     NONE / anything named
 * codec:       an empty object decodes to NONE / a slug's entry round-trips
 *              / an out-of-range number is refused (zero projectiles, negative damage)
 * RI:          a null field -> IAE
 * </pre>
 */
final class AmmoProfileTest {

    private static WeaponStats shotgun() {
        return new WeaponStats(6, 8, 13, 4.0f, 6, 0.07f, 12.0f, 2.5f, 40, 3.0f);
    }

    private static AmmoProfile slug() {
        return new AmmoProfile(Optional.of(18.0f), Optional.of(1), Optional.of(0.01f), Optional.of(30.0f),
                Optional.of(4.0f), Optional.of(80), Optional.of(3.0f), Optional.of(new Falloff(12.0f, 30.0f, 0.4f)));
    }

    @Test
    void anEmptyEntryChangesNothing() {
        assertEquals(shotgun(), AmmoProfile.NONE.applyTo(shotgun()));
        assertTrue(AmmoProfile.NONE.isEmpty());
        assertEquals(Optional.empty(), AmmoProfile.NONE.falloffOr(Optional.empty()));
    }

    @Test
    void aNamedFieldReplacesOnlyItself() {
        AmmoProfile heavier = new AmmoProfile(Optional.of(9.0f), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        WeaponStats stats = heavier.applyTo(shotgun());
        assertEquals(9.0f, stats.damage());
        assertEquals(shotgun().withDamage(9.0f), stats);
        assertFalse(heavier.isEmpty());
    }

    @Test
    void aSlugTurnsBuckshotIntoOneHeavyRound() {
        WeaponStats stats = slug().applyTo(shotgun());
        assertEquals(1, stats.projectilesPerShot());
        assertEquals(18.0f, stats.damage());
        assertEquals(0.01f, stats.spread());
        assertEquals(30.0f, stats.engagementRange());
        assertEquals(4.0f, stats.projectileSpeed());
        assertEquals(80, stats.projectileLifetimeTicks());
        assertEquals(3.0f, stats.knockback());
        // The gun's own: how many it holds, how fast it cycles and reloads.
        assertEquals(6, stats.capacity());
        assertEquals(8, stats.reloadTicksPerRound());
        assertEquals(13, stats.fireRateTicks());
        assertEquals(Optional.of(new Falloff(12.0f, 30.0f, 0.4f)), slug().falloffOr(Optional.of(new Falloff(5.0f, 18.0f, 0.2f))));
        assertEquals(Optional.of(new Falloff(5.0f, 18.0f, 0.2f)), AmmoProfile.NONE.falloffOr(Optional.of(new Falloff(5.0f, 18.0f, 0.2f))));
    }

    @Test
    void theCodecReadsAnEmptyObjectAndRoundTripsASlug() {
        assertEquals(AmmoProfile.NONE, AmmoProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow(msg -> new AssertionError(msg)));
        JsonElement json = AmmoProfile.CODEC.encodeStart(JsonOps.INSTANCE, slug()).getOrThrow(msg -> new AssertionError(msg));
        assertEquals(slug(), AmmoProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new AssertionError(msg)));
        assertTrue(json.getAsJsonObject().has("projectiles_per_shot"));
        assertTrue(json.getAsJsonObject().has("damage_falloff"));
    }

    @Test
    void theCodecRefusesNumbersTheStatsWouldRefuse() {
        assertTrue(AmmoProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"projectiles_per_shot\": 0}")).isError());
        assertTrue(AmmoProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"damage\": -1}")).isError());
        assertTrue(AmmoProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"knockback\": -0.5}")).isError());
    }

    @Test
    void noFieldMayBeNull() {
        assertThrows(IllegalArgumentException.class, () -> new AmmoProfile(null, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
