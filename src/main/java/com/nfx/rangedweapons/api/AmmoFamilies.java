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

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The ammunition families the protocol ships, as item tags: what a weapon
 * says it accepts, and what a round says it is. Datapacks fill them; the
 * protocol ships them empty but present, so a profile may name one before
 * any round exists.
 *
 * <p>A family beyond these is any mod's to declare under the same
 * convention, {@code <namespace>:ammo/<family>}, and to put under
 * {@link #ALL} if it wants "is this ammunition at all" to say yes.
 */
public final class AmmoFamilies {
    private AmmoFamilies() {}

    /** Pistol and submachine-gun rounds. */
    public static final TagKey<Item> SMALL = family("small");
    /** Rifle and machine-gun rounds. */
    public static final TagKey<Item> MEDIUM = family("medium");
    /** Heavy rounds: anti-materiel rifles, cannons; never a rifle's. */
    public static final TagKey<Item> LARGE = family("large");
    /** Shotgun shells: a different cartridge, not a size. */
    public static final TagKey<Item> SHELL = family("shell");
    /** Every family above, and any a mod adds to it: ammunition of any kind. */
    public static final TagKey<Item> ALL = TagKey.create(Registries.ITEM, RangedWeapons.id("ammo"));

    /** effects: returns the tag {@code rangedweapons:ammo/<name>} */
    public static TagKey<Item> family(String name) {
        return TagKey.create(Registries.ITEM, RangedWeapons.id("ammo/" + name));
    }

    /** effects: returns the tag {@code <namespace>:ammo/<name>}, another mod's family under the convention */
    public static TagKey<Item> family(String namespace, String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, "ammo/" + name));
    }
}
