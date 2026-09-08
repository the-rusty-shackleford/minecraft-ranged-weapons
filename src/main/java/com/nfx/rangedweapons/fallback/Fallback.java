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
package com.nfx.rangedweapons.fallback;

import com.mojang.serialization.Codec;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What the fallback tier registers with the game: the component that holds
 * a profiled weapon's rounds and the bullet it fires.
 */
public final class Fallback {
    private Fallback() {}

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RangedWeapons.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RangedWeapons.NAMESPACE);

    /**
     * Rounds loaded in a profiled weapon, on the stack. Persistent so a gun
     * in a chest keeps its load; synced so a tooltip could show it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUNDS =
            COMPONENTS.registerComponentType("rounds", builder -> builder
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The round loaded in a profiled weapon, on the stack: the item it was
     * last filled with, which decides what it fires when that round has a
     * {@code rangedweapons:ammo} entry. Absent until a reload names one.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Item>> LOADED_AMMO =
            COMPONENTS.registerComponentType("loaded_ammo", builder -> builder
                    .persistent(BuiltInRegistries.ITEM.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.ITEM)));

    /**
     * The profiled bullet. Sized and tracked like a vanilla arrow: a quarter
     * block, sent to clients within four chunks, position resynced every
     * twenty ticks -- the same settings vanilla found sufficient for a fast,
     * short-lived projectile. Never saved: a bullet is gone within seconds,
     * and a chunk written mid-flight would rather forget it than persist it
     * -- vanilla's arrow save also insists on writing a pickup item, which a
     * bullet has none of.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ProfiledBullet>> BULLET =
            ENTITIES.register("bullet", () -> EntityType.Builder
                    .<ProfiledBullet>of(ProfiledBullet::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .noSave()
                    .build(RangedWeapons.id("bullet").toString()));

    /**
     * effects: queues both registrations on {@code modBus}; call once from
     * the mod constructor
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ENTITIES.register(modBus);
    }
}
