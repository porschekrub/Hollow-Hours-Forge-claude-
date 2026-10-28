package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.config.HHClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First person: look down and there you are. Your own body and legs are drawn (no head, no arms - the first-person
 * hands take care of those), a little behind the eyes, with every third-person animation: the skip in your step on the
 * first nights, the hunch and the shaky legs later, sneaking, jumping, sitting on a swing.
 */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonBody {
    private FirstPersonBody() {}

    /** how far behind the eyes the body stands: just enough not to see into your own chest, close enough to see it */
    static final double BACK = 0.06;
    static boolean drawing;

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || drawing) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !HHClientConfig.FIRST_PERSON_BODY.get() || !mc.options.getCameraType().isFirstPerson() || e.getCamera().isDetached()
                || e.getCamera().getEntity() != p || p.isSleeping() || p.isSpectator() || p.isInvisible() || p.isFallFlying() || p.isVisuallySwimming()
                || p.isAutoSpinAttack() || KillCam.active() || Jumpscare.active()
                // hiding under a bed or in a closet: the camera is squeezed in, a body would fill the view
                || p.getVehicle() instanceof com.hollowhours.entity.HideSeatEntity || p.getVehicle() instanceof com.hollowhours.entity.ClosetEntity
                || p.getVehicle() instanceof com.hollowhours.entity.GhostEntity) return;
        float partial = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        double x = Mth.lerp(partial, p.xo, p.getX()), y = Mth.lerp(partial, p.yo, p.getY()), z = Mth.lerp(partial, p.zo, p.getZ());
        float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        double back = BACK + (p.isCrouching() ? 0.14 : 0);
        x += Mth.sin(bodyYaw) * back;
        z -= Mth.cos(bodyYaw) * back;
        // what you hold and the chestplate are drawn by the first-person hands / would hang in the air without arms
        var inv = p.getInventory();
        ItemStack main = inv.items.get(inv.selected), off = inv.offhand.get(0), chest = p.getItemBySlot(EquipmentSlot.CHEST);
        inv.items.set(inv.selected, ItemStack.EMPTY);
        inv.offhand.set(0, ItemStack.EMPTY);
        inv.armor.set(EquipmentSlot.CHEST.getIndex(), ItemStack.EMPTY);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        drawing = true;
        try {
            var d = mc.getEntityRenderDispatcher();
            d.render(p, x - cam.x, y - cam.y, z - cam.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial, e.getPoseStack(), buf,
                    d.getPackedLightCoords(p, partial));
            buf.endBatch();
        } catch (Throwable ex) {
            HollowHours.LOG.debug("[Hollow Hours] first-person body skipped", ex);
        } finally {
            drawing = false;
            inv.items.set(inv.selected, main);
            inv.offhand.set(0, off);
            inv.armor.set(EquipmentSlot.CHEST.getIndex(), chest);
        }
    }

    /** While drawing the first-person body: no head, no arms. */
    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre e) {
        if (!drawing) return;
        PlayerModel<?> m = e.getRenderer().getModel();
        m.head.visible = false;
        m.hat.visible = false;
        m.rightArm.visible = false;
        m.leftArm.visible = false;
        m.rightSleeve.visible = false;
        m.leftSleeve.visible = false;
    }
}
