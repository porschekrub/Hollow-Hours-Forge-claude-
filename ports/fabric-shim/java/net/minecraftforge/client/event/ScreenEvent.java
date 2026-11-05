package net.minecraftforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

public class ScreenEvent extends Event {
    private final Screen screen;

    public ScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }

    public static class Init extends ScreenEvent {
        private final List<GuiEventListener> listeners;

        public Init(Screen screen, List<GuiEventListener> listeners) {
            super(screen);
            this.listeners = listeners;
        }

        public List<GuiEventListener> getListenersList() {
            return listeners;
        }

        public static class Post extends Init {
            public Post(Screen screen, List<GuiEventListener> listeners) {
                super(screen, listeners);
            }
        }
    }

    public static class Render extends ScreenEvent {
        private final GuiGraphics g;
        private final int mouseX, mouseY;
        private final float partialTick;

        public Render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super(screen);
            this.g = g;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() {
            return g;
        }

        public int getMouseX() {
            return mouseX;
        }

        public int getMouseY() {
            return mouseY;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public static class Post extends Render {
            public Post(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                super(screen, g, mouseX, mouseY, partialTick);
            }
        }
    }

    /** A screen about to open (cancelled: it does not). */
    public static class Opening extends ScreenEvent {
        private final Screen current;
        private Screen newScreen;

        public Opening(Screen current, Screen screen) {
            super(screen);
            this.current = current;
            this.newScreen = screen;
        }

        public Screen getCurrentScreen() {
            return current;
        }

        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(Screen s) {
            newScreen = s;
        }
    }

    public static class KeyPressed extends ScreenEvent {
        private final int keyCode, scanCode, modifiers;

        public KeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
            super(screen);
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public int getScanCode() {
            return scanCode;
        }

        public int getModifiers() {
            return modifiers;
        }

        /** Before the screen gets the key (cancelled: it does not). */
        public static class Pre extends KeyPressed {
            public Pre(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen, keyCode, scanCode, modifiers);
            }
        }
    }
}
