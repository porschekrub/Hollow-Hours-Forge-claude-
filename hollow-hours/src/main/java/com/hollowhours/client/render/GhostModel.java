package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
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
    public void applyMolangQueries(GhostEntity g, double animTime) {
        super.applyMolangQueries(g, animTime);
        MolangParser p = MolangParser.INSTANCE;
        for (Map.Entry<String, Double> e : kind.molang.entrySet()) {
            double v = e.getValue();
            p.setValue("variable." + e.getKey(), () -> v);
        }
        p.setValue("variable.rage", () -> g.isRaged() ? 1 : 0);
        p.setValue("variable.seed", () -> g.seed);
    }

    @Override
    public void setCustomAnimations(GhostEntity g, long instanceId, AnimationState<GhostEntity> state) {
        super.setCustomAnimations(g, instanceId, state);
        double tick = state.getData(DataTickets.TICK) == null ? g.tickCount : state.getData(DataTickets.TICK);
        float t = (float) tick + state.getPartialTick();
        GhostAnim a = g.anim();
        boolean holding = a == GhostAnim.GRAB || a == GhostAnim.HOLD || a == GhostAnim.SCREAM || a == GhostAnim.KILL;
        // sudden head ticks: snap to an angle, hold, snap back (stronger when enraged)
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head != null && !holding) {
            float s = g.seed;
            int slot = (int) Math.floor((t + s) / 9f);
            float r = hash(slot * 31 + (int) s);
            float amt = g.isRaged() ? 0.55f : 0.28f;
            if (r > 0.72f) {
                float k = hash(slot * 17 + 3) * 2 - 1;
                head.setRotZ(head.getRotZ() + k * amt);
                head.setRotX(head.getRotX() + (hash(slot * 7 + 5) - 0.5f) * amt * 0.6f);
            }
            // tiny constant tremor
            head.setRotY(head.getRotY() + Mth.sin(t * 2.7f + s) * 0.02f);
        }
        // the jaw keeps working, faster when angry
        CoreGeoBone jaw = getAnimationProcessor().getBone("jaw");
        if (jaw != null && !holding) jaw.setRotX(jaw.getRotX() + (0.5f + 0.5f * Mth.sin(t * (g.isRaged() ? 1.3f : 0.35f) + g.seed)) * 0.12f);
        // fingers spasm
        for (String f : new String[]{"fingers_l", "fingers_r"}) {
            CoreGeoBone b = getAnimationProcessor().getBone(f);
            if (b != null) b.setRotX(b.getRotX() + Mth.sin(t * 1.9f + g.seed + f.length()) * 0.08f);
        }
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
