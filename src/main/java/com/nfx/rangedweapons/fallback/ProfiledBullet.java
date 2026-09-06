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

import net.minecraft.nbt.CompoundTag;
import com.nfx.rangedweapons.api.Falloff;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The fallback tier's projectile: a straight, gravity-free tracer that
 * deals a fixed damage to the first thing it hits and is gone after a fixed
 * number of ticks.
 *
 * <p>Built on {@link AbstractArrow} for its movement, collision, owner and
 * deflection handling, and for the renderer contract, with three of its
 * behaviours replaced. Vanilla arrows deal {@code ceil(|velocity| *
 * baseDamage)} and lose one percent of their speed every tick, so a
 * profile's damage would arrive rounded and diminished; this bullet keeps
 * its speed constant and deals its damage as written. Vanilla arrows stick
 * in blocks and lie there for a minute; this one is removed on impact. And
 * nothing can pick it up.
 *
 * <p>RI: {@code damage >= 0} and finite; {@code speed > 0} and finite;
 * {@code lifetimeTicks >= 1}. The entity type is not saved (see
 * {@link Fallback#BULLET}); the type-and-level constructor exists for the
 * client, which builds the entity from the spawn packet, and starts at the
 * harmless minimum. The save methods are kept complete so that an explicit
 * copy of the entity, if a mod ever makes one, keeps its numbers.
 */
public final class ProfiledBullet extends AbstractArrow {

    private static final String TAG_DAMAGE = "HitDamage";
    private static final String TAG_SPEED = "Speed";
    private static final String TAG_LIFETIME = "LifetimeTicks";
    private static final String TAG_FALLOFF = "Falloff";
    private static final String TAG_FIRED_FROM = "FiredFrom";

    private float damage = 0.0f;
    private float speed = 1.0f;
    private int lifetimeTicks = 1;
    /** How damage falls with distance flown, if it does. */
    @Nullable
    private Falloff falloff = null;
    /** Where the flight began, for the distance; set on the first move if not by the shooter. */
    @Nullable
    private Vec3 firedFrom = null;

    /**
     * The constructor the entity type and the client use. Harmless defaults;
     * see the RI.
     *
     * @param type  this entity's type
     * @param level the level
     */
    public ProfiledBullet(EntityType<? extends ProfiledBullet> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    /**
     * A bullet ready to be shot. The caller positions and shoots it.
     *
     * <p>requires: {@code weapon} is not empty; {@code damage >= 0} and
     * finite; {@code speed > 0} and finite; {@code lifetimeTicks >= 1}<br>
     * effects: a bullet owned by {@code shooter} at its eye, not yet moving,
     * carrying {@code weapon}'s enchantments the way an arrow carries its
     * bow's
     *
     * @param type          this entity's type
     * @param shooter       who fired; the bullet's owner
     * @param level         the level to live in
     * @param weapon        the stack it was fired from
     * @param damage        what a hit deals
     * @param speed         blocks per tick, held constant
     * @param lifetimeTicks ticks before an unspent bullet is removed
     */
    public ProfiledBullet(EntityType<? extends ProfiledBullet> type, LivingEntity shooter, Level level,
                          ItemStack weapon, float damage, float speed, int lifetimeTicks) {
        super(type, shooter, level, ItemStack.EMPTY, weapon);
        if (!(damage >= 0) || Float.isInfinite(damage)) {
            throw new IllegalArgumentException("damage must be finite and >= 0, was " + damage);
        }
        if (!(speed > 0) || Float.isInfinite(speed)) {
            throw new IllegalArgumentException("speed must be finite and > 0, was " + speed);
        }
        if (lifetimeTicks < 1) {
            throw new IllegalArgumentException("lifetimeTicks must be >= 1, was " + lifetimeTicks);
        }
        this.damage = damage;
        this.speed = speed;
        this.lifetimeTicks = lifetimeTicks;
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    /** What a hit deals. */
    public float damage() {
        return damage;
    }

    /** Ticks before an unspent bullet is removed. */
    public int lifetimeTicks() {
        return lifetimeTicks;
    }

    /**
     * effects: makes damage fall with distance flown by {@code falloff},
     * measured from where the bullet is now
     *
     * @param falloff the rule
     */
    public void setFalloff(Falloff falloff) {
        this.falloff = falloff;
        this.firedFrom = this.position();
    }

    /**
     * effects: returns what a hit deals here: {@link #damage()} scaled by
     * the falloff for the distance flown from where the bullet was fired,
     * or all of it with no falloff
     */
    public float damageHere() {
        if (falloff == null) {
            return damage;
        }
        Vec3 from = firedFrom != null ? firedFrom : this.position();
        return damage * falloff.factor((float) from.distanceTo(this.position()));
    }

    /**
     * Vanilla's tick, then two corrections on the server: the drag vanilla
     * applied after moving is undone so the next move is at full speed
     * again, and the bullet is removed once it has lived its lifetime.
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        if (this.tickCount >= this.lifetimeTicks) {
            this.discard();
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        double length = velocity.length();
        if (length > 1e-6) {
            this.setDeltaMovement(velocity.scale(this.speed / length));
        }
    }

    /**
     * Deals {@link #damageHere()} -- the profile's damage, less the falloff
     * for the distance flown -- to the entity hit, credits the owner with
     * the hit for its AI's sake, and is gone.
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().arrow(this, owner != null ? owner : this);
        if (target.hurt(source, this.damageHere()) && owner instanceof LivingEntity livingOwner) {
            livingOwner.setLastHurtMob(target);
        }
        this.discard();
    }

    /**
     * Lets the block react as it would to any projectile -- a target block
     * lights up, a bell rings -- then, on the server, what {@link Impact}
     * says: debris, the hit sound, and damage toward breaking the block if
     * the owner may. Gone either way. Deliberately not vanilla's arrow
     * behaviour, which sticks, plays a sound and waits a minute.
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockState state = this.level().getBlockState(result.getBlockPos());
        state.onProjectileHit(this.level(), state, result, this);
        if (this.level() instanceof ServerLevel serverLevel) {
            Impact.hit(serverLevel, this, result);
        }
        this.discard();
    }

    /** Never dropped, never picked up: it is not an item. */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_DAMAGE, this.damage);
        tag.putFloat(TAG_SPEED, this.speed);
        tag.putInt(TAG_LIFETIME, this.lifetimeTicks);
        if (this.falloff != null) {
            Falloff.CODEC.encodeStart(NbtOps.INSTANCE, this.falloff).result().ifPresent(f -> tag.put(TAG_FALLOFF, f));
        }
        if (this.firedFrom != null) {
            tag.put(TAG_FIRED_FROM, this.newDoubleList(this.firedFrom.x, this.firedFrom.y, this.firedFrom.z));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Clamped back into the RI: a hand-edited or truncated tag must not
        // produce a bullet that can never be removed or that heals.
        this.damage = Math.max(0.0f, tag.getFloat(TAG_DAMAGE));
        this.speed = Math.max(Float.MIN_NORMAL, tag.getFloat(TAG_SPEED));
        this.lifetimeTicks = Math.max(1, tag.getInt(TAG_LIFETIME));
        this.falloff = tag.contains(TAG_FALLOFF)
                ? Falloff.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_FALLOFF)).result().orElse(null) : null;
        if (tag.contains(TAG_FIRED_FROM, Tag.TAG_LIST)) {
            ListTag from = tag.getList(TAG_FIRED_FROM, Tag.TAG_DOUBLE);
            this.firedFrom = from.size() == 3 ? new Vec3(from.getDouble(0), from.getDouble(1), from.getDouble(2)) : null;
        }
    }
}
