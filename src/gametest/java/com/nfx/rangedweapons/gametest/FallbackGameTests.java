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
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.fallback.Fallback;
import com.nfx.rangedweapons.fallback.ProfiledWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fallback tier against the real backend: a headless server, the
 * registries live, the gametest source set's own datapack giving three
 * vanilla items profiles ({@code src/gametest/resources/.../weapons.json}).
 *
 * <p>Partitions. Resolution: profiled item / unprofiled item / empty stack;
 * one instance per item; {@code isWeapon} and {@code ammoStore} agree with
 * {@code resolve}. Rounds: fresh, at capacity, after one consumed, at zero;
 * per stack; {@code load} above capacity and below zero; {@code consumeRound}
 * on empty; the {@code stats}/{@code capacity} invariant. Firing: one
 * projectile hits for exactly the profile's damage; a projectile that hits
 * nothing is gone after its lifetime and not before; every projectile of a
 * multi-projectile shot is launched, and firing spends no round.
 *
 * <p>An armor stand is the shooter: the smallest {@link LivingEntity} that
 * needs no AI switched off. The tests are deliberately short-ranged and slow
 * so nothing leaves the arena's bounds, which is what the presence
 * assertions scan.
 *
 * <p>The class has a public no-argument constructor and instance test
 * methods because the gametest registry instantiates the holder class
 * reflectively before invoking each test.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FallbackGameTests {
    /** Relative to the template origin. Layer 0 is the floor laid below. */
    private static final BlockPos SHOOTER = new BlockPos(1, 1, 4);
    private static final BlockPos TARGET = new BlockPos(7, 1, 4);
    private static final int ARENA_SIZE = 9;
    /** How far in front of the eye a shot starts: outside the shooter's own box. */
    private static final double MUZZLE_OFFSET = 0.6;

    public FallbackGameTests() {}

    @GameTest(template = "arena")
    public void resolveFindsProfiledItemsAndNothingElse(GameTestHelper helper) {
        RangedWeapon stick = RangedWeapons.resolve(new ItemStack(Items.STICK));
        helper.assertTrue(stick instanceof ProfiledWeapon,
                "a profiled item resolves to the fallback weapon, got " + stick);
        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.STICK)) == stick,
                "one weapon instance per item");
        helper.assertTrue(RangedWeapons.resolve(new ItemStack(Items.DIAMOND)) == null,
                "an item no pack describes is not a weapon");
        helper.assertTrue(RangedWeapons.resolve(ItemStack.EMPTY) == null,
                "the empty stack is not a weapon");
        helper.assertTrue(RangedWeapons.isWeapon(new ItemStack(Items.STICK)),
                "isWeapon agrees with resolve for a weapon");
        helper.assertFalse(RangedWeapons.isWeapon(new ItemStack(Items.DIAMOND)),
                "isWeapon agrees with resolve for a non-weapon");
        helper.assertTrue(RangedWeapons.ammoStore(new ItemStack(Items.STICK)) == stick,
                "a weapon is its own ammo store");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void profiledWeaponKeepsRoundsOnTheStack(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        RangedWeapon weapon = requireWeapon(helper, stack);

        helper.assertValueEqual(weapon.capacity(stack), 4, "capacity from the profile");
        helper.assertValueEqual(weapon.stats(stack).capacity(), weapon.capacity(stack), "stats and capacity");
        helper.assertValueEqual(weapon.rounds(stack), 0, "rounds on a fresh stack");
        helper.assertTrue(weapon.isEmpty(stack), "a fresh stack is empty");

        weapon.load(stack, 4);
        helper.assertValueEqual(weapon.rounds(stack), 4, "rounds after loading to capacity");
        helper.assertFalse(weapon.isEmpty(stack), "a loaded stack is not empty");
        helper.assertValueEqual(weapon.rounds(new ItemStack(Items.STICK)), 0, "rounds on another stack");

        weapon.consumeRound(stack);
        helper.assertValueEqual(weapon.rounds(stack), 3, "rounds after one consumed");

        weapon.load(stack, 0);
        helper.assertTrue(weapon.isEmpty(stack), "loaded to zero is empty");

        expectThrows(helper, IllegalArgumentException.class, () -> weapon.load(stack, 5), "load above capacity");
        expectThrows(helper, IllegalArgumentException.class, () -> weapon.load(stack, -1), "load below zero");
        expectThrows(helper, IllegalStateException.class, () -> weapon.consumeRound(stack), "consume when empty");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void bulletDealsTheProfileDamageToWhatItHits(GameTestHelper helper) {
        layFloor(helper);
        ArmorStand shooter = helper.spawn(EntityType.ARMOR_STAND, SHOOTER);
        IronGolem target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, TARGET);
        ItemStack stack = new ItemStack(Items.STICK);
        RangedWeapon weapon = requireWeapon(helper, stack);

        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(shooter.getEyePosition());
        fire(helper, weapon, stack, shooter, aim);

        float expected = target.getMaxHealth() - weapon.stats(stack).damage();
        helper.succeedWhen(() -> {
            helper.assertFalse(target.getHealth() == target.getMaxHealth(), "the target has not been hit yet");
            helper.assertValueEqual(target.getHealth(), expected, "health after one hit");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void bulletIsGoneAfterItsLifetimeAndNotBefore(GameTestHelper helper) {
        layFloor(helper);
        ArmorStand shooter = helper.spawn(EntityType.ARMOR_STAND, SHOOTER);
        // Half a block per tick for ten ticks: five blocks of open air.
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        RangedWeapon weapon = requireWeapon(helper, stack);

        fire(helper, weapon, stack, shooter, new Vec3(1, 0, 0));

        helper.runAtTickTime(5, () -> helper.assertEntityPresent(Fallback.BULLET.get()));
        helper.runAtTickTime(20, () -> {
            helper.assertEntityNotPresent(Fallback.BULLET.get());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void everyProjectileOfAShotIsLaunchedAndNoRoundIsSpent(GameTestHelper helper) {
        layFloor(helper);
        ArmorStand shooter = helper.spawn(EntityType.ARMOR_STAND, SHOOTER);
        ItemStack stack = new ItemStack(Items.BONE);
        RangedWeapon weapon = requireWeapon(helper, stack);
        weapon.load(stack, 2);

        fire(helper, weapon, stack, shooter, new Vec3(1, 0, 0));

        helper.assertValueEqual(weapon.rounds(stack), 2, "rounds after firing: the caller consumes, not fire()");
        helper.runAtTickTime(3, () -> {
            int inFlight = helper.getLevel().getEntities(Fallback.BULLET.get(), helper.getBounds(), e -> true).size();
            helper.assertValueEqual(inFlight, weapon.stats(stack).projectilesPerShot(), "bullets in flight");
            helper.succeed();
        });
    }

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    private static RangedWeapon requireWeapon(GameTestHelper helper, ItemStack stack) {
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            helper.fail("no profile for " + stack + ": the gametest data map did not load");
        }
        return weapon;
    }

    /** One shot from just in front of the shooter's eye, along {@code aim}. */
    private static void fire(GameTestHelper helper, RangedWeapon weapon, ItemStack stack, LivingEntity shooter, Vec3 aim) {
        Vec3 origin = shooter.getEyePosition().add(aim.normalize().scale(MUZZLE_OFFSET));
        weapon.fire(helper.getLevel(), shooter, stack, Shot.of(weapon.stats(stack), origin, aim));
    }

    private static void expectThrows(GameTestHelper helper, Class<? extends RuntimeException> expected,
                                     Runnable action, String what) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (expected.isInstance(e)) {
                return;
            }
            helper.fail(what + " threw " + e + ", expected " + expected.getSimpleName());
        }
        helper.fail(what + " did not throw " + expected.getSimpleName());
    }
}
