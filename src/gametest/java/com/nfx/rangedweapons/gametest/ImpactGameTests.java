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
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the fallback bullet does to blocks, on a real server, under the
 * shipped defaults (D-0011): a player's round shatters glass and a lantern
 * at once, leaving nothing behind; everything else only cracks, however
 * many rounds it takes -- stone, ice, a block with no hardness at all -- and
 * obsidian is never marked; a mob's round leaves glass standing under the
 * default players-only policy.
 *
 * <p>The shooter is a mock player (a {@code Player} with no connection,
 * which the rules admit without the block-break event) or an armor stand
 * for a mob. The stick profile in this mod's test data map deals 5 a
 * round, so stone (1.5 hardness, 22.5 health) shows its last crack at the
 * fifth round, which used to break it; eight rounds are fired.
 *
 * <p>Not tested here: the {@code EVERYONE} and {@code NOBODY} policies.
 * Tests run concurrently in one server, and the policy is one common
 * config for all of them, so flipping it inside a test would race the
 * others. The policy switch itself is three lines of {@code mayBreak};
 * the mob path under {@code EVERYONE} is vanilla's {@code mobGriefing}
 * check.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ImpactGameTests {
    private static final int ARENA_SIZE = 9;
    /** Where the shooter stands, on the floor; its eye is a block and a half up. */
    private static final Vec3 SHOOTER = new Vec3(1.5, 1.0, 4.5);
    /** The block shot at, in the line of the shooter's eye. */
    private static final BlockPos TARGET = new BlockPos(5, 2, 4);
    private static final double MUZZLE_OFFSET = 0.6;
    /** Ticks between shots: at two blocks a tick the round is home in two. */
    private static final int SHOT_INTERVAL = 3;

    public ImpactGameTests() {}

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aPlayersRoundShattersGlass(GameTestHelper helper) {
        Player shooter = arena(helper, Blocks.GLASS);
        shots(helper, shooter, 1);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, TARGET));
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aLanternShattersOnOneRoundAndDropsNothing(GameTestHelper helper) {
        // Standing on a block, as a lantern must; aimed at its body, which is
        // the lower half of the block. Mined, a lantern drops itself.
        Player shooter = arena(helper, Blocks.LANTERN);
        helper.setBlock(TARGET.below(), Blocks.SMOOTH_STONE);
        Vec3 body = Vec3.atBottomCenterOf(TARGET).add(0.0, 0.2, 0.0);
        fire(helper, shooter, body);
        helper.runAtTickTime(6, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET);
            helper.assertEntityNotPresent(EntityType.ITEM);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void stoneCracksButStandsThroughEightRounds(GameTestHelper helper) {
        Player shooter = arena(helper, Blocks.STONE);
        shots(helper, shooter, 8);
        helper.runAtTickTime(8 * SHOT_INTERVAL + 4, () -> {
            helper.assertEntityNotPresent(Fallback.BULLET.get());
            helper.assertBlockPresent(Blocks.STONE, TARGET);
            helper.assertEntityNotPresent(EntityType.ITEM);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aBlockWithNoHardnessStands(GameTestHelper helper) {
        // Slime: hardness 0 and a full collision box. Its health was 0, so
        // the first round used to take it.
        Player shooter = arena(helper, Blocks.SLIME_BLOCK);
        shots(helper, shooter, 3);
        helper.runAtTickTime(3 * SHOT_INTERVAL + 4, () -> {
            helper.assertEntityNotPresent(Fallback.BULLET.get());
            helper.assertBlockPresent(Blocks.SLIME_BLOCK, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void obsidianIsBulletproof(GameTestHelper helper) {
        Player shooter = arena(helper, Blocks.OBSIDIAN);
        shots(helper, shooter, 6);
        helper.runAtTickTime(6 * SHOT_INTERVAL + 4, () -> {
            helper.assertEntityNotPresent(Fallback.BULLET.get());
            helper.assertBlockPresent(Blocks.OBSIDIAN, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void iceIsNotGlassAndStands(GameTestHelper helper) {
        Player shooter = arena(helper, Blocks.ICE);
        shots(helper, shooter, 3);
        helper.runAtTickTime(3 * SHOT_INTERVAL + 4, () -> {
            helper.assertEntityNotPresent(Fallback.BULLET.get());
            helper.assertBlockPresent(Blocks.ICE, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void aMobsRoundLeavesGlassStanding(GameTestHelper helper) {
        layFloor(helper);
        helper.setBlock(TARGET, Blocks.GLASS);
        ArmorStand shooter = helper.spawn(EntityType.ARMOR_STAND, BlockPos.containing(SHOOTER));
        fire(helper, shooter);
        helper.runAtTickTime(6, () -> {
            helper.assertEntityNotPresent(Fallback.BULLET.get());
            helper.assertBlockPresent(Blocks.GLASS, TARGET);
            helper.succeed();
        });
    }

    /** The floor, {@code target} at the target position, and a mock survival player standing ready. */
    private static Player arena(GameTestHelper helper, Block target) {
        layFloor(helper);
        helper.setBlock(TARGET, target);
        Player shooter = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(SHOOTER);
        shooter.moveTo(at.x, at.y, at.z, -90.0f, 0.0f);
        return shooter;
    }

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    /** {@code count} rounds, one every {@link #SHOT_INTERVAL} ticks from tick 0. */
    private static void shots(GameTestHelper helper, LivingEntity shooter, int count) {
        for (int i = 0; i < count; i++) {
            if (i == 0) {
                fire(helper, shooter);
            } else {
                helper.runAtTickTime(i * SHOT_INTERVAL, () -> fire(helper, shooter));
            }
        }
    }

    /** One round from just in front of the shooter's eye at the target block's centre. */
    private static void fire(GameTestHelper helper, LivingEntity shooter) {
        fire(helper, shooter, Vec3.atCenterOf(TARGET));
    }

    /** One round from just in front of the shooter's eye at {@code at}, relative to the test. */
    private static void fire(GameTestHelper helper, LivingEntity shooter, Vec3 at) {
        ItemStack stack = new ItemStack(Items.STICK);
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            helper.fail("no profile for the stick: the gametest data map did not load");
            return;
        }
        Vec3 aim = helper.absoluteVec(at).subtract(shooter.getEyePosition());
        Vec3 origin = shooter.getEyePosition().add(aim.normalize().scale(MUZZLE_OFFSET));
        weapon.fire(helper.getLevel(), shooter, stack, Shot.of(weapon.stats(stack), origin, aim));
    }
}
