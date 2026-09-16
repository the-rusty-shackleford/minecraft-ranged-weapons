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

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * What is true of every stack of one weapon item: its class, its default
 * stats, what it eats, and what it sounds like. Immutable.
 *
 * <p>Items and sounds are referenced by id and resolved at use rather than
 * held as registry objects, so a profile can be decoded from a datapack
 * without a bootstrapped registry. An id that resolves to nothing degrades --
 * no ammo drops, no sound plays -- and is reported once per reload by the
 * protocol mod, so the loud failure is not lost, only moved.
 *
 * <p>The datapack shape is flat: {@code "class"} (optional, default
 * {@code unclassified}), the nine {@link WeaponStats} fields, the four
 * optional ids {@code "ammo"}, {@code "magazine"}, {@code "shot_sound"},
 * {@code "far_shot_sound"}, the optional tag {@code "ammo_family"}
 * (written with a leading {@code #}, as tags are), and the optional object
 * {@code "damage_falloff"} ({@link Falloff}: {@code start}, {@code end},
 * {@code floor}).
 *
 * <p>Ammunition is two questions with two answers. What the weapon
 * <em>accepts</em> is its family: an item tag, so any round in it loads,
 * from any mod; the protocol ships {@link AmmoFamilies#SMALL small},
 * {@link AmmoFamilies#MEDIUM medium}, {@link AmmoFamilies#LARGE large} and
 * {@link AmmoFamilies#SHELL shell}, and any mod may add its own. What its
 * <em>native round</em> is stays an item: the one a killed carrier drops,
 * the one a recipe makes, the one a tooltip names -- a tag has no first
 * member to be that. A weapon with a family accepts the family; one
 * without accepts only its native round; one with neither loads nothing.
 *
 * <p>AF: one item's default combat facts and optional presentation declaration;
 * the profile does not represent a particular stack's live aim or reload state.
 * <p>RI: no field is null. Enforced in the constructor; the {@code Optional}s
 * express absence, a null {@code Optional} is a bug.
 *
 * @param weaponClass  the coarse category, for consumer policy
 * @param defaults     the stats for a stack with no overrides of its own
 * @param ammoItem     the native round: the loose-round item this weapon eats; what a killed carrier drops
 * @param ammoFamily   the tag of every round this weapon accepts, if it takes more than its native round
 * @param magazineItem a detachable container for its rounds, if any; also loot
 * @param shotSound    the report heard near the shooter, if any
 * @param farShotSound the muffled report heard at a distance, if any
 * @param falloff      how damage falls with distance flown, if it does
 * @param grip         the ordinary hold; absent leaves the consumer's existing hold alone
 */
public record WeaponProfile(WeaponClass weaponClass, WeaponStats defaults,
                            Optional<ResourceLocation> ammoItem, Optional<TagKey<Item>> ammoFamily,
                            Optional<ResourceLocation> magazineItem,
                            Optional<ResourceLocation> shotSound, Optional<ResourceLocation> farShotSound,
                            Optional<Falloff> falloff, Optional<Grip> grip) {

    private static final Codec<Grip> GRIP_CODEC = Codec.STRING.comapFlatMap(name -> switch (name) {
        case "one_handed" -> DataResult.success(Grip.ONE_HANDED);
        case "two_handed" -> DataResult.success(Grip.TWO_HANDED);
        default -> DataResult.error(() -> "Unknown grip: " + name + "; expected one_handed or two_handed");
    }, grip -> switch (grip) {
        case ONE_HANDED -> "one_handed";
        case TWO_HANDED -> "two_handed";
    });

    /** The datapack shape described above. */
    public static final Codec<WeaponProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            WeaponClass.CODEC.optionalFieldOf("class", WeaponClass.UNCLASSIFIED).forGetter(WeaponProfile::weaponClass),
            WeaponStats.MAP_CODEC.forGetter(WeaponProfile::defaults),
            ResourceLocation.CODEC.optionalFieldOf("ammo").forGetter(WeaponProfile::ammoItem),
            TagKey.hashedCodec(Registries.ITEM).optionalFieldOf("ammo_family").forGetter(WeaponProfile::ammoFamily),
            ResourceLocation.CODEC.optionalFieldOf("magazine").forGetter(WeaponProfile::magazineItem),
            ResourceLocation.CODEC.optionalFieldOf("shot_sound").forGetter(WeaponProfile::shotSound),
            ResourceLocation.CODEC.optionalFieldOf("far_shot_sound").forGetter(WeaponProfile::farShotSound),
            Falloff.CODEC.optionalFieldOf("damage_falloff").forGetter(WeaponProfile::falloff),
            GRIP_CODEC.optionalFieldOf("grip").forGetter(WeaponProfile::grip)
    ).apply(i, WeaponProfile::new));

    /**
     * requires: none. effects: constructs the immutable profile.
     * @throws IllegalArgumentException if any field is null
     */
    public WeaponProfile {
        if (weaponClass == null || defaults == null || ammoItem == null || ammoFamily == null || magazineItem == null
                || shotSound == null || farShotSound == null || falloff == null || grip == null) {
            throw new IllegalArgumentException("no field of a WeaponProfile may be null");
        }
    }

    /**
     * requires: no argument is null.
     * effects: constructs a profile with no grip declaration. Retains the constructor
     * descriptor used by already compiled 1.x providers.
     * @throws IllegalArgumentException if any argument is null
     */
    public WeaponProfile(WeaponClass weaponClass, WeaponStats defaults,
                         Optional<ResourceLocation> ammoItem, Optional<TagKey<Item>> ammoFamily,
                         Optional<ResourceLocation> magazineItem, Optional<ResourceLocation> shotSound,
                         Optional<ResourceLocation> farShotSound, Optional<Falloff> falloff) {
        this(weaponClass, defaults, ammoItem, ammoFamily, magazineItem, shotSound, farShotSound, falloff, Optional.empty());
    }

    /**
     * effects: returns whether {@code stack} loads into this weapon: a
     * member of its ammo family if it names one, else its native round
     * item, else nothing; never the empty stack
     *
     * @param stack the candidate
     * @return whether it is accepted
     */
    public boolean acceptsAmmo(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ammoFamily.isPresent()) {
            return stack.is(ammoFamily.get());
        }
        return ammoItem.isPresent() && BuiltInRegistries.ITEM.getOptional(ammoItem.get()).map(stack::is).orElse(false);
    }
}
