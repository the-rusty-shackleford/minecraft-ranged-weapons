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
package com.nfx.rangedweapons.gametest;

import com.nfx.rangedweapons.RangedWeaponsMod;
import com.nfx.rangedweapons.api.AmmoStore;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweapons.api.WeaponProfile;
import com.nfx.rangedweapons.api.WeaponStats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.Optional;

/**
 * A stand-in gun mod for the gametests: registers the native tier's
 * capabilities on a few vanilla items, exactly the way a real gun mod or a
 * bridge would, so precedence can be asserted against real registrations.
 *
 * <p>Only present in the gametest run -- this class lives in the gametest
 * source set, which never ships.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID)
public final class NativeTierFixture {
    private NativeTierFixture() {}

    /** A weapon that does nothing but be identifiable. */
    static final RangedWeapon NATIVE_WEAPON = new RangedWeapon() {
        private final WeaponProfile profile = new WeaponProfile(WeaponClass.get("fixture"),
                new WeaponStats(1, 0, 1, 0.0f, 1, 0.0f, 1.0f, 1.0f, 1),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        @Override
        public WeaponProfile profile() {
            return profile;
        }

        @Override
        public int capacity(ItemStack stack) {
            return 1;
        }

        @Override
        public int rounds(ItemStack stack) {
            return 1;
        }

        @Override
        public void load(ItemStack stack, int count) {}

        @Override
        public void consumeRound(ItemStack stack) {}

        @Override
        public void fire(ServerLevel level, LivingEntity shooter, ItemStack stack, Shot shot) {}

        @Override
        public String toString() {
            return "NATIVE_WEAPON";
        }
    };

    /** A store that does nothing but be identifiable. */
    static final AmmoStore NATIVE_STORE = new AmmoStore() {
        @Override
        public int capacity(ItemStack stack) {
            return 1;
        }

        @Override
        public int rounds(ItemStack stack) {
            return 0;
        }

        @Override
        public void load(ItemStack stack, int count) {}

        @Override
        public String toString() {
            return "NATIVE_STORE";
        }
    };

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Iron ingot: capability only. Feather: capability and a profile in
        // the gametest data map; the provider answers for a plain feather and
        // declines a named one, so both orders of precedence are reachable.
        event.registerItem(RangedWeapons.WEAPON,
                (stack, context) -> stack.has(DataComponents.CUSTOM_NAME) ? null : NATIVE_WEAPON,
                Items.IRON_INGOT, Items.FEATHER);
        event.registerItem(RangedWeapons.AMMO_STORE, (stack, context) -> NATIVE_STORE, Items.BUCKET);
    }
}
