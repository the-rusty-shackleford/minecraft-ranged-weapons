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
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.fallback.ProfiledWeapon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@link RangedWeapons#resolve} and {@link RangedWeapons#ammoStore} against
 * real registrations: the {@link NativeTierFixture} capabilities and the
 * gametest data map.
 *
 * <p>Partitions. Weapon: capability only / capability and profile /
 * capability declined and profile / profile only / neither / empty stack.
 * Store: store capability only / a native weapon / a profiled weapon /
 * neither / empty stack.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecedenceGameTests {

    public PrecedenceGameTests() {}

    @GameTest(template = "arena")
    public void capabilityBeatsProfileBeatsNothing(GameTestHelper helper) {
        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.IRON_INGOT)) == NativeTierFixture.NATIVE_WEAPON,
                "capability only: the provider's weapon");
        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.FEATHER)) == NativeTierFixture.NATIVE_WEAPON,
                "capability and profile: the provider's weapon");

        ItemStack declined = new ItemStack(Items.FEATHER);
        declined.set(DataComponents.CUSTOM_NAME, Component.literal("declined"));
        helper.assertTrue(RangedWeapons.resolve(declined) instanceof ProfiledWeapon,
                "provider declines the stack: the profile's weapon");

        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.STICK)) instanceof ProfiledWeapon,
                "profile only: the fallback weapon");
        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.DIAMOND)) == null,
                "neither: not a weapon");
        helper.assertTrue(RangedWeapons.resolve(ItemStack.EMPTY) == null, "the empty stack: not a weapon");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void ammoStoreHasItsOwnTierThenIsTheWeapon(GameTestHelper helper) {
        helper.assertTrue(RangedWeapons.ammoStore(new ItemStack(Items.BUCKET)) == NativeTierFixture.NATIVE_STORE,
                "store capability: the provider's store");
        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.BUCKET)) == null,
                "a store is not thereby a weapon");
        helper.assertTrue(RangedWeapons.ammoStore(new ItemStack(Items.IRON_INGOT)) == NativeTierFixture.NATIVE_WEAPON,
                "a native weapon is its own store");
        helper.assertTrue(RangedWeapons.ammoStore(new ItemStack(Items.STICK)) == RangedWeapons.resolve(new ItemStack(Items.STICK)),
                "a profiled weapon is its own store");
        helper.assertTrue(RangedWeapons.ammoStore(new ItemStack(Items.DIAMOND)) == null, "neither: no store");
        helper.assertTrue(RangedWeapons.ammoStore(ItemStack.EMPTY) == null, "the empty stack: no store");
        helper.succeed();
    }
}
