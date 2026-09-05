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

import com.nfx.rangedweapons.fallback.ProfiledWeapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The protocol's registry objects and the lookups over them.
 *
 * <p>{@link #WEAPONS} is the data map that lets a datapack describe any item
 * as a weapon with no code at all. Every mod and pack ships its entries at
 * {@code data/<namespace>/data_maps/item/weapons.json}; the loader merges
 * them, later entries for the same item winning, and an entry may be guarded
 * with a {@code neoforge:mod_loaded} condition so one file can describe guns
 * from several mods without failing to load when one is absent.
 *
 * <p>{@link #resolve} is the one question a consumer asks of a stack: "is
 * this a weapon, and who operates it?". It is one explicit function with its
 * tiers written out in precedence order, rather than a set of providers
 * whose order depends on cross-mod event dispatch, so the answer is the
 * same whichever mods are loaded. This is the one place the contract package
 * reaches down into an implementation: the fallback tier is part of the
 * protocol's promise, not an optional extra.
 */
public final class RangedWeapons {
    private RangedWeapons() {}

    /**
     * effects: returns the weapon behind {@code stack}, or null if it is
     * empty or no tier claims it. Currently one tier: an item with a
     * {@link #WEAPONS} profile is operated by the fallback
     * {@link ProfiledWeapon}. Two lookups, no allocation on the steady
     * state: cheap enough to call every tick.
     *
     * @param stack the stack in question
     * @return its weapon, or null
     */
    @Nullable
    public static RangedWeapon resolve(ItemStack stack) {
        if (stack.isEmpty() || stack.getItemHolder().getData(WEAPONS) == null) {
            return null;
        }
        return ProfiledWeapon.of(stack.getItem());
    }

    /**
     * effects: returns whether {@link #resolve} would find a weapon
     *
     * @param stack the stack in question
     * @return whether it is a weapon
     */
    public static boolean isWeapon(ItemStack stack) {
        return resolve(stack) != null;
    }

    /**
     * effects: returns the ammo store behind {@code stack}, or null if it
     * holds no rounds. Every weapon is one; the protocol defines no
     * detachable magazines of its own, so today this is {@link #resolve}.
     *
     * @param stack the stack in question
     * @return its store, or null
     */
    @Nullable
    public static AmmoStore ammoStore(ItemStack stack) {
        return resolve(stack);
    }

    /** The protocol's namespace, for its own ids. */
    public static final String NAMESPACE = "rangedweapons";

    /**
     * Item to {@link WeaponProfile}, from datapacks. Not synced to clients:
     * the AI that reads it is server-side.
     */
    public static final DataMapType<Item, WeaponProfile> WEAPONS =
            DataMapType.builder(id("weapons"), Registries.ITEM, WeaponProfile.CODEC).build();

    /**
     * effects: returns the id {@code rangedweapons:<path>}
     *
     * @param path the path part
     * @return the id
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * effects: returns the data-map profile for {@code item}, if any pack
     * described it
     *
     * @param item the item
     * @return its profile, or empty
     */
    public static Optional<WeaponProfile> profileOf(Item item) {
        return Optional.ofNullable(BuiltInRegistries.ITEM.wrapAsHolder(item).getData(WEAPONS));
    }

    /**
     * effects: returns the data-map profile for the stack's item, or empty for
     * an empty stack or an undescribed item. Cheap: one holder lookup and one
     * map get.
     *
     * @param stack the stack
     * @return its profile, or empty
     */
    public static Optional<WeaponProfile> profileOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemHolder().getData(WEAPONS));
    }
}
