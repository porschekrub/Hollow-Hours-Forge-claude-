package net.minecraftforge.client.event;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.eventbus.api.Event;

public class InputEvent extends Event {
    /** Attack / use / pick block about to happen (cancelled: it does not; the hand swings when shouldSwingHand). */
    public static class InteractionKeyMappingTriggered extends InputEvent {
        private final int button;
        private final KeyMapping keyMapping;
        private final InteractionHand hand;
        private boolean handSwing = true;

        public InteractionKeyMappingTriggered(int button, KeyMapping keyMapping, InteractionHand hand) {
            this.button = button;
            this.keyMapping = keyMapping;
            this.hand = hand;
        }

        public boolean isAttack() {
            return button == 0;
        }

        public boolean isUseItem() {
            return button == 1;
        }

        public boolean isPickBlock() {
            return button == 2;
        }

        public void setSwingHand(boolean v) {
            handSwing = v;
        }

        public boolean shouldSwingHand() {
            return handSwing;
        }

        public InteractionHand getHand() {
            return hand;
        }

        public KeyMapping getKeyMapping() {
            return keyMapping;
        }

        public static InteractionKeyMappingTriggered attack() {
            return new InteractionKeyMappingTriggered(0, Minecraft.getInstance().options.keyAttack, InteractionHand.MAIN_HAND);
        }

        public static InteractionKeyMappingTriggered use(InteractionHand hand) {
            return new InteractionKeyMappingTriggered(1, Minecraft.getInstance().options.keyUse, hand);
        }
    }
}
