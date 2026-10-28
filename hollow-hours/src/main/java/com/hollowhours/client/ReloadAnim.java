package com.hollowhours.client;

import com.hollowhours.registry.HHItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * Putting a battery in the flashlight, in first person: the flashlight tips up, the battery slides up into its tail,
 * a click, and the flashlight swings back down.
 */
public final class ReloadAnim {
    private ReloadAnim() {}

    static final int LEN = 26;
    static long t0 = -1000;

    static void start() {
        t0 = ClientFx.now();
    }

    static boolean active() {
        return ClientFx.now() - t0 < LEN;
    }

    /** @return true when the hand was drawn here (the event is then cancelled). */
    static boolean render(RenderHandEvent e) {
        if (!active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack stack = e.getItemStack();
        boolean flash = stack.is(HHItems.FLASHLIGHT.get());
        // the hand that held the battery is busy (the battery is going in)
        if (!flash) return stack.is(HHItems.BATTERY.get());
        float t = (ClientFx.now() - t0 + e.getPartialTick()) / LEN;
        boolean right = (e.getHand() == InteractionHand.MAIN_HAND) == (mc.player.getMainArm() == HumanoidArm.RIGHT);
        float side = right ? 1 : -1;
        // tilt: up during the first third, held, down at the end
        float tilt = t < 0.3f ? t / 0.3f : t > 0.8f ? 1 - (t - 0.8f) / 0.2f : 1;
        tilt = tilt * tilt * (3 - 2 * tilt);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(side * 0.56f - side * 0.18f * tilt, -0.52f + 0.12f * tilt, -0.72f);
        ps.mulPose(Axis.XP.rotationDegrees(70 * tilt));
        ps.mulPose(Axis.ZP.rotationDegrees(side * 12 * tilt));
        var ir = mc.getEntityRenderDispatcher().getItemInHandRenderer();
        ir.renderItem(mc.player, stack, right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !right, ps,
                e.getMultiBufferSource(), e.getPackedLight());
        // the battery: comes up from below the tail and disappears into it
        if (t > 0.25f && t < 0.72f) {
            float k = Mth.clamp((t - 0.25f) / 0.42f, 0, 1);
            k = k * k * (3 - 2 * k);
            ps.pushPose();
            // along the flashlight's axis (its tail is at z 0.24 in this space, see models3d.py), terminal first
            ps.translate(side * 0.094f, 0.1875f, 0.50f - 0.40f * k);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.scale(0.55f, 0.55f, 0.55f);
            ir.renderItem(mc.player, new ItemStack(HHItems.BATTERY.get()), ItemDisplayContext.FIXED, false, ps, e.getMultiBufferSource(), e.getPackedLight());
            ps.popPose();
        }
        ps.popPose();
        return true;
    }
}
