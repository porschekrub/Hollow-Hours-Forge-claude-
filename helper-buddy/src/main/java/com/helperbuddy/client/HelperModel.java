package com.helperbuddy.client;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.constant.DataTickets;

/** The helper's model: 6 colour skins, the head follows where it looks. */
public class HelperModel extends GeoModel<HelperEntity> {
    private final BoneEdits edits = new BoneEdits();
    static final ResourceLocation MODEL = HelperBuddy.id("geo/entity/helper.geo.json");
    static final ResourceLocation ANIMS = HelperBuddy.id("animations/entity/helper.animation.json");
    static final ResourceLocation[] TEX = new ResourceLocation[HelperEntity.SKINS];

    static {
        for (int i = 0; i < TEX.length; i++) TEX[i] = HelperBuddy.id("textures/entity/helper/helper_" + i + ".png");
    }

    @Override
    public ResourceLocation getModelResource(HelperEntity h) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HelperEntity h) {
        return TEX[Mth.clamp(h.skin(), 0, TEX.length - 1)];
    }

    @Override
    public ResourceLocation getAnimationResource(HelperEntity h) {
        return ANIMS;
    }

    @Override
    public void setCustomAnimations(HelperEntity h, long instanceId, AnimationState<HelperEntity> state) {
        super.setCustomAnimations(h, instanceId, state);
        edits.begin();
        if (h.isKO() || h.isInWater()) return;
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && d != null) edits.addRot(head, d.headPitch() * Mth.DEG_TO_RAD * 0.8f, d.netHeadYaw() * Mth.DEG_TO_RAD * 0.8f, 0);
    }
}
