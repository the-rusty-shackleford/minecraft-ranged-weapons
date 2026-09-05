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
package com.nfx.rangedweapons;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Ranged Weapons: a protocol between gun mods and the mobs that use them.
 *
 * <p>The contract lives in {@code com.nfx.rangedweapons.api}. This class is
 * the mod's presence in the loader -- a real mod rather than a bare library,
 * because the protocol will register things of its own: a data map type that
 * lets a datapack describe any item as a weapon, and a fallback projectile so
 * that description alone is enough to make it fire. Consumers nest this jar
 * inside theirs, and the loader deduplicates it to one copy, so those
 * registrations happen exactly once however many mods carry it.
 */
@Mod(RangedWeaponsMod.MOD_ID)
public final class RangedWeaponsMod {
    public static final String MOD_ID = "rangedweapons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RangedWeaponsMod(IEventBus modBus, ModContainer container) {
        // Registrations arrive with the data map and the fallback tier.
    }
}
