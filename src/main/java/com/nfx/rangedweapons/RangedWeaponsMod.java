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
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.fallback.Fallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ranged Weapons: a protocol between gun mods and the mobs that use them.
 *
 * <p>The contract lives in {@code com.nfx.rangedweapons.api}. This class is
 * the mod's presence in the loader -- a real mod rather than a bare library,
 * because the protocol registers things of its own: the {@code weapons} data
 * map type that lets a datapack describe any item as a weapon, and the
 * fallback tier's round-count component and bullet entity that make such an
 * item fire with no gun mod present. Consumers nest this jar inside theirs,
 * and the loader deduplicates it to one copy, so those registrations happen
 * exactly once however many mods carry it.
 *
 * <p>Profiles reference items and sounds by id and resolve them at use, which
 * keeps the codec free of registries but gives up the loud failure a bad id
 * would otherwise produce at load. That failure is restored here, at the one
 * moment the data is known: every reload, each profile's ids are checked
 * against the registries and one actionable warning is logged per bad one.
 */
@Mod(RangedWeaponsMod.MOD_ID)
public final class RangedWeaponsMod {
    public static final String MOD_ID = "rangedweapons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RangedWeaponsMod(IEventBus modBus, ModContainer container) {
        Fallback.register(modBus);
        modBus.addListener(RangedWeaponsMod::registerDataMaps);
        // A game-bus event, not a mod-bus one.
        NeoForge.EVENT_BUS.addListener(RangedWeaponsMod::onDataMapsUpdated);
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(RangedWeapons.WEAPONS);
    }

    private static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        event.ifRegistry(Registries.ITEM, RangedWeaponsMod::validateItemProfiles);
    }

    /**
     * effects: for every profile in the item data map, logs one warning per
     * referenced id that resolves to nothing, naming the weapon, the field,
     * the id, and what will silently not happen as a result
     */
    private static void validateItemProfiles(Registry<Item> items) {
        items.getDataMap(RangedWeapons.WEAPONS).forEach((key, profile) -> {
            ResourceLocation weapon = key.location();
            check(weapon, "ammo", profile.ammoItem(), BuiltInRegistries.ITEM::containsKey,
                    "kills will drop no ammunition");
            check(weapon, "magazine", profile.magazineItem(), BuiltInRegistries.ITEM::containsKey,
                    "kills will drop no magazine");
            check(weapon, "shot_sound", profile.shotSound(), BuiltInRegistries.SOUND_EVENT::containsKey,
                    "shots will be silent nearby");
            check(weapon, "far_shot_sound", profile.farShotSound(), BuiltInRegistries.SOUND_EVENT::containsKey,
                    "shots will be silent at a distance");
        });
    }

    private static void check(ResourceLocation weapon, String field, Optional<ResourceLocation> id,
                              Predicate<ResourceLocation> registered, String consequence) {
        if (id.isPresent() && !registered.test(id.get())) {
            LOGGER.warn("weapon profile for {} names {} {}, which is not registered; {}",
                    weapon, field, id.get(), consequence);
        }
    }
}
