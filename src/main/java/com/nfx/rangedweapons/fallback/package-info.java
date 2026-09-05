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

/**
 * The fallback tier: a working weapon for any item that has a profile and
 * nothing else.
 *
 * <p>A datapack entry in {@code rangedweapons:weapons} is enough to make a
 * mob shoot an item. {@link com.nfx.rangedweapons.fallback.ProfiledWeapon}
 * keeps the round count in a data component of its own and launches
 * {@link com.nfx.rangedweapons.fallback.ProfiledBullet}, a gravity-free
 * projectile that deals the profile's damage exactly and is gone after its
 * lifetime. No gun mod is involved, so this is also what a consumer's own
 * tests fire when no gun mod is on the classpath: a real backend, not a mock.
 *
 * <p>A gun mod that wants its own bullets, ammunition items and effects
 * implements {@link com.nfx.rangedweapons.api.RangedWeapon} instead, and
 * {@link com.nfx.rangedweapons.api.RangedWeapons#resolve} prefers that.
 */
package com.nfx.rangedweapons.fallback;
