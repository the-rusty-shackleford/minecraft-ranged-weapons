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

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * What everyone else notices when a weapon fires: the report, near and far,
 * and a puff of smoke at the muzzle.
 *
 * <p>The {@link RangedWeapon#fire} contract leaves sound and effects to the
 * caller, so that a consumer decides the sound source (a mob's gunfire is
 * hostile, a player's is not) and so that a weapon implementation stays
 * silent and testable. Every consumer then wants the same two-layer
 * treatment: the close report through the level for everything nearby, and
 * a muffled distant report for players further out, the way a gunshot
 * carries. This is that treatment, once.
 */
public final class ShotReport {
    private ShotReport() {}

    /** Within this many blocks of the shooter the close report is what is heard. */
    public static final double NEAR_RANGE = 16.0;
    /** Beyond the near radius and up to this many blocks, the far report is heard instead. */
    public static final double FAR_RANGE = 64.0;
    /** A puff of this many smoke particles marks the muzzle. */
    private static final int SMOKE_COUNT = 3;

    /**
     * Whether a listener hears the muffled distant report of a shot rather
     * than the close one: outside the near radius but inside the far one.
     *
     * <p>requires: all arguments {@code >= 0}<br>
     * effects: returns {@code near*near < distSqr && distSqr <= far*far}<br>
     * throws: {@link IllegalArgumentException} if any argument is negative or
     * NaN
     *
     * @param distSqr squared distance from the shooter to the listener
     * @param near    radius, in blocks, within which the close report is heard
     * @param far     radius, in blocks, beyond which nothing is heard
     * @return whether this listener gets the far report
     */
    public static boolean hearsFarReport(double distSqr, double near, double far) {
        requireNonNegative("distSqr", distSqr);
        requireNonNegative("near", near);
        requireNonNegative("far", far);
        return near * near < distSqr && distSqr <= far * far;
    }

    /**
     * Plays a shot's report and marks its muzzle.
     *
     * <p>effects: plays the profile's shot sound at the shooter through the
     * level, so everything within the sound's range hears it, the shooter
     * included; sends the profile's far shot sound to every player between
     * {@link #NEAR_RANGE} and {@link #FAR_RANGE} of the shooter; spawns smoke
     * at {@code muzzle}. A sound the profile does not name, or names an id
     * that resolves to nothing, is skipped silently -- the protocol mod
     * reports unresolvable ids once per reload, which is the right place to
     * be loud about it.
     *
     * @param level   the level the shot was fired in
     * @param shooter who fired; the reports are positioned on it
     * @param profile the weapon's profile, for its sounds
     * @param muzzle  where the smoke appears
     * @param source  the sound category, the consumer's call
     * @param pitch   the pitch for both reports
     */
    public static void play(ServerLevel level, LivingEntity shooter, WeaponProfile profile, Vec3 muzzle,
                            SoundSource source, float pitch) {
        double x = shooter.getX();
        double y = shooter.getY();
        double z = shooter.getZ();

        SoundEvent near = sound(profile.shotSound());
        if (near != null) {
            level.playSound(null, x, y, z, near, source, 1.0f, pitch);
        }
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, SMOKE_COUNT, 0.02, 0.02, 0.02, 0.01);

        SoundEvent farEvent = sound(profile.farShotSound());
        if (farEvent == null) {
            return;
        }
        Holder<SoundEvent> far = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(farEvent);
        long seed = shooter.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (hearsFarReport(player.distanceToSqr(shooter), NEAR_RANGE, FAR_RANGE)) {
                player.connection.send(new ClientboundSoundPacket(far, source, x, y, z, 1.0f, pitch, seed));
            }
        }
    }

    private static SoundEvent sound(Optional<ResourceLocation> id) {
        return id.map(BuiltInRegistries.SOUND_EVENT::get).orElse(null);
    }

    private static void requireNonNegative(String name, double value) {
        // `!(value >= 0)` rather than `value < 0` so that NaN fails too.
        if (!(value >= 0)) {
            throw new IllegalArgumentException(name + " must be >= 0, was " + value);
        }
    }
}
