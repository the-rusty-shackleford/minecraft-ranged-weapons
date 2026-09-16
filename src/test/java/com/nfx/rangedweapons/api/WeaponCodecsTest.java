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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

/**
 * Tests for the codecs on {@link WeaponStats}, {@link WeaponClass} and
 * {@link WeaponProfile}: the datapack format, which is a public contract of
 * its own.
 *
 * <p>Plain JUnit against JsonOps; no game is booted. {@link ResourceLocation}
 * is the one Minecraft type touched.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * WeaponStats.CODEC:
 *   round trip:   encode then decode equals the original
 *   missing:      one required field absent -> error naming it
 *   range:        a field below its bound (capacity 0) -> error naming it
 *   field names:  snake_case keys as documented
 * WeaponClass.CODEC:
 *   valid:        "sidearm" -> the interned SIDEARM (same instance)
 *   minted:       an unknown lowercase name decodes to a new class
 *   invalid:      "Rifle" -> error naming the string
 *   encode:       SHOTGUN -> "shotgun"
 * WeaponProfile.CODEC:
 *   full:         every field present round-trips
 *   minimal:      stats only -> class UNCLASSIFIED, every optional empty
 *   encode keys:  a full profile encodes "class" and the four ids at the top level
 *   bad class:    an invalid class string fails the whole decode
 *   grip:         one_handed / two_handed round-trip; unknown spelling is refused
 * </pre>
 */
final class WeaponCodecsTest {

    private static WeaponStats revolver() {
        return new WeaponStats(6, 10, 15, 6.0f, 1, 0.045f, 16.0f, 4.0f, 100);
    }

    private static JsonObject statsJson() {
        return JsonParser.parseString("""
                {"capacity": 6, "reload_ticks_per_round": 10, "fire_rate_ticks": 15,
                 "damage": 6.0, "projectiles_per_shot": 1, "spread": 0.045,
                 "engagement_range": 16.0, "projectile_speed": 4.0,
                 "projectile_lifetime_ticks": 100}
                """).getAsJsonObject();
    }

    private static <T> T decode(Codec<T> codec, JsonElement json) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
        return result.getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    private static <T> String decodeError(Codec<T> codec, JsonElement json) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(result.isError(), "expected a decode error");
        return result.error().orElseThrow().message();
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
    }

    // --- WeaponStats ------------------------------------------------------------

    @Test
    void statsRoundTrip() {
        assertEquals(revolver(), decode(WeaponStats.CODEC, encode(WeaponStats.CODEC, revolver())));
    }

    @Test
    void statsDecodeFromDocumentedKeys() {
        assertEquals(revolver(), decode(WeaponStats.CODEC, statsJson()));
    }

    @Test
    void statsMissingFieldNamesIt() {
        JsonObject json = statsJson();
        json.remove("capacity");
        assertTrue(decodeError(WeaponStats.CODEC, json).contains("capacity"));
    }

    @Test
    void statsOutOfRangeFieldNamesIt() {
        JsonObject json = statsJson();
        json.addProperty("capacity", 0);
        String error = decodeError(WeaponStats.CODEC, json);
        assertTrue(error.contains("capacity") || error.contains("0"), error);
    }

    // --- WeaponClass ------------------------------------------------------------

    @Test
    void classDecodesToTheInternedInstance() {
        assertSame(WeaponClass.SIDEARM, decode(WeaponClass.CODEC, JsonParser.parseString("\"sidearm\"")));
    }

    @Test
    void classDecodesAnUnknownNameByMintingIt() {
        WeaponClass minted = decode(WeaponClass.CODEC, JsonParser.parseString("\"railgun\""));
        assertEquals("railgun", minted.name());
        assertSame(minted, WeaponClass.get("railgun"));
    }

    @Test
    void classRejectsAMalformedNameByName() {
        assertTrue(decodeError(WeaponClass.CODEC, JsonParser.parseString("\"Rifle\"")).contains("Rifle"));
    }

    @Test
    void classEncodesAsItsName() {
        assertEquals("\"shotgun\"", encode(WeaponClass.CODEC, WeaponClass.SHOTGUN).toString());
    }

    // --- WeaponProfile ----------------------------------------------------------

    private static WeaponProfile fullProfile() {
        return new WeaponProfile(WeaponClass.SIDEARM, revolver(),
                Optional.of(ResourceLocation.parse("agm:small_bullet")),
                Optional.of(AmmoFamilies.SMALL),
                Optional.of(ResourceLocation.parse("agm:small_magazine")),
                Optional.of(ResourceLocation.parse("agm:revolver")),
                Optional.of(ResourceLocation.parse("agm:far_shot")),
                Optional.of(new Falloff(10.0f, 28.0f, 0.5f)));
    }

    @Test
    void profileRoundTrip() {
        assertEquals(fullProfile(), decode(WeaponProfile.CODEC, encode(WeaponProfile.CODEC, fullProfile())));
    }

    @Test
    void minimalProfileDefaultsClassAndLeavesIdsEmpty() {
        WeaponProfile profile = decode(WeaponProfile.CODEC, statsJson());
        assertSame(WeaponClass.UNCLASSIFIED, profile.weaponClass());
        assertEquals(revolver(), profile.defaults());
        assertFalse(profile.ammoItem().isPresent());
        assertFalse(profile.ammoFamily().isPresent());
        assertFalse(profile.magazineItem().isPresent());
        assertFalse(profile.shotSound().isPresent());
        assertFalse(profile.farShotSound().isPresent());
        assertFalse(profile.falloff().isPresent());
        assertFalse(profile.grip().isPresent(), "old profiles do not acquire a guessed grip");
    }

    @Test
    void profileEncodesFlatWithDocumentedKeys() {
        JsonObject json = encode(WeaponProfile.CODEC, fullProfile()).getAsJsonObject();
        assertEquals("sidearm", json.get("class").getAsString());
        assertEquals(6, json.get("capacity").getAsInt());
        assertEquals("agm:small_bullet", json.get("ammo").getAsString());
        assertEquals("#rangedweapons:ammo/small", json.get("ammo_family").getAsString(), "a tag is written with its hash");
        assertEquals("agm:small_magazine", json.get("magazine").getAsString());
        assertEquals("agm:revolver", json.get("shot_sound").getAsString());
        assertEquals("agm:far_shot", json.get("far_shot_sound").getAsString());
        assertEquals(10.0f, json.getAsJsonObject("damage_falloff").get("start").getAsFloat());
        assertEquals(0.5f, json.getAsJsonObject("damage_falloff").get("floor").getAsFloat());
    }

    @Test
    void aFalloffWithEndBeforeStartIsRefusedByTheProfile() {
        JsonObject json = statsJson();
        JsonObject falloff = new JsonObject();
        falloff.addProperty("start", 20.0); falloff.addProperty("end", 5.0); falloff.addProperty("floor", 0.5);
        json.add("damage_falloff", falloff);
        assertTrue(decodeError(WeaponProfile.CODEC, json).contains("start"), "the RI's message reaches the pack author");
    }

    @Test
    void anAmmoFamilyWithoutItsHashIsRefused() {
        JsonObject json = statsJson();
        json.addProperty("ammo_family", "rangedweapons:ammo/small");
        assertTrue(decodeError(WeaponProfile.CODEC, json).contains("Not a tag id"), "vanilla's wording: the hash is what makes it a tag");
    }

    @Test
    void anotherModsFamilyDecodesUnderTheConvention() {
        JsonObject json = statsJson();
        json.addProperty("ammo_family", "#spudgun:ammo/potato");
        assertEquals(AmmoFamilies.family("spudgun", "potato"), decode(WeaponProfile.CODEC, json).ammoFamily().orElseThrow());
    }

    @Test
    void profileWithABadClassFailsAsAWhole() {
        JsonObject json = statsJson();
        json.addProperty("class", "Side Arm");
        assertTrue(decodeError(WeaponProfile.CODEC, json).contains("Side Arm"));
    }

    @Test
    void declaredGripSurvivesTheSyncedProfileCodec() {
        for (String grip : new String[] {"one_handed", "two_handed"}) {
            JsonObject json = statsJson();
            json.addProperty("grip", grip);
            JsonObject encoded = encode(WeaponProfile.CODEC, decode(WeaponProfile.CODEC, json)).getAsJsonObject();
            assertTrue(encoded.has("grip"), "a grip must survive the server-to-client profile codec");
            assertEquals(grip, encoded.get("grip").getAsString());
        }
    }

    @Test
    void theOldProviderConstructorLeavesTheGripUndeclared() {
        assertTrue(fullProfile().grip().isEmpty());
    }

    @Test
    void aMisspelledGripIsRefusedRatherThanSilentlyLosingThePose() {
        JsonObject json = statsJson();
        json.addProperty("grip", "onehanded");
        assertTrue(decodeError(WeaponProfile.CODEC, json).contains("onehanded"));
    }
}
