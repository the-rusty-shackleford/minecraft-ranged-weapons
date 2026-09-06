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

import com.nfx.rangedweapons.fallback.ProfiledWeapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The protocol's registry objects and the lookups over them.
 *
 * <p>{@link #WEAPONS} is the data map that lets a datapack describe any item
 * as a weapon with no code at all. Every mod and pack ships its entries at
 * {@code data/rangedweapons/data_maps/item/weapons.json}; the loader merges
 * them, later entries for the same item winning, and an entry may be guarded
 * with a {@code neoforge:mod_loaded} condition so one file can describe guns
 * from several mods without failing to load when one is absent.
 *
 * <p>{@link #WEAPON} is the capability a gun mod -- or a bridge on its
 * behalf -- registers on its items to operate them natively: its own
 * projectiles, ammunition and effects. {@link #AMMO_STORE} is the same for
 * anything that holds rounds without being a weapon, a detachable magazine.
 *
 * <p>{@link #resolve} is the one question a consumer asks of a stack: "is
 * this a weapon, and who operates it?". It is one explicit function with its
 * tiers written out in precedence order, rather than a set of providers
 * whose order depends on cross-mod event dispatch, so the answer is the
 * same whichever mods are loaded. The capability beats the profile, always:
 * code that knows the item beats data describing it, the precedence NeoForge
 * itself uses for an item's own burn time over the furnace-fuels data map.
 * This is the one place the contract package reaches down into an
 * implementation: the fallback tier is part of the protocol's promise, not
 * an optional extra.
 */
public final class RangedWeapons {
    private RangedWeapons() {}

    /**
     * The native tier. A provider registered on an item through
     * {@code RegisterCapabilitiesEvent.registerItem} answers for that item
     * ahead of any profile; a provider may return null for a stack it
     * declines, and the profile tier is then consulted.
     */
    public static final ItemCapability<RangedWeapon, Void> WEAPON =
            ItemCapability.createVoid(id("weapon"), RangedWeapon.class);

    /** As {@link #WEAPON}, for something that holds rounds and is not a weapon. */
    public static final ItemCapability<AmmoStore, Void> AMMO_STORE =
            ItemCapability.createVoid(id("ammo_store"), AmmoStore.class);

    /**
     * effects: returns the weapon behind {@code stack}, or null if it is
     * empty or no tier claims it: the {@link #WEAPON} capability if a
     * provider answers, else the fallback {@link ProfiledWeapon} if the item
     * has a {@link #WEAPONS} profile. A capability dispatch and a holder
     * lookup, no allocation on the steady state: cheap enough to call every
     * tick.
     *
     * @param stack the stack in question
     * @return its weapon, or null
     */
    @Nullable
    public static RangedWeapon resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        RangedWeapon provided = stack.getCapability(WEAPON);
        if (provided != null) {
            return provided;
        }
        return stack.getItemHolder().getData(WEAPONS) == null ? null : ProfiledWeapon.of(stack.getItem());
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
     * holds no rounds: the {@link #AMMO_STORE} capability if a provider
     * answers, else the weapon itself, since every weapon is one. The
     * protocol defines no detachable magazines of its own.
     *
     * @param stack the stack in question
     * @return its store, or null
     */
    @Nullable
    public static AmmoStore ammoStore(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        AmmoStore provided = stack.getCapability(AMMO_STORE);
        return provided != null ? provided : resolve(stack);
    }

    /** The protocol's namespace, for its own ids. */
    public static final String NAMESPACE = "rangedweapons";

    /**
     * Item to {@link WeaponProfile}, from datapacks. Synced to clients that
     * carry the protocol, so a client-side consumer -- an ammo counter that
     * needs the capacity -- reads the same profile the server does; not
     * mandatory, so a client without it still connects.
     */
    public static final DataMapType<Item, WeaponProfile> WEAPONS =
            DataMapType.builder(id("weapons"), Registries.ITEM, WeaponProfile.CODEC)
                    .synced(WeaponProfile.CODEC, false)
                    .build();

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
