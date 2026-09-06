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
package com.nfx.rangedweapons.client.compat;

import com.nfx.rangedweapons.RangedWeaponsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps guns out of Hold My Items' hands.
 *
 * <p>Hold My Items ({@code holdmyitemsnf}) takes over first-person rendering
 * of every held item and poses it in its own arm animation, then applies
 * the item model's first-person transform on top. A two-handed weapon
 * cannot be right under both frames -- which is why that mod's own default
 * exclusion list is gun mods -- and the only way out it offers is two
 * lists in its client config, read live every frame. This writes a gun
 * mod's items into the per-item one, once, so a gun is held the way its
 * model says and a new gun needs nothing from the player.
 *
 * <p>Reached by reflection: the mod is not a build dependency and is
 * absent on most installs. Every failure is logged and harmless -- the
 * gun then renders as that mod likes, which is what happened before.
 */
public final class HoldMyItems {
    private HoldMyItems() {}

    public static final String MOD_ID = "holdmyitemsnf";
    static final String CONFIG_CLASS = "de.bene2212.holdmyitemsnf.config.HoldMyItemsClientConfig";
    static final String EXCLUDED_ITEMS_FIELD = "ITEM_IDS_TO_EXCLUDE";

    /**
     * effects: if Hold My Items is loaded, ensures every one of
     * {@code items} is in its excluded-item list, saving its config file if
     * any had to be added, and logs what was done; if it is not loaded, or
     * its config cannot be reached (renamed internals, config not yet
     * loaded), logs and changes nothing
     *
     * @param items the items to be held as their models say
     * @return how many ids were added; 0 if all were present or the mod is
     *         absent; -1 if the mod is present but could not be reached
     */
    public static int excludeItems(Collection<? extends ItemLike> items) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return 0;
        }
        try {
            Field field = Class.forName(CONFIG_CLASS).getField(EXCLUDED_ITEMS_FIELD);
            @SuppressWarnings("unchecked")
            ModConfigSpec.ConfigValue<List<? extends String>> value =
                    (ModConfigSpec.ConfigValue<List<? extends String>>) field.get(null);
            List<String> merged = merge(value.get(), ids(items));
            int added = merged.size() - value.get().size();
            if (added > 0) {
                value.set(merged);
                RangedWeaponsMod.LOGGER.info("Hold My Items: excluded {} item(s) from its hand rendering so they are held as modelled", added);
            }
            return added;
        } catch (ReflectiveOperationException | RuntimeException e) {
            RangedWeaponsMod.LOGGER.warn("Hold My Items is present but its exclusion list could not be reached ({}); "
                    + "guns will render as it likes. Add them to excludedItemIds in {}-client.toml by hand.", e.toString(), MOD_ID);
            return -1;
        }
    }

    /**
     * effects: returns {@code existing} followed by those of {@code wanted}
     * not already in it, each once, in order; case-insensitive, as the mod
     * compares lower-cased ids
     */
    static List<String> merge(List<? extends String> existing, List<String> wanted) {
        Set<String> present = new LinkedHashSet<>();
        for (String id : existing) {
            present.add(id.toLowerCase());
        }
        List<String> merged = new ArrayList<>(existing);
        for (String id : wanted) {
            if (present.add(id.toLowerCase())) {
                merged.add(id);
            }
        }
        return merged;
    }

    private static List<String> ids(Collection<? extends ItemLike> items) {
        List<String> ids = new ArrayList<>(items.size());
        for (ItemLike item : items) {
            Item asItem = item.asItem();
            ids.add(BuiltInRegistries.ITEM.getKey(asItem).toString());
        }
        return ids;
    }
}
