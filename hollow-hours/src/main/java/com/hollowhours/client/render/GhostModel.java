package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

/**
 * One model per ghost (geo/entity/ghost_N.geo.json) sharing one animation file. Each ghost feeds its own molang
 * variables (crawl height, fold, peek ...) so the shared animations fit every body. On top of the keyframes the
 * client adds unnatural motion: head ticks, finger spasms and a jaw that never quite closes.
 */
public class GhostModel extends GeoModel<GhostEntity> {
    private final GhostKind kind;
    private final ResourceLocation model, texture, anims;
    private final BoneEdits edits = new BoneEdits();

    public GhostModel(GhostKind kind) {
        this.kind = kind;
        this.model = HollowHours.id("geo/entity/ghost_" + kind.n + ".geo.json");
        this.texture = HollowHours.id("textures/entity/ghost_" + kind.n + ".png");
        this.anims = HollowHours.id("animations/entity/ghost.animation.json");
    }

    @Override
    public ResourceLocation getModelResource(GhostEntity g) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(GhostEntity g) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(GhostEntity g) {
        return anims;
    }

    @Override
    //#if MC>=12100
    //$$ public void applyMolangQueries(software.bernie.geckolib.animation.AnimationState<GhostEntity> state, double animTime) {
    //$$     super.applyMolangQueries(state, animTime);
    //$$     GhostEntity g = state.getAnimatable();
    //#else
    public void applyMolangQueries(GhostEntity g, double animTime) {
        super.applyMolangQueries(g, animTime);
        MolangParser p = MolangParser.INSTANCE;
    //#endif
        for (Map.Entry<String, Double> e : kind.molang.entrySet()) {
            double v = e.getValue();
            molang("variable." + e.getKey(), () -> v);
        }
        molang("variable.rage", () -> g.isRaged() ? 1 : 0);
        molang("variable.seed", () -> g.seed);
        // Bedrock's query.modified_distance_moved (metres walked) and the entity's pre_animation script
        float partial = com.hollowhours.compat.ClientCompat.partialTick();
        double dist = g.walkAnimation.position(partial) / 4.0;
        double life = (g.tickCount + partial) / 20.0;
        molang("variable.dist_moved", () -> dist);
        molang("variable.cph", () -> dist * 45.0 + life * 90.0);
    }

    @Override
    public void setCustomAnimations(GhostEntity g, long instanceId, AnimationState<GhostEntity> state) {
        super.setCustomAnimations(g, instanceId, state);
        edits.begin();
        float lt = (g.tickCount + state.getPartialTick()) / 20f;
        GhostAnim a = g.anim();
        boolean holding = a == GhostAnim.GRAB || a == GhostAnim.HOLD || a == GhostAnim.SCREAM || a == GhostAnim.KILL;
        // ---- look: the head follows its target (as the Bedrock "look" layer did)
        boolean look = switch (a) {
            case IDLE, STALK, SPAWN, SNAP, CHASE, FOLLOW_RUN -> true;
            default -> false;
        };
        EntityModelData md = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (look && md != null) {
            // GeckoLib hands these over already flipped into its own convention
            rot("head", -Mth.clamp(md.headPitch(), -40, 40), -Mth.clamp(md.netHeadYaw(), -70, 70), 0);
        }
        // ---- deform: each body breathes and shifts its own way
        switch (kind) {
            case WAIL -> {
                scale("chest", 1, 1, 1 + sin(lt * 110) * 0.035f);
                scale("neck", 1, 1 + sin(lt * 37) * 0.04f, 1);
            }
            case WIDOW -> {
                scale("chest", 1, 1, 1 + sin(lt * 90) * 0.03f);
                rot("hair", sin(lt * 41) * 3, 0, sin(lt * 29) * 3);
            }
            case ASH -> scale("chest", 1 + sin(lt * 70) * 0.025f, 1, 1 + sin(lt * 70) * 0.05f);
            case VOID -> {
                scale("chest", 1, 1, 1 + sin(lt * 80) * 0.03f);
                rot("extraArmR", sin(lt * 63) * 9, 0, sin(lt * 47) * 6);
                rot("extraArmL", sin(lt * 59 + 90) * 9, 0, -sin(lt * 51) * 6);
            }
            case STAG -> scale("chest", 1, 1, 1 + sin(lt * 100) * 0.04f);
        }
        if (holding) return;
        // ---- twitch: sudden snaps of the head, jaw, arms (idle / stalking)
        if (a == GhostAnim.IDLE || a == GhostAnim.STALK || a == GhostAnim.SPAWN) {
            rot("head", (mod(lt, 3.1f) > 2.9f ? -22 : 0), (mod(lt, 4.3f) > 4.05f ? 55 : 0) - (mod(lt, 7.7f) > 7.4f ? 80 : 0),
                    (mod(lt, 2.3f) > 2.15f ? 38 : 0) - (mod(lt, 5.9f) > 5.7f ? 60 : 0));
            rot("jaw", mod(lt, 5.3f) > 5.0f ? 50 : 0, 0, 0);
            rot("rightArm", mod(lt, 3.7f) > 3.5f ? -40 : 0, 0, mod(lt, 2.9f) > 2.8f ? 25 : 0);
            rot("leftArm", mod(lt, 4.7f) > 4.5f ? -50 : 0, 0, mod(lt, 3.3f) > 3.2f ? -25 : 0);
            rot("rightHand", mod(lt, 1.9f) > 1.75f ? 40 : 0, 0, 0);
            rot("leftHand", mod(lt, 2.1f) > 1.95f ? -40 : 0, 0, 0);
            rot("body", mod(lt, 6.1f) > 5.9f ? 18 : 0, 0, mod(lt, 8.3f) > 8.1f ? 12 : 0);
        }
        // ---- spasm: while chasing the whole body shakes
        if (a == GhostAnim.CHASE || a == GhostAnim.FOLLOW_RUN || a == GhostAnim.CRAWL_CHASE || a == GhostAnim.FOLLOW_CRAWL) {
            CoreGeoBone root = getAnimationProcessor().getBone("root");
            if (root != null) edits.addPos(root, -sin(lt * 1900) * 0.35f, 0, cos(lt * 1700) * 0.35f);
            rot("head", sin(lt * 1500) * 14, sin(lt * 1100) * 35, sin(lt * 1300) * 22);
            rot("jaw", 30 + sin(lt * 2400) * 28, 0, 0);
            rot("rightArm", sin(lt * 1400) * 22, 0, sin(lt * 1000) * 14);
            rot("leftArm", sin(lt * 1300 + 90) * 22, 0, sin(lt * 900) * 14);
            rot("rightHand", sin(lt * 2200) * 35, 0, 0);
            rot("leftHand", sin(lt * 2100 + 40) * 35, 0, 0);
            rot("chest", 0, sin(lt * 1200) * 10, 0);
        }
        // ---- searching on all fours: the head sweeps
        if (a == GhostAnim.CRAWL_SEARCH) rot("head", mod(lt, 3.3f) > 3.05f ? -22 : 0, sin(lt * 38) * 30, 0);
        // fingers never stop moving
        rot("fingers_l", sin(lt * 520 + g.seed) * 8, 0, 0);
        rot("fingers_r", sin(lt * 470 + g.seed * 2) * 8, 0, 0);
    }

    /** Adds a rotation given in Bedrock degrees (x and y are flipped in GeckoLib). */
    void rot(String bone, float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) return;
        CoreGeoBone b = getAnimationProcessor().getBone(bone);
        if (b == null) return;
        edits.addRot(b, -x * Mth.DEG_TO_RAD, -y * Mth.DEG_TO_RAD, z * Mth.DEG_TO_RAD);
    }

    void scale(String bone, float x, float y, float z) {
        CoreGeoBone b = getAnimationProcessor().getBone(bone);
        if (b == null) return;
        edits.mulScale(b, x, y, z);
    }

    /** Bedrock math: sin/cos take degrees. */
    static float sin(float deg) {
        return Mth.sin(deg * Mth.DEG_TO_RAD);
    }

    static float cos(float deg) {
        return Mth.cos(deg * Mth.DEG_TO_RAD);
    }

    static float mod(float a, float b) {
        return a - (float) Math.floor(a / b) * b;
    }

    static float hash(int x) {
        x = (x ^ 61) ^ (x >>> 16);
        x *= 9;
        x ^= x >>> 4;
        x *= 0x27d4eb2d;
        x ^= x >>> 15;
        return (x & 0xFFFF) / 65535f;
    }

    static void molang(String name, java.util.function.DoubleSupplier value) {
        //#if MC>=12100
        //$$ software.bernie.geckolib.loading.math.MathParser.setVariable(name, value);
        //#else
        MolangParser.INSTANCE.setValue(name, value);
        //#endif
    }
}
