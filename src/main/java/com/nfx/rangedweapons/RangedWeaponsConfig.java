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
package com.nfx.rangedweapons;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The protocol's common config, {@code config/rangedweapons-common.toml}:
 * what the fallback tier's bullets do to blocks. The numbers are the two
 * knobs of {@link com.nfx.rangedweapons.fallback.Impact}'s rules; the policy
 * says whose bullets may break anything at all.
 */
public final class RangedWeaponsConfig {
    private RangedWeaponsConfig() {}

    /** Whose bullets may break blocks. */
    public enum BreakBlocks {
        /** Players' bullets, each break offered to claim and protection mods as a block-break event. */
        PLAYERS,
        /** Everyone's, mobs subject to the {@code mobGriefing} rule. */
        EVERYONE,
        /** No one's: bullets only mark and shower debris. */
        NOBODY
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<BreakBlocks> BREAK_BLOCKS;
    public static final ModConfigSpec.DoubleValue HEALTH_PER_HARDNESS;
    public static final ModConfigSpec.DoubleValue BULLETPROOF_HARDNESS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("What bullets do to the blocks they hit.").push("bullets");
        BREAK_BLOCKS = builder
                .comment("Whose bullets may break blocks: PLAYERS (each break is offered to protection mods),",
                        "EVERYONE (mobs too, under the mobGriefing rule), or NOBODY.")
                .defineEnum("breakBlocks", BreakBlocks.PLAYERS);
        HEALTH_PER_HARDNESS = builder
                .comment("A block takes this much bullet damage per point of its hardness before it breaks.",
                        "At 15: glass (0.3) goes to one round of five, stone (1.5) takes five, an iron block (5) fourteen.",
                        "Damage is remembered for 20 seconds after the last hit, then the block heals.")
                .defineInRange("healthPerHardness", 15.0D, 0.1D, 10000.0D);
        BULLETPROOF_HARDNESS = builder
                .comment("Blocks at or above this hardness never break: obsidian, ancient debris, netherite, ender chests at 20.",
                        "Blocks with negative hardness (bedrock, command blocks) and the #rangedweapons:bulletproof tag are always immune.")
                .defineInRange("bulletproofHardness", 20.0D, 0.0D, 10000.0D);
        builder.pop();
        SPEC = builder.build();
    }
}
