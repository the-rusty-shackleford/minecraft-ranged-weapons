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
package com.nfx.rangedweapons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.fallback.ProfiledBullet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a {@link ProfiledBullet} as a short tracer.
 *
 * <p>Vanilla's arrow renderer with a different texture: the arrow model is a
 * long thin cross of quads oriented along the flight path, which is the
 * shape of a tracer already. The texture leaves the fletching region blank
 * and paints only the shaft, brightest at the tip.
 *
 * <p>Every entity type needs a renderer -- the render dispatcher throws on
 * one without -- so this is not optional even for a projectile that could
 * have been invisible.
 */
public final class ProfiledBulletRenderer extends ArrowRenderer<ProfiledBullet> {

    private static final ResourceLocation TEXTURE = RangedWeapons.id("textures/entity/bullet.png");
    /** The arrow model is a block long; a tracer is a fraction of that. */
    private static final float SCALE = 0.4f;

    public ProfiledBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ProfiledBullet entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ProfiledBullet entity) {
        return TEXTURE;
    }
}
