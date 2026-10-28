package com.hollowhours.client;

import com.hollowhours.config.HHClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * First-person body language (in the spirit of "Actions & Stuff"): your hands move with what you do.
 * Before the story (nights 1-2) they are carefree: bouncing with every step, pumping when running, thrown up when you
 * jump, swaying to a tune and waving when you stand around. From night 3 they are scared: pulled in close, trembling,
 * flinching at nothing, flailing when you run, shielding your face when you jump, and when you stand still with empty
 * hands you hug yourself. Eating, drinking and scooping water from a river get their own motions.
 *
 * Both hands are drawn from one pose stack, so whatever one hand's pose added is taken back out before the next.
 */
public final class FirstPersonAnim {
    private FirstPersonAnim() {}

    // ---- per tick
    static float hug, hugO, land, landO, air, airO;
    static int still;
    static boolean wasGround = true;
    static double lastVy;
    static long sipT0 = -1000, flinchT0 = -1000;
    static float flinchSide = 1;
    static final int SIP_TICKS = 18;

    // ---- per frame
    static int frame = -1;
    static final Matrix4f applied = new Matrix4f();
    static final Matrix3f appliedN = new Matrix3f();

    /** CI: always hug when standing (ignores the 'still for a while' wait). */
    public static boolean forceHug;

    public static void startSip() {
        sipT0 = ClientFx.now();
    }

    static boolean emptyHands(LocalPlayer p) {
        return p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        hugO = hug;
        landO = land;
        airO = air;
        boolean moving = p.walkDist - p.walkDistO > 0.01f;
        boolean busy = p.isUsingItem() || p.swinging || ClientFx.now() - sipT0 < SIP_TICKS;
        still = !moving && p.onGround() && !busy ? still + 1 : 0;
        boolean scared = Atmos.haunted;
        boolean wantHug = scared && emptyHands(p) && !p.isCrouching() && (still > 14 || forceHug && !moving && !busy);
        hug += ((wantHug ? 1 : 0) - hug) * 0.16f;
        // in the air: + rising, - falling; landing hard dips the hands
        boolean ground = p.onGround() || p.isInWater() || p.getAbilities().flying || p.isPassenger();
        float vy = (float) p.getDeltaMovement().y;
        air += ((ground ? 0 : Mth.clamp(vy * 3, -1, 1)) - air) * 0.35f;
        if (ground && !wasGround) land = Math.max(land, Mth.clamp((float) -lastVy * 1.6f, 0.15f, 1));
        else land *= 0.78f;
        wasGround = ground;
        lastVy = vy;
        // scared: jumping at nothing now and then
        if (scared && ClientFx.now() - flinchT0 > 40) {
            float f = Fear.local(1);
            if (p.getRandom().nextFloat() < 0.003f + 0.012f * f) {
                flinchT0 = ClientFx.now();
                flinchSide = p.getRandom().nextBoolean() ? 1 : -1;
            }
        }
    }

    /** @return true when the event was handled here (cancel it). */
    static boolean render(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !HHClientConfig.PLAYER_ANIMS.get()) return false;
        if (frame != Atmos.frame) {
            frame = Atmos.frame;
            applied.identity();
            appliedN.identity();
        }
        float partial = e.getPartialTick();
        float hugK = Mth.lerp(partial, hugO, hug);
        if (hugK > 0.02f && emptyHands(p) && !p.isUsingItem()) {
            if (e.getHand() == InteractionHand.MAIN_HAND) hugArms(e, p, hugK, partial);
            return true;
        }
        PoseStack m = new PoseStack();
        pose(m, p, e.getHand(), e.getItemStack(), e.getSwingProgress(), partial);
        // apply (this hand's pose) * inverse(what the previous hand added)
        PoseStack.Pose last = e.getPoseStack().last();
        Matrix4f undo = new Matrix4f(applied).invert();
        Matrix3f undoN = new Matrix3f(appliedN).invert();
        last.pose().mul(undo).mul(m.last().pose());
        last.normal().mul(undoN).mul(m.last().normal());
        applied.set(m.last().pose());
        appliedN.set(m.last().normal());
        return HHClientConfig.FIRST_PERSON_ARMS.get() && holding(e, p);
    }

    // ------------------------------------------------------------------ the arm that holds the item
    /** Items drawn their own way in first person (bows, maps, shields...): left to the game. */
    static boolean special(ItemStack s) {
        return s.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem || s.getItem() instanceof net.minecraft.world.item.TridentItem
                || s.getItem() instanceof net.minecraft.world.item.MapItem || s.getItem() instanceof net.minecraft.world.item.ShieldItem
                || s.getItem() instanceof net.minecraft.world.item.SpyglassItem || s.getItem() instanceof net.minecraft.world.item.InstrumentItem
                || s.getItem() instanceof net.minecraft.world.item.BrushItem
                || net.minecraftforge.client.extensions.common.IClientItemExtensions.of(s) != net.minecraftforge.client.extensions.common.IClientItemExtensions.DEFAULT;
    }

    /**
     * An item in the hand, held by the arm: drawn here the way the game draws it (equip, swing, eating), plus the arm
     * gripping it from below, moving with it: you see your arm swing the pickaxe, raise the bread to your mouth.
     */
    static boolean holding(RenderHandEvent e, LocalPlayer p) {
        ItemStack stack = e.getItemStack();
        if (stack.isEmpty() || p.isInvisible() || special(stack) || p.isAutoSpinAttack()) return false;
        InteractionHand hand = e.getHand();
        boolean right = (hand == InteractionHand.MAIN_HAND) == (p.getMainArm() == HumanoidArm.RIGHT);
        boolean using = p.isUsingItem() && p.getUseItemRemainingTicks() > 0 && p.getUsedItemHand() == hand;
        UseAnim ua = stack.getUseAnimation();
        boolean eating = using && (ua == UseAnim.EAT || ua == UseAnim.DRINK);
        if (using && !eating && ua != UseAnim.NONE) return false;
        float i = right ? 1 : -1, eq = e.getEquipProgress(), sw = e.getSwingProgress(), partial = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        if (eating) {
            // the game's eating motion: up to the mouth, bobbing
            float f = p.getUseItemRemainingTicks() - partial + 1;
            float f1 = f / stack.getUseDuration();
            if (f1 < 0.8f) ps.translate(0, Mth.abs(Mth.cos(f / 4f * Mth.PI) * 0.1f), 0);
            float f3 = 1 - (float) Math.pow(f1, 27);
            ps.translate(f3 * 0.6f * i, f3 * -0.5f, 0);
            ps.mulPose(Axis.YP.rotationDegrees(i * f3 * 90));
            ps.mulPose(Axis.XP.rotationDegrees(f3 * 10));
            ps.mulPose(Axis.ZP.rotationDegrees(i * f3 * 30));
            ps.translate(i * 0.56f, -0.52f + eq * -0.6f, -0.72f);
        } else if (!using) {
            // the game's swing
            float sq = Mth.sqrt(sw);
            ps.translate(i * -0.4f * Mth.sin(sq * Mth.PI), 0.2f * Mth.sin(sq * Mth.TWO_PI), -0.2f * Mth.sin(sw * Mth.PI));
            ps.translate(i * 0.56f, -0.52f + eq * -0.6f, -0.72f);
            float f = Mth.sin(sw * sw * Mth.PI);
            ps.mulPose(Axis.YP.rotationDegrees(i * (45 + f * -20)));
            float f1 = Mth.sin(sq * Mth.PI);
            ps.mulPose(Axis.ZP.rotationDegrees(i * f1 * -20));
            ps.mulPose(Axis.XP.rotationDegrees(f1 * -80));
            ps.mulPose(Axis.YP.rotationDegrees(i * -45));
        } else ps.translate(i * 0.56f, -0.52f + eq * -0.6f, -0.72f);
        // the arm, from where the item is held back to camera space, so it swings and eats along with it
        ps.pushPose();
        ps.translate(i * -0.56f, 0.52f, 0.72f);
        holdingArm(ps, e.getMultiBufferSource(), e.getPackedLight(), p, right);
        ps.popPose();
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, stack,
                right ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, ps, e.getMultiBufferSource(), e.getPackedLight());
        ps.popPose();
        return true;
    }

    /** The first-person arm turned about the shoulder so its hand grips the item (angles found by projecting the arm). */
    static void holdingArm(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, boolean right) {
        float s = right ? 1 : -1;
        float sx = s * 0.4853f, sy = -0.8315f, sz = -0.5254f;
        ps.translate(s * -0.006f, 0.003f, 0.334f);
        ps.translate(sx, sy, sz);
        ps.mulPose(Axis.YP.rotationDegrees(s * 13.66f));
        ps.mulPose(Axis.XP.rotationDegrees(18.43f));
        ps.mulPose(Axis.ZP.rotationDegrees(s * -5.42f));
        ps.translate(-sx, -sy, -sz);
        vanillaArm(ps, buf, light, p, right);
    }

    /** The game's own first-person arm (no swing, fully raised). */
    static void vanillaArm(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, boolean right) {
        float s = right ? 1 : -1;
        ps.translate(s * 0.64000005f, -0.6f, -0.71999997f);
        ps.mulPose(Axis.YP.rotationDegrees(s * 45));
        ps.translate(s * -1.0f, 3.6f, 3.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(s * 120));
        ps.mulPose(Axis.XP.rotationDegrees(200));
        ps.mulPose(Axis.YP.rotationDegrees(s * -135));
        ps.translate(s * 5.6f, 0, 0);
        PlayerRenderer r = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
        if (right) r.renderRightHand(ps, buf, light, p);
        else r.renderLeftHand(ps, buf, light, p);
    }

    /** Rotate around the hand (not the camera). */
    static void around(PoseStack m, float s, Runnable rot) {
        float bx = s * 0.56f, by = -0.52f, bz = -0.72f;
        m.translate(bx, by, bz);
        rot.run();
        m.translate(-bx, -by, -bz);
    }

    static void pose(PoseStack m, LocalPlayer p, InteractionHand hand, ItemStack stack, float swing, float partial) {
        boolean right = (hand == InteractionHand.MAIN_HAND) == (p.getMainArm() == HumanoidArm.RIGHT);
        float s = right ? 1 : -1;
        float t = ClientFx.now() + partial;
        float sway = (float) (double) HHClientConfig.CAMERA_SWAY.get();
        float walk = Mth.lerp(partial, p.walkDistO, p.walkDist);
        float ph = walk * Mth.PI;
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        float move = Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) / 0.21f, 0, 1.3f);
        boolean sprint = p.isSprinting() && move > 0.1f, sneak = p.isCrouching();
        float airK = Mth.lerp(partial, airO, air), landK = Mth.lerp(partial, landO, land);
        float up = Math.abs(airK);
        boolean scared = Atmos.haunted;
        float x = 0, y = 0, z = 0, rx = 0, rz = 0;
        float stepSin = Mth.sin(ph), stepAbs = Mth.abs(stepSin);

        if (!scared) {
            // ---------------- carefree
            if (move > 0.05f) {
                y += stepAbs * (sprint ? 0.08f : 0.055f) * move;               // a skip in every step
                z += s * stepSin * (sprint ? 0.15f : 0.07f) * move;             // arms pumping
                rz += s * stepSin * (sprint ? 10 : 6) * move;
                if (sprint) {
                    y -= 0.03f;
                    rx += s * stepSin * 12;
                }
            } else {
                // swaying to a tune
                x += Mth.sin(t * 0.09f) * 0.02f;
                y += Mth.sin(t * 0.18f) * 0.013f;
                rz += Mth.sin(t * 0.09f) * 4;
                // and waving hello now and then (an empty main hand)
                float wph = t % 220;
                if (hand == InteractionHand.MAIN_HAND && stack.isEmpty() && wph < 44 && swing <= 0) {
                    float k = Mth.sin(wph / 44f * Mth.PI);
                    k = Math.min(1, k * 1.5f);
                    y += 0.2f * k;
                    x -= s * 0.06f * k;
                    rz += s * (12 + Mth.sin(t * 0.8f) * 16) * k;
                }
            }
            if (sneak) {
                y -= 0.08f - stepAbs * 0.03f * move;                             // tiptoeing
                x -= s * 0.05f;
                rz += s * 9;
            }
            // jumping for joy, a bump on landing
            y += 0.17f * up;
            rz -= s * 16 * up;
            y -= 0.11f * landK;
        } else {
            // ---------------- scared
            float f = Math.max(Fear.local(partial), 0.35f);
            x -= s * 0.09f * f;                                                  // hands pulled in close
            z += 0.05f * f;
            y -= 0.02f * f;
            float breath = Mth.sin(t * (0.15f + 0.25f * f)) * 0.01f * (0.5f + f);
            y += breath;
            if (move > 0.05f) {
                if (sprint) {
                    // running for your life
                    z += s * stepSin * 0.16f * move;
                    y += stepAbs * 0.07f * move - 0.04f;
                    rz += s * stepSin * 15 * move;
                    rx += Mth.sin(t * 1.9f) * 5 * f;
                } else {
                    // careful, shaky steps
                    y += stepAbs * 0.025f * move;
                    z += s * stepSin * 0.035f * move;
                }
            }
            if (sneak) {
                // creeping with the hands up, ready to defend
                y += 0.1f;
                x -= s * 0.06f;
                z += 0.05f;
                rx -= 10;
            }
            // jumping: shielding the face; landing: a hard jolt
            y += 0.2f * up;
            x -= s * 0.1f * up;
            rz += s * 24 * up;
            y -= 0.13f * landK;
            // flinching at nothing
            float fl = ClientFx.now() - flinchT0 + partial;
            if (fl < 12) {
                float k = Mth.sin(fl / 12f * Mth.PI);
                y += 0.09f * k;
                z += 0.08f * k;
                rz += s * flinchSide * 14 * k;
            }
            // trembling (stronger when swinging or truly afraid)
            float tf = f * sway;
            if (p.swinging) tf = Math.max(tf, 0.45f * sway);
            float a = 0.0015f + 0.011f * tf;
            x += (Mth.sin(t * 2.9f + s) * 0.6f + Mth.sin(t * 5.3f) * 0.4f) * a;
            y += (Mth.sin(t * 3.7f) * 0.6f + Mth.sin(t * 4.1f + s) * 0.4f) * a;
            rz += Mth.sin(t * 2.3f) * 0.9f * tf;
        }

        // ---- eating / drinking with the hand that holds it
        if (p.isUsingItem() && p.getUsedItemHand() == hand) {
            UseAnim ua = stack.getUseAnimation();
            float used = p.getTicksUsingItem() + partial;
            if (ua == UseAnim.DRINK) {
                float k = Mth.clamp(used / 8f, 0, 1);
                rx -= 32 * k;                                                     // tipping it up
                y += 0.05f * k;
                x -= s * 0.04f * k;
            } else if (ua == UseAnim.EAT) {
                float bite = Mth.sin(used * 1.35f);
                z += bite > 0.55f ? 0.03f : 0;                                    // bites
                rz += bite * 3;
            }
        }
        // ---- scooping water from a river with an empty hand
        float sip = ClientFx.now() - sipT0 + partial;
        if (sip < SIP_TICKS && hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
            float k = sip / SIP_TICKS;
            if (k < 0.4f) {
                float d = Mth.sin(k / 0.4f * Mth.HALF_PI);
                y -= 0.3f * d;
                z -= 0.12f * d;
                rx += 25 * d;
            } else {
                float u = Mth.sin((k - 0.4f) / 0.6f * Mth.PI);
                float d = 1 - Mth.clamp((k - 0.4f) / 0.25f, 0, 1);
                y += -0.3f * d + 0.12f * u;
                z += -0.12f * d + 0.12f * u;
                x -= s * 0.22f * u;
                rx += 25 * d - 40 * u;
            }
        }

        m.translate(x, y, z);
        final float frx = rx, frz = rz;
        if (frx != 0 || frz != 0) around(m, s, () -> {
            m.mulPose(Axis.XP.rotationDegrees(frx));
            m.mulPose(Axis.ZP.rotationDegrees(frz));
        });
    }

    /** Standing scared with empty hands: both arms come up and cross over the chest, trembling, breathing hard. */
    static void hugArms(RenderHandEvent e, AbstractClientPlayer p, float k, float partial) {
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float t = ClientFx.now() + partial;
        float f = Math.max(Fear.local(partial), 0.35f);
        float breath = Mth.sin(t * (0.15f + 0.25f * f)) * 0.012f * (0.5f + f);
        float a = (0.002f + 0.01f * f) * (float) (double) HHClientConfig.CAMERA_SWAY.get();
        boolean mainRight = p.getMainArm() == HumanoidArm.RIGHT;
        // the far arm first, so the near one crosses over it
        drawArm(ps, buf, light, p, !mainRight, k, t, breath, a);
        drawArm(ps, buf, light, p, mainRight, k, t, breath, a);
    }

    static void drawArm(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, boolean right, float k, float t, float breath, float shake) {
        float s = right ? 1 : -1;
        boolean mainArm = (p.getMainArm() == HumanoidArm.RIGHT) == right;
        ps.pushPose();
        // the arm that wasn't on screen comes up from below
        if (!mainArm) ps.translate(0, -0.7f * (1 - k), 0);
        ps.translate((Mth.sin(t * 2.9f + s) * 0.6f + Mth.sin(t * 5.3f) * 0.4f) * shake, (Mth.sin(t * 3.7f + s) * 0.6f) * shake + breath, 0);
        // turned in across the chest around the shoulder, so the two forearms cross low on the screen
        // (angles found by projecting the arm's box through this same transform chain)
        ps.translate(-s * 0.41f * k, (0.29f + (mainArm ? 0 : 0.03f)) * k, (0.11f + (mainArm ? 0 : 0.01f)) * k);
        float bx = s * 0.64f, by = -0.6f, bz = -0.72f;
        ps.translate(bx, by, bz);
        ps.mulPose(Axis.YP.rotationDegrees(s * 89 * k));
        ps.mulPose(Axis.XP.rotationDegrees(-28 * k));
        ps.translate(-bx, -by, -bz);
        vanillaArm(ps, buf, light, p, right);
        ps.popPose();
    }
}
