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

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.api.WeaponProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link RangedWeapon} whose entire behaviour is its item's data-map
 * profile: rounds live in the {@link Fallback#ROUNDS} component, and firing
 * launches {@link ProfiledBullet}s.
 *
 * <p>One instance per item, shared by every stack of it and obtained through
 * {@link #of(Item)}; the instance holds the item, never the profile, so a
 * {@code /reload} that changes the profile is honoured on the next call.
 *
 * <p>AF: the weapon that operates every stack of {@code item} exactly as the
 * current {@code rangedweapons:weapons} entry for {@code item} describes.<br>
 * RI: {@code item} is not null.
 */
public final class ProfiledWeapon implements RangedWeapon {

    // Bounded by the number of profiled items. Concurrent because a client
    // thread may ask isWeapon() for a tooltip while the server thread fires.
    private static final Map<Item, ProfiledWeapon> INSTANCES = new ConcurrentHashMap<>();

    private final Item item;

    private ProfiledWeapon(Item item) {
        this.item = item;
    }

    /**
     * effects: returns the one weapon for {@code item}, creating it on the
     * first call. Does not check that {@code item} has a profile: that is
     * {@link RangedWeapons#resolve}'s job, and this weapon fails loudly
     * rather than quietly if it is used without one.
     *
     * @param item the item
     * @return its profiled weapon
     */
    public static ProfiledWeapon of(Item item) {
        return INSTANCES.computeIfAbsent(item, ProfiledWeapon::new);
    }

    /**
     * effects: returns the item's current profile<br>
     * throws: {@link IllegalStateException} if no pack describes the item
     * any more -- the caller resolved a weapon that a reload has since
     * unmade, and must resolve again
     */
    @Override
    public WeaponProfile profile() {
        return RangedWeapons.profileOf(item).orElseThrow(() -> new IllegalStateException(
                "no rangedweapons:weapons entry for " + BuiltInRegistries.ITEM.getKey(item)
                        + " any more; resolve the weapon again"));
    }

    @Override
    public int capacity(ItemStack stack) {
        return profile().defaults().capacity();
    }

    /**
     * effects: returns the rounds on the stack, clamped to the capacity so a
     * profile whose capacity shrank on reload still satisfies
     * {@code rounds <= capacity}
     */
    @Override
    public int rounds(ItemStack stack) {
        return Math.min(stack.getOrDefault(Fallback.ROUNDS.get(), 0), capacity(stack));
    }

    @Override
    public void load(ItemStack stack, int count) {
        int capacity = capacity(stack);
        if (count < 0 || count > capacity) {
            throw new IllegalArgumentException("count must be in [0, " + capacity + "], was " + count);
        }
        stack.set(Fallback.ROUNDS.get(), count);
    }

    @Override
    public void consumeRound(ItemStack stack) {
        int rounds = rounds(stack);
        if (rounds == 0) {
            throw new IllegalStateException("cannot consume a round from an empty "
                    + BuiltInRegistries.ITEM.getKey(item));
        }
        stack.set(Fallback.ROUNDS.get(), rounds - 1);
    }

    /**
     * Spawns {@code shot.count()} bullets from the shot's origin, each along
     * the aim scattered per {@link Spread}, at the shot's speed, dealing the
     * shot's damage, gone after its lifetime. The shooter's own random
     * supplies the scatter, so the stream a mob's AI is seeded with governs
     * its shots too.
     */
    @Override
    public void fire(ServerLevel level, LivingEntity shooter, ItemStack stack, Shot shot) {
        Vec3 origin = shot.origin();
        for (int i = 0; i < shot.count(); i++) {
            ProfiledBullet bullet = new ProfiledBullet(Fallback.BULLET.get(), shooter, level, stack,
                    shot.damage(), shot.speed(), shot.lifetimeTicks());
            bullet.setPos(origin.x, origin.y, origin.z);
            Vec3 direction = Spread.jitter(shot.direction(), shot.spread(), shooter.getRandom());
            bullet.shoot(direction.x, direction.y, direction.z, shot.speed(), 0.0f);
            level.addFreshEntity(bullet);
        }
    }

    @Override
    public String toString() {
        return "ProfiledWeapon[" + BuiltInRegistries.ITEM.getKey(item) + "]";
    }
}
