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

/**
 * How a round's damage falls with the distance it has flown: full up to
 * {@code start} blocks, down in a straight line to {@code floor} of full at
 * {@code end} blocks, and {@code floor} from there on. A shotgun's pellets
 * that are lethal at arm's length and a sting at twenty blocks are
 * {@code (5, 18, 0.2)}; a rifle round that does not care is no falloff at
 * all, which a profile expresses by leaving the field out.
 *
 * <p>RI: {@code 0 <= start <= end}, both finite; {@code 0 <= floor <= 1}.
 *
 * @param start blocks flown before damage begins to fall
 * @param end   blocks flown by which it has reached the floor
 * @param floor the fraction of full damage that remains at and past {@code end}
 */
public record Falloff(float start, float end, float floor) {

    private record Raw(float start, float end, float floor) {}

    private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0f, Float.MAX_VALUE).fieldOf("start").forGetter(Raw::start),
            Codec.floatRange(0.0f, Float.MAX_VALUE).fieldOf("end").forGetter(Raw::end),
            Codec.floatRange(0.0f, 1.0f).fieldOf("floor").forGetter(Raw::floor)
    ).apply(i, Raw::new));

    /**
     * The datapack shape: {@code {"start": 5.0, "end": 18.0, "floor": 0.2}}.
     * The one rule the fields cannot check alone, end at or after start, is
     * a decode error naming both numbers, not an exception out of the
     * constructor.
     */
    public static final Codec<Falloff> CODEC = RAW.comapFlatMap(
            raw -> raw.end() >= raw.start()
                    ? DataResult.success(new Falloff(raw.start(), raw.end(), raw.floor()))
                    : DataResult.error(() -> "damage_falloff: end " + raw.end() + " is before start " + raw.start()),
            falloff -> new Raw(falloff.start(), falloff.end(), falloff.floor()));

    /**
     * @throws IllegalArgumentException if the RI does not hold
     */
    public Falloff {
        if (!(start >= 0.0f) || !(end >= start) || Float.isInfinite(end) || !(floor >= 0.0f && floor <= 1.0f)) {
            throw new IllegalArgumentException("need 0 <= start <= end (finite) and 0 <= floor <= 1, were "
                    + start + ", " + end + ", " + floor);
        }
    }

    /**
     * effects: returns the fraction of full damage a round deals after
     * flying {@code distance} blocks: {@code floor} at and past {@code end},
     * 1 up to {@code start}, straight between -- so with start equal to
     * end it is a step, full under it and the floor from it
     *
     * @param distance blocks flown, {@code >= 0}
     * @return the fraction, in {@code [floor, 1]}
     */
    public float factor(float distance) {
        if (distance >= end) {
            return floor;
        }
        if (distance <= start) {
            return 1.0f;
        }
        float t = (distance - start) / (end - start);
        return 1.0f - t * (1.0f - floor);
    }
}
