package com.hollowhours.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Skips model cubes that touch the camera. Entity poses are camera-relative (the eye is the origin), so a cube whose
 * box (grown by a small margin) contains the origin would be drawn from the inside: a screen full of stretched
 * polygons. That happens when a ghost's arm swings through the victim's face during a kill, or a ghost walks through you.
 */
public final class NearClip {
    private NearClip() {}

    /** Distance (blocks) from the camera to the cube's box, for diagnostics. */
    public static float distance(PoseStack poseStack, GeoCube cube) {
        GeoQuad[] quads = cube.quads();
        if (quads == null) return 99;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (GeoQuad q : quads) {
            if (q == null) continue;
            for (GeoVertex v : q.vertices()) {
                Vector3f p = v.position();
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
            }
        }
        poseStack.pushPose();
        RenderUtils.translateToPivotPoint(poseStack, cube);
        RenderUtils.rotateMatrixAroundCube(poseStack, cube);
        RenderUtils.translateAwayFromPivotPoint(poseStack, cube);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        float scale = new Vector3f(m.m00(), m.m01(), m.m02()).length();
        Vector4f c = m.invert(new Matrix4f()).transform(new Vector4f(0, 0, 0, 1));
        float dx = Math.max(0, Math.max(minX - c.x, c.x - maxX)), dy = Math.max(0, Math.max(minY - c.y, c.y - maxY)), dz = Math.max(0, Math.max(minZ - c.z, c.z - maxZ));
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * scale;
    }

    public static boolean touchesCamera(PoseStack poseStack, GeoCube cube, float marginBlocks) {
        GeoQuad[] quads = cube.quads();
        if (quads == null) return false;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean any = false;
        for (GeoQuad q : quads) {
            if (q == null) continue;
            for (GeoVertex v : q.vertices()) {
                Vector3f p = v.position();
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
                any = true;
            }
        }
        if (!any) return false;
        poseStack.pushPose();
        RenderUtils.translateToPivotPoint(poseStack, cube);
        RenderUtils.rotateMatrixAroundCube(poseStack, cube);
        RenderUtils.translateAwayFromPivotPoint(poseStack, cube);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        float scale = new Vector3f(m.m00(), m.m01(), m.m02()).length();
        if (!(scale > 1e-5f)) return false;
        float det = m.determinant();
        if (!Float.isFinite(det) || Math.abs(det) < 1e-12f) return false;
        Vector4f c = m.invert(new Matrix4f()).transform(new Vector4f(0, 0, 0, 1));
        float mg = marginBlocks / scale;
        return c.x > minX - mg && c.x < maxX + mg && c.y > minY - mg && c.y < maxY + mg && c.z > minZ - mg && c.z < maxZ + mg;
    }
}
