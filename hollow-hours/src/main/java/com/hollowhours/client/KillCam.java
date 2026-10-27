package com.hollowhours.client;

import com.hollowhours.config.HHClientConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.ghost.KillTracks;
import com.hollowhours.net.S2CKill;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The victim's side of a kill: your camera is locked onto the ghost's face (the track says where its head is),
 * you can't move or use anything, and every blow in the animation lands on your screen (shake, blood, red flash,
 * a black frame on the big ones).
 */
public final class KillCam {
    private KillCam() {}

    public static int ghostId = -1, kind, phase = -1, track = -1;
    static int nextEvent;
    static float lastT;
    static long startedAt, endedAt = -100000;
    static float hurtTilt;
    static boolean prone;

    public static boolean active() {
        return phase >= 0 && phase != S2CKill.END && phase != S2CKill.RELEASE;
    }

    static void packet(S2CKill m) {
        Minecraft mc = Minecraft.getInstance();
        switch (m.phase()) {
            case S2CKill.END, S2CKill.RELEASE -> {
                if (m.phase() == S2CKill.RELEASE) ClientFx.fade(0xFFFFFF, 1, 2, 16);
                else endedAt = ClientFx.now();
                phase = m.phase();
                track = -1;
                ghostId = -1;
                unprone();
                if (mc.screen instanceof BetrayalScreen) mc.setScreen(null);
                return;
            }
            default -> {}
        }
        ghostId = m.ghostId();
        kind = m.kind();
        phase = m.phase();
        track = m.anim();
        nextEvent = 0;
        lastT = 0;
        startedAt = ClientFx.now();
        if (m.phase() == S2CKill.GRAB) {
            ClientFx.addShake(1.2f, 12);
            ClientFx.addStatic(0.45f, 8);
            if (!HHClientConfig.REDUCE_FLASHING.get()) ClientFx.fade(0x5A0000, 0, 1, 8);
        } else if (m.phase() == S2CKill.SCREAM) {
            ClientFx.addShake(1.8f, Math.max(20, m.ticks()));
            ClientFx.addStatic(0.35f, m.ticks());
        } else if (m.phase() == S2CKill.KILL) {
            ClientFx.addBlood(0.35f, 40);
        }
    }

    public static GhostEntity ghost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ghostId < 0) return null;
        Entity e = mc.level.getEntity(ghostId);
        return e instanceof GhostEntity g ? g : null;
    }

    /** Fire the track's events on the victim's screen as the animation plays. */
    static void tick() {
        if (!active()) return;
        GhostEntity g = ghost();
        if (g == null) {
            if (ClientFx.now() - startedAt > 40) phase = -1;
            return;
        }
        KillTracks.Track tr = KillTracks.get(track);
        if (tr == null) return;
        float t = g.trackTime(0);
        // pinned to the floor: lie flat (the server does the same)
        var pl = net.minecraft.client.Minecraft.getInstance().player;
        if (pl != null) {
            boolean pr = tr.proneAt(t);
            if (pr != prone) {
                prone = pr;
                pl.setForcedPose(pr ? net.minecraft.world.entity.Pose.SWIMMING : null);
            }
        }
        if (t + 0.05f < lastT) nextEvent = 0;
        lastT = t;
        while (nextEvent < tr.events.size() && tr.events.get(nextEvent).t() <= t) {
            fire(tr.events.get(nextEvent));
            nextEvent++;
        }
        if (hurtTilt > 0) hurtTilt = Math.max(0, hurtTilt - 1.5f);
    }

    static void fire(KillTracks.Event e) {
        boolean calm = HHClientConfig.REDUCE_FLASHING.get();
        float s = e.strength();
        switch (e.type()) {
            case "hit" -> {
                ClientFx.addShake(0.8f + 0.9f * s, 8 + (int) (6 * s));
                ClientFx.addBlood(0.35f + 0.4f * s, 30);
                if (!calm) ClientFx.fade(0x7A0000, 0, 1, 5);
                hurtTilt = 10 + 10 * s;
            }
            case "final" -> {
                ClientFx.addShake(2.5f, 20);
                ClientFx.addBlood(1f, 80);
                ClientFx.fade(0x000000, 0, 30, 20);
            }
            case "fx" -> {
                switch (e.arg()) {
                    case "black" -> ClientFx.fade(0x000000, 0, (int) (4 * s) + 2, 6);
                    case "white" -> {
                        if (!calm) ClientFx.fade(0xFFFFFF, 0, 1, 6);
                    }
                    case "red" -> ClientFx.fade(0x6A0000, 1, 3, 10);
                    case "static" -> ClientFx.addStatic(0.6f * s, 14);
                    case "blur", "dizzy" -> ClientFx.addShake(0.4f, 30);
                    case "face" -> {
                        if (HHClientConfig.FACE_CLOSEUPS.get()) Jumpscare.start(kind, Jumpscare.FLASH, 3);
                    }
                    default -> {}
                }
            }
            default -> {}
        }
    }

    /** {yaw, pitch, roll} for the camera, or null when not in a kill. */
    static float[] camera(float partial) {
        if (!active()) return null;
        Minecraft mc = Minecraft.getInstance();
        GhostEntity g = ghost();
        if (g == null || mc.player == null) return null;
        KillTracks.Track tr = KillTracks.get(track);
        Vec3 headOff = tr == null ? new Vec3(0, g.kind.height * 0.85, 0) : tr.headAt(g.trackTime(partial));
        float yaw = Mth.rotLerp(partial, g.yBodyRotO, g.yBodyRot) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double lx = fz, lz = -fx;
        Vec3 gp = g.getPosition(partial);
        double sx = g.kind.scaleXZ, sy = g.kind.scaleY;
        Vec3 target = new Vec3(gp.x + (fx * headOff.x + lx * headOff.z) * sx, gp.y + headOff.y * sy, gp.z + (fz * headOff.x + lz * headOff.z) * sx);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float camYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float camPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        float roll = tr == null ? 0 : tr.rollAt(g.trackTime(partial));
        return new float[]{camYaw, camPitch, roll + hurtTilt * (float) Math.sin(ClientFx.now() * 0.9)};
    }

    static void unprone() {
        if (prone) {
            prone = false;
            var pl = net.minecraft.client.Minecraft.getInstance().player;
            if (pl != null) pl.setForcedPose(null);
        }
    }

    static void reset() {
        unprone();
        phase = -1;
        track = -1;
        ghostId = -1;
    }
}
