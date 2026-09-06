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

import com.nfx.rangedweapons.RangedWeaponsConfig;
import com.nfx.rangedweapons.RangedWeaponsConfig.BreakBlocks;
import com.nfx.rangedweapons.api.RangedWeapons;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a bullet does to the block it hits: debris and a sound for every
 * hit, and, for shooters allowed to, damage that accumulates until the
 * block breaks.
 *
 * <p>The rules are pure functions over numbers and are unit tested; the
 * effects are one method, {@link #hit}, and are gametested.
 *
 * <p>A block's health is its hardness times {@code healthPerHardness}
 * (config), so the numbers the game already has decide: glass at 0.3
 * shatters at one round of five, stone at 1.5 takes five, an iron block at
 * 5 fourteen. Blocks at or above {@code bulletproofHardness} (config; 20 by
 * default: obsidian, ancient debris, netherite, an ender chest) and every
 * block with negative hardness (bedrock, command blocks) are immune, as is
 * anything in {@code #rangedweapons:bulletproof}. Anything in
 * {@code #rangedweapons:shatters} -- glass, panes, ice -- breaks on any
 * hit whatever its hardness.
 *
 * <p>Damage is remembered per block position for {@link #HEAL_TICKS} after
 * the last hit, which is how long the client keeps a crack it was last
 * told about, then forgotten. The ledger is per level and is pruned as it
 * is written, so an idle server holds nothing.
 */
public final class Impact {
    private Impact() {}

    /** Ticks after the last hit a block's damage is remembered; the client forgets a crack after the same. */
    public static final int HEAL_TICKS = 400;
    /** Vanilla's crack textures: stages 0 to 9. */
    public static final int CRACK_STAGES = 10;
    /** Entries in a level's ledger past which healed ones are dropped on the next write. */
    static final int PRUNE_ABOVE = 512;

    /** Blocks no bullet breaks, beyond what hardness already excludes. */
    public static final TagKey<Block> BULLETPROOF = BlockTags.create(RangedWeapons.id("bulletproof"));
    /** Blocks any hit breaks: glass and its kind. */
    public static final TagKey<Block> SHATTERS = BlockTags.create(RangedWeapons.id("shatters"));

    private static final int DEBRIS_COUNT = 8;
    private static final double DEBRIS_SPREAD = 0.06;
    private static final double DEBRIS_SPEED = 0.12;
    private static final int SPARK_COUNT = 2;

    // ---------------------------------------------------------------- rules

    /**
     * effects: returns whether a block of {@code hardness} never breaks:
     * negative hardness (vanilla's unbreakable), not a number, or at or
     * above {@code ceiling}
     */
    public static boolean bulletproof(float hardness, float ceiling) {
        return !(hardness >= 0.0f) || hardness >= ceiling;
    }

    /**
     * requires: {@code hardness >= 0}, {@code perHardness > 0}<br>
     * effects: returns the bullet damage a block of {@code hardness} takes
     * before it breaks
     */
    public static float health(float hardness, float perHardness) {
        if (!(hardness >= 0.0f) || !(perHardness > 0.0f)) {
            throw new IllegalArgumentException("hardness must be >= 0 and perHardness > 0, were "
                    + hardness + " and " + perHardness);
        }
        return hardness * perHardness;
    }

    /**
     * requires: {@code health > 0}, {@code 0 <= dealt < health}<br>
     * effects: returns the crack stage, 0 to 9, that shows {@code dealt} of
     * {@code health}: the tenth of the way to breaking the block has come
     */
    public static int crackStage(float dealt, float health) {
        if (!(health > 0.0f) || !(dealt >= 0.0f) || dealt >= health) {
            throw new IllegalArgumentException("need 0 <= dealt < health, were " + dealt + " and " + health);
        }
        return Math.min(CRACK_STAGES - 1, (int) (dealt / health * CRACK_STAGES));
    }

    /**
     * effects: returns the damage still counted from a hit of {@code dealt}
     * at {@code lastHit}, as of {@code now}: all of it within
     * {@code healTicks}, none after, and none if time has gone backwards
     * (another world's clock)
     */
    public static float remembered(float dealt, long lastHit, long now, int healTicks) {
        return now < lastHit || now - lastHit > healTicks ? 0.0f : dealt;
    }

    /**
     * effects: returns the id the cracks on the block at {@code posKey} are
     * broadcast under: negative, so it collides with no entity's, and the
     * same for the same position
     */
    public static int breakerId(long posKey) {
        return -1 - (int) (Long.hashCode(posKey) & 0x3fffffff);
    }

    // --------------------------------------------------------------- ledger

    private record Hit(float dealt, long tick) {}

    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Hit>> LEDGERS = new WeakHashMap<>();

    private static Long2ObjectOpenHashMap<Hit> ledger(ServerLevel level) {
        return LEDGERS.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
    }

    /** How many positions {@code level}'s ledger holds, healed or not. Tests only. */
    static int ledgerSize(ServerLevel level) {
        Long2ObjectOpenHashMap<Hit> ledger = LEDGERS.get(level);
        return ledger == null ? 0 : ledger.size();
    }

    // -------------------------------------------------------------- effects

    /**
     * effects: showers debris of the block at {@code result}'s position and
     * plays its hit sound; then, if {@code bullet}'s owner may break blocks
     * here (config policy, spawn protection, the block-break event for a
     * player, the {@code mobGriefing} rule for a mob) and the block is not
     * bulletproof, adds the bullet's damage to what the block remembers,
     * and either breaks the block with its drops or shows the crack stage
     * reached. Nothing for air.
     *
     * @param level  the server level the hit is in
     * @param bullet the bullet, for its damage and owner
     * @param result where and which face
     */
    public static void hit(ServerLevel level, ProfiledBullet bullet, BlockHitResult result) {
        BlockPos pos = result.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        splash(level, pos, state, result);

        Entity owner = bullet.getOwner();
        if (!mayBreak(level, owner, pos, state)) {
            return;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (state.is(BULLETPROOF) || bulletproof(hardness, RangedWeaponsConfig.BULLETPROOF_HARDNESS.get().floatValue())) {
            return;
        }
        float health = state.is(SHATTERS) ? 0.0f
                : health(hardness, RangedWeaponsConfig.HEALTH_PER_HARDNESS.get().floatValue());
        long key = pos.asLong();
        long now = level.getGameTime();
        Long2ObjectOpenHashMap<Hit> ledger = ledger(level);
        Hit prior = ledger.get(key);
        float dealt = (prior == null ? 0.0f : remembered(prior.dealt(), prior.tick(), now, HEAL_TICKS)) + bullet.damage();

        if (dealt >= health) {
            ledger.remove(key);
            level.destroyBlockProgress(breakerId(key), pos, -1);
            level.destroyBlock(pos, true, owner);
            return;
        }
        if (ledger.size() > PRUNE_ABOVE) {
            ledger.values().removeIf(h -> remembered(h.dealt(), h.tick(), now, HEAL_TICKS) == 0.0f);
        }
        ledger.put(key, new Hit(dealt, now));
        level.destroyBlockProgress(breakerId(key), pos, crackStage(dealt, health));
    }

    /**
     * effects: returns whether {@code owner}'s bullet may break the block at
     * {@code pos}: by the config policy; for a connected player, within
     * what the level lets it touch (spawn protection, the border) and not
     * vetoed by the block-break event; for a mob or no owner, by the
     * {@code mobGriefing} rule. A player with no connection -- only a test
     * makes one -- may.
     */
    static boolean mayBreak(ServerLevel level, @Nullable Entity owner, BlockPos pos, BlockState state) {
        BreakBlocks policy = RangedWeaponsConfig.BREAK_BLOCKS.get();
        if (policy == BreakBlocks.NOBODY) {
            return false;
        }
        if (owner instanceof ServerPlayer player) {
            return level.mayInteract(player, pos)
                    && !CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled();
        }
        if (owner instanceof Player) {
            return true;
        }
        return policy == BreakBlocks.EVERYONE && EventHooks.canEntityGrief(level, owner);
    }

    /**
     * effects: debris of the block's own texture thrown from the face hit,
     * sparks too if the block is stone or metal, and the block's hit sound
     */
    private static void splash(ServerLevel level, BlockPos pos, BlockState state, BlockHitResult result) {
        Vec3 out = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        Vec3 at = result.getLocation().add(out.scale(DEBRIS_SPREAD));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                DEBRIS_COUNT, DEBRIS_SPREAD, DEBRIS_SPREAD, DEBRIS_SPREAD, DEBRIS_SPEED);
        SoundType sound = state.getSoundType(level, pos, null);
        if (sparks(state, sound)) {
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, SPARK_COUNT, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0f) / 4.0f, sound.getPitch() * 0.8f);
    }

    /** Stone and metal spark; the note block's idea of the material, and the sound's, decide. */
    private static boolean sparks(BlockState state, SoundType sound) {
        NoteBlockInstrument instrument = state.instrument();
        return instrument == NoteBlockInstrument.BASEDRUM
                || instrument == NoteBlockInstrument.IRON_XYLOPHONE
                || instrument == NoteBlockInstrument.BELL
                || sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.ANVIL
                || sound == SoundType.CHAIN || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.LANTERN;
    }
}
