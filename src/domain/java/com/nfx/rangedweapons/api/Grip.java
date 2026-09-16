/*
 * Ranged Weapons - a protocol between gun mods and the mobs that use them.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nfx.rangedweapons.api;

/**
 * The hands a weapon's ordinary hold needs, independently of its combat class.
 *
 * <p>AF: ONE_HANDED leaves the support hand free; TWO_HANDED uses both hands.
 * RI: the two enum values are the complete vocabulary. An absent declaration
 * belongs to the profile's Optional, not to a guessed grip. Immutable and JDK-only.
 */
public enum Grip {
    ONE_HANDED,
    TWO_HANDED
}
