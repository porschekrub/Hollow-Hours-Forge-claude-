package net.minecraftforge.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.eventbus.api.Event;

public class ViewportEvent extends Event {
    private final GameRenderer renderer;
    private final Camera camera;
    private final double partialTick;

    public ViewportEvent(GameRenderer renderer, Camera camera, double partialTick) {
        this.renderer = renderer;
        this.camera = camera;
        this.partialTick = partialTick;
    }

    public GameRenderer getRenderer() {
        return renderer;
    }

    public Camera getCamera() {
        return camera;
    }

    public double getPartialTick() {
        return partialTick;
    }

    /** Fog distances (cancelled: the event's values are used). */
    public static class RenderFog extends ViewportEvent {
        private final FogRenderer.FogMode mode;
        private final FogType type;
        private float farPlaneDistance, nearPlaneDistance;
        private FogShape fogShape;

        public RenderFog(FogRenderer.FogMode mode, FogType type, Camera camera, float partialTick, float near, float far, FogShape shape) {
            super(net.minecraft.client.Minecraft.getInstance().gameRenderer, camera, partialTick);
            this.mode = mode;
            this.type = type;
            this.nearPlaneDistance = near;
            this.farPlaneDistance = far;
            this.fogShape = shape;
        }

        public FogRenderer.FogMode getMode() {
            return mode;
        }

        public FogType getType() {
            return type;
        }

        public float getFarPlaneDistance() {
            return farPlaneDistance;
        }

        public void setFarPlaneDistance(float d) {
            farPlaneDistance = d;
        }

        public float getNearPlaneDistance() {
            return nearPlaneDistance;
        }

        public void setNearPlaneDistance(float d) {
            nearPlaneDistance = d;
        }

        public FogShape getFogShape() {
            return fogShape;
        }

        public void setFogShape(FogShape s) {
            fogShape = s;
        }

        public void scaleFarPlaneDistance(float f) {
            farPlaneDistance *= f;
        }

        public void scaleNearPlaneDistance(float f) {
            nearPlaneDistance *= f;
        }
    }

    public static class ComputeFogColor extends ViewportEvent {
        private float red, green, blue;

        public ComputeFogColor(Camera camera, float partialTick, float red, float green, float blue) {
            super(net.minecraft.client.Minecraft.getInstance().gameRenderer, camera, partialTick);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public float getRed() {
            return red;
        }

        public void setRed(float v) {
            red = v;
        }

        public float getGreen() {
            return green;
        }

        public void setGreen(float v) {
            green = v;
        }

        public float getBlue() {
            return blue;
        }

        public void setBlue(float v) {
            blue = v;
        }
    }

    public static class ComputeCameraAngles extends ViewportEvent {
        private float yaw, pitch, roll;

        public ComputeCameraAngles(GameRenderer renderer, Camera camera, double partialTick, float yaw, float pitch, float roll) {
            super(renderer, camera, partialTick);
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float v) {
            yaw = v;
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float v) {
            pitch = v;
        }

        public float getRoll() {
            return roll;
        }

        public void setRoll(float v) {
            roll = v;
        }
    }

    public static class ComputeFov extends ViewportEvent {
        private final boolean usedConfiguredFov;
        private double fov;

        public ComputeFov(GameRenderer renderer, Camera camera, double partialTick, double fov, boolean usedConfiguredFov) {
            super(renderer, camera, partialTick);
            this.fov = fov;
            this.usedConfiguredFov = usedConfiguredFov;
        }

        public double getFOV() {
            return fov;
        }

        public void setFOV(double fov) {
            this.fov = fov;
        }

        public boolean usedConfiguredFov() {
            return usedConfiguredFov;
        }
    }
}
