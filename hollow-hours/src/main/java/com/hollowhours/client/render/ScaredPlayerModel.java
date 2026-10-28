package com.hollowhours.client.render;

import com.hollowhours.client.Fear;
import com.hollowhours.config.HHClientConfig;
import com.hollowhours.entity.GhostEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * The player's body acts scared (seen in third person and by other players), more the more afraid they are:
 * - standing: trembling, hugging themselves, glancing over their shoulders
 * - walking: hunched over, arms held in, shaky steps, nervous looks around
 * - running: bent forward, arms flailing, looking back at what is chasing them
 * - sneaking: creeping with the hands up, ready to flinch
 * - mining / hitting: hands shaking on every swing
 * - badly hurt: limping, one hand pressed to the side
 * - grabbed by a ghost: kicking and clawing at the air
 * - jumping: arms up to shield the head (before the story: arms thrown up for joy)
 * - eating and drinking: the hand to the mouth, chewing / head tipped back
 * The first-person arm (drawn through this model with all-zero arguments) is left alone.
 */
public class ScaredPlayerModel extends PlayerModel<AbstractClientPlayer> {
    public ScaredPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AbstractClientPlayer p, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(p, limbSwing, limbAmount, age, headYaw, headPitch);
        if (age == 0 || !HHClientConfig.PLAYER_ANIMS.get()) return;
        try {
            scare(p, limbSwing, limbAmount, age);
        } catch (Throwable ignored) {
            // never break player rendering
        }
        hat.copyFrom(head);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
    }

    void scare(AbstractClientPlayer p, float swing, float amount, float t) {
        float f = Fear.of(p, Minecraft.getInstance().getFrameTime());
        if (p.getVehicle() instanceof GhostEntity) {
            struggle(t);
            return;
        }
        if (p.isSleeping() || p.isFallFlying() || p.isVisuallySwimming() || swimAmount > 0 || p.isPassenger() || p.isSpectator()) return;
        if (!com.hollowhours.client.Atmos.haunted) {
            happy(p, swing, amount, t);
            eatDrink(p, t);
            return;
        }
        scared(p, swing, amount, t, f);
        eatDrink(p, t);
    }

    /** In the air from a jump or a fall (not just stepping down a slab). */
    static boolean airborne(AbstractClientPlayer p) {
        return !p.onGround() && !p.isInWater() && !p.getAbilities().flying && !p.onClimbable() && (p.getY() - p.yo > 0.02 || p.fallDistance > 0.4f);
    }

    /** Eating: the hand at the mouth, chewing. Drinking: the hand higher, head tipped back. */
    void eatDrink(AbstractClientPlayer p, float t) {
        if (!p.isUsingItem()) return;
        var ua = p.getUseItem().getUseAnimation();
        if (ua != net.minecraft.world.item.UseAnim.EAT && ua != net.minecraft.world.item.UseAnim.DRINK) return;
        boolean right = (p.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND) == (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        ModelPart arm = right ? rightArm : leftArm;
        float s = right ? 1 : -1;
        if (ua == net.minecraft.world.item.UseAnim.DRINK) {
            arm.xRot = -2.05f + Mth.sin(t * 0.5f) * 0.04f;
            arm.yRot = -s * 0.5f;
            arm.zRot = 0;
            head.xRot = Math.min(head.xRot, 0) - 0.45f;
        } else {
            float chew = Mth.sin(t * 1.35f);
            arm.xRot = -1.55f + chew * 0.08f;
            arm.yRot = -s * 0.55f;
            arm.zRot = 0;
            head.xRot += chew * 0.06f + 0.1f;
        }
    }

    void scared(AbstractClientPlayer p, float swing, float amount, float t, float f) {
        float seed = (p.getId() % 97) * 1.37f;
        boolean moving = amount > 0.08f;
        boolean sprint = p.isSprinting() && moving;
        boolean attack = attackTime > 0;
        float hp = p.getHealth() / Math.max(1, p.getMaxHealth());

        // ---- trembling: a fine shiver on everything, stronger with fear
        float tr = 0.006f + 0.045f * f;
        rightArm.xRot += shiver(t, seed) * tr;
        rightArm.zRot += shiver(t * 1.13f, seed + 3) * tr;
        leftArm.xRot += shiver(t * 0.97f, seed + 7) * tr;
        leftArm.zRot += shiver(t * 1.21f, seed + 11) * tr;
        head.xRot += shiver(t * 0.83f, seed + 5) * tr * 0.6f;
        head.zRot += shiver(t * 0.91f, seed + 9) * tr * 0.5f;

        // ---- nervous glances over the shoulder
        float window = 46 + (seed % 17);
        float ph = t % window;
        float dir = hash(Mth.floor(t / window) + (int) seed) > 0.5f ? 1 : -1;
        float glance = ph < 16 ? Mth.sin(ph / 16f * Mth.PI) : 0;
        if (!sprint) head.yRot += glance * dir * (0.15f + 0.5f * f);

        if (crouching) {
            // creeping, hands up in front of the chest
            float k = 0.4f + 0.6f * f;
            rightArm.xRot = Mth.lerp(k * 0.6f, rightArm.xRot, -1.0f);
            leftArm.xRot = Mth.lerp(k * 0.6f, leftArm.xRot, -1.0f);
            rightArm.yRot -= 0.25f * k;
            leftArm.yRot += 0.25f * k;
        } else if (sprint) {
            // running for your life
            float lean = 0.12f + 0.3f * f;
            hunch(lean);
            float flail = 1 + 0.7f * f;
            rightArm.xRot *= flail;
            leftArm.xRot *= flail;
            float out = Mth.abs(Mth.sin(swing * 0.6662f)) * 0.35f * f;
            rightArm.zRot += out;
            leftArm.zRot -= out;
            // looking back at it
            float back = t % 58;
            if (f > 0.35f && back < 18) {
                float k = Mth.sin(back / 18f * Mth.PI);
                float side = hash(Mth.floor(t / 58) + 13) > 0.5f ? 1 : -1;
                head.yRot += k * side * 1.15f * f;
                head.xRot -= k * 0.25f;
                body.yRot += k * side * 0.22f * f;
            }
        } else if (moving) {
            // walking hunched, arms in, small shaky steps
            hunch(0.08f + 0.26f * f);
            float in = 1 - 0.5f * f;
            rightArm.xRot = rightArm.xRot * in - 0.15f * f;
            leftArm.xRot = leftArm.xRot * in - 0.15f * f;
            rightArm.zRot -= 0.06f * f;
            leftArm.zRot += 0.06f * f;
            head.xRot += 0.12f * f;
            rightLeg.xRot *= 1 - 0.25f * f;
            leftLeg.xRot *= 1 - 0.25f * f;
        } else if (!attack) {
            // standing still: hugging yourself, more tightly the worse it gets, breathing hard
            float k = Math.max(0.85f, Mth.clamp((f - 0.3f) / 0.45f, 0, 1));
            k = k * k * (3 - 2 * k);
            float breath = Mth.sin(t * (0.12f + 0.3f * f)) * (0.03f + 0.06f * f);
            rightArm.xRot = Mth.lerp(k, rightArm.xRot, -0.95f) + breath;
            leftArm.xRot = Mth.lerp(k, leftArm.xRot, -0.95f) + breath;
            rightArm.yRot = Mth.lerp(k, rightArm.yRot, -0.55f);
            leftArm.yRot = Mth.lerp(k, leftArm.yRot, 0.55f);
            rightArm.zRot = Mth.lerp(k, rightArm.zRot, 0.12f);
            leftArm.zRot = Mth.lerp(k, leftArm.zRot, -0.12f);
            hunch(0.05f + 0.15f * k);
            head.xRot += 0.18f * k + breath * 0.5f;
        }

        // ---- jumping / falling: arms up over the head, knees pulled up
        if (airborne(p)) {
            float k = Mth.clamp(0.6f + f, 0, 1);
            rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.45f);
            leftArm.xRot = Mth.lerp(k, leftArm.xRot, -2.45f);
            rightArm.yRot = Mth.lerp(k, rightArm.yRot, -0.45f);
            leftArm.yRot = Mth.lerp(k, leftArm.yRot, 0.45f);
            rightArm.zRot = Mth.lerp(k, rightArm.zRot, 0.25f);
            leftArm.zRot = Mth.lerp(k, leftArm.zRot, -0.25f);
            head.xRot += 0.3f * k;
            rightLeg.xRot = -0.55f;
            leftLeg.xRot = 0.25f;
        }

        // ---- mining / hitting with shaking hands
        if (attack) {
            rightArm.xRot += Mth.sin(t * 3.1f + seed) * (0.03f + 0.1f * f);
            rightArm.zRot += Mth.sin(t * 2.3f + seed) * (0.02f + 0.07f * f);
        }

        // ---- badly hurt: limping, holding the side
        if (hp < 0.35f && !sprint) {
            float k = 1 - hp / 0.35f;
            if (moving) {
                rightLeg.xRot *= 1 - 0.65f * k;
                body.zRot += Mth.sin(swing * 0.6662f) * 0.1f * k;
                head.zRot += Mth.sin(swing * 0.6662f) * 0.06f * k;
            }
            leftArm.xRot = Mth.lerp(k, leftArm.xRot, -0.55f);
            leftArm.yRot = Mth.lerp(k, leftArm.yRot, 0.5f);
            leftArm.zRot = Mth.lerp(k, leftArm.zRot, 0.15f);
            head.xRot += 0.15f * k;
        }
    }

    /**
     * Before the story starts: a carefree, happy body. Skipping along with big arm swings, bouncing when running,
     * bobbing the head to a tune when standing, waving now and then, hitting things with gusto.
     */
    void happy(AbstractClientPlayer p, float swing, float amount, float t) {
        float seed = (p.getId() % 97) * 1.37f;
        boolean moving = amount > 0.08f;
        boolean sprint = p.isSprinting() && moving;
        float hp = p.getHealth() / Math.max(1, p.getMaxHealth());
        if (moving) {
            // a skip in every step
            float hop = Mth.abs(Mth.sin(swing * 0.6662f)) * (sprint ? 1.8f : 1.2f) * Math.min(1, amount * 1.5f);
            for (ModelPart part : new ModelPart[] {head, body, rightArm, leftArm, rightLeg, leftLeg}) part.y -= hop;
            float big = sprint ? 1.45f : 1.35f;
            rightArm.xRot *= big;
            leftArm.xRot *= big;
            float out = 0.12f + (sprint ? 0.1f : 0);
            rightArm.zRot += out;
            leftArm.zRot -= out;
            head.zRot += Mth.sin(swing * 0.3331f) * 0.12f;
            head.xRot -= 0.06f;
            body.yRot += Mth.sin(swing * 0.6662f) * 0.08f;
            if (sprint) body.xRot += 0.08f;
        } else if (!crouching && attackTime <= 0) {
            // humming along to something
            float beat = Mth.sin(t * 0.21f + seed);
            head.zRot += beat * 0.09f;
            head.xRot += Mth.sin(t * 0.42f + seed) * 0.04f;
            body.zRot += beat * 0.025f;
            rightArm.zRot += 0.06f + beat * 0.03f;
            leftArm.zRot -= 0.06f - beat * 0.03f;
            // and a wave now and then
            float window = 150 + (seed % 40);
            float ph = t % window;
            if (ph < 36) {
                float k = Mth.sin(ph / 36f * Mth.PI);
                k = Math.min(1, k * 1.6f);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.7f);
                rightArm.zRot = Mth.lerp(k, rightArm.zRot, -0.25f + Mth.sin(ph * 0.9f) * 0.35f);
                head.zRot -= 0.08f * k;
            }
        }
        if (attackTime > 0) {
            // hitting with gusto
            body.yRot *= 1.3f;
            rightArm.xRot -= 0.15f;
        }
        if (crouching) {
            // tiptoeing like a cartoon burglar
            float tip = Mth.sin(swing * 0.6662f);
            rightArm.xRot = -0.85f + tip * 0.25f;
            leftArm.xRot = -0.85f - tip * 0.25f;
            rightArm.zRot += 0.3f;
            leftArm.zRot -= 0.3f;
            head.zRot += Mth.sin(t * 0.3f) * 0.12f;
        }
        if (airborne(p)) {
            // a jump for joy: arms up wide, legs kicked
            rightArm.xRot = -2.85f;
            leftArm.xRot = -2.85f;
            rightArm.zRot = 0.5f;
            leftArm.zRot = -0.5f;
            rightArm.yRot = leftArm.yRot = 0;
            rightLeg.xRot = -0.45f;
            leftLeg.xRot = 0.6f;
            head.xRot -= 0.15f;
        }
        if (hp < 0.35f && moving) {
            float k = 1 - hp / 0.35f;
            rightLeg.xRot *= 1 - 0.5f * k;
            body.zRot += Mth.sin(swing * 0.6662f) * 0.08f * k;
        }
    }

    /** Held by a ghost: kicking and clawing. */
    void struggle(float t) {
        rightArm.xRot = -2.35f + Mth.sin(t * 0.9f) * 0.55f;
        rightArm.yRot = Mth.sin(t * 0.7f) * 0.3f;
        rightArm.zRot = 0.25f + Mth.sin(t * 1.3f) * 0.3f;
        leftArm.xRot = -2.2f + Mth.sin(t * 1.1f + 1) * 0.55f;
        leftArm.yRot = -Mth.sin(t * 0.8f) * 0.3f;
        leftArm.zRot = -0.25f - Mth.sin(t * 1.2f) * 0.3f;
        rightLeg.xRot = Mth.sin(t * 0.85f) * 0.85f;
        leftLeg.xRot = -Mth.sin(t * 0.85f) * 0.85f;
        rightLeg.zRot = 0.08f;
        leftLeg.zRot = -0.08f;
        head.xRot = -0.35f + Mth.sin(t * 0.5f) * 0.2f;
        head.yRot = Mth.sin(t * 0.37f) * 0.6f;
        body.zRot = Mth.sin(t * 0.6f) * 0.08f;
    }

    /** Bend the upper body forward by h radians (like a partial sneak). */
    void hunch(float h) {
        float k = h / 0.5f;
        body.xRot += h;
        rightArm.xRot += 0.4f * k;
        leftArm.xRot += 0.4f * k;
        rightLeg.z += 3.9f * k;
        leftLeg.z += 3.9f * k;
        rightLeg.y += 0.2f * k;
        leftLeg.y += 0.2f * k;
        head.y += 4.2f * k;
        body.y += 3.2f * k;
        leftArm.y += 3.2f * k;
        rightArm.y += 3.2f * k;
    }

    static float shiver(float t, float seed) {
        return Mth.sin(t * 2.7f + seed) * 0.6f + Mth.sin(t * 4.3f + seed * 1.7f) * 0.4f;
    }

    static float hash(int x) {
        x = (x ^ 61) ^ (x >>> 16);
        x *= 9;
        x ^= x >>> 4;
        x *= 0x27d4eb2d;
        x ^= x >>> 15;
        return (x & 0xFFFF) / 65535f;
    }
}
