package com.veritypal.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.veritypal.config.PalClientConfig;
import com.veritypal.config.PalConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mods > Verity Pal > Config: every setting of Verity Pal on one page (the story switch first), each with its
 * explanation on hover, saved right away. Always in English, whatever language the game is in. A button opens Verity JE's own settings (her AI, voice, microphone).
 */
public class PalConfigScreen extends Screen {
    final Screen parent;
    Rows rows;
    final List<Runnable> onClose = new ArrayList<>();

    /** Settings that are one of a few words. */
    static final Map<String, List<String>> CHOICES = Map.of(
            "language", List.of("auto", "thai", "english"),
            "thaiVoice", List.of("auto", "always", "off"),
            "geminiEars", List.of("auto", "on", "off"));

    public PalConfigScreen(Screen parent) {
        super(Component.translatable("veritypal.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        onClose.clear();
        rows = new Rows(minecraft, width, height, 28, height - 34, 24);
        addSpec(PalConfig.SPEC);
        addSpec(PalClientConfig.SPEC);
        addWidget(rows);
        int bw = 150;
        boolean verityScreen = ModList.get().getModContainerById("verity").flatMap(c -> c.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)).isPresent();
        if (verityScreen) {
            addRenderableWidget(Button.builder(Component.translatable("veritypal.config.verity_settings"), b -> {
                saveAll();
                ModList.get().getModContainerById("verity").flatMap(c -> c.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class))
                        .ifPresent(f -> minecraft.setScreen(f.screenFunction().apply(minecraft, this)));
            }).bounds(width / 2 - bw - 4, height - 28, bw, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(verityScreen ? width / 2 + 4 : width / 2 - bw / 2, height - 28, bw, 20).build());
    }

    void addSpec(ForgeConfigSpec spec) {
        if (!spec.isLoaded()) {
            rows.add(new Header(Component.translatable("veritypal.config.not_loaded")));
            return;
        }
        walk(spec, spec.getValues(), new ArrayList<>());
    }

    void walk(ForgeConfigSpec spec, UnmodifiableConfig cfg, List<String> path) {
        for (Map.Entry<String, Object> e : cfg.valueMap().entrySet()) {
            List<String> p = new ArrayList<>(path);
            p.add(e.getKey());
            Object v = e.getValue();
            if (v instanceof UnmodifiableConfig sub) {
                rows.add(new Header(label("section." + e.getKey(), e.getKey())));
                walk(spec, sub, p);
            } else if (v instanceof ForgeConfigSpec.ConfigValue<?> cv) {
                ForgeConfigSpec.ValueSpec vs = spec.getSpec().get(p);
                rows.add(new Setting(cv, vs, e.getKey()));
            }
        }
    }

    static Component label(String key, String fallback) {
        String k = "veritypal.config." + key;
        return I18n.exists(k) ? Component.translatable(k) : Component.literal(fallback);
    }

    static Tooltip tip(String key, ForgeConfigSpec.ValueSpec vs) {
        String k = "veritypal.config." + key + ".tooltip";
        if (I18n.exists(k)) return Tooltip.create(Component.translatable(k));
        String c = vs == null || vs.getComment() == null ? "" : vs.getComment().replaceAll("\\s*\\n\\s*", " ").replaceAll("Range: .*$", "").trim();
        return Tooltip.create(Component.literal(c));
    }

    void saveAll() {
        onClose.forEach(Runnable::run);
        if (PalConfig.SPEC.isLoaded()) PalConfig.SPEC.save();
        if (PalClientConfig.SPEC.isLoaded()) PalClientConfig.SPEC.save();
    }

    @Override
    public void onClose() {
        saveAll();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rows.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFE24A);
        super.render(g, mx, my, pt);
    }

    // ------------------------------------------------------------------ the list
    class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft mc, int w, int h, int top, int bottom, int itemH) {
            super(mc, w, h, top, bottom, itemH);
        }

        void add(Row r) {
            addEntry(r);
        }

        @Override
        public int getRowWidth() {
            return Math.min(400, width - 20);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }
    }

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    class Header extends Row {
        final Component text;

        Header(Component text) {
            this.text = text.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        @Override
        public void render(GuiGraphics g, int i, int top, int left, int w, int h, int mx, int my, boolean hover, float pt) {
            g.drawString(font, text, left, top + 8, 0xFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    class Setting extends Row {
        final Component name;
        final AbstractWidget widget;

        @SuppressWarnings("unchecked")
        Setting(ForgeConfigSpec.ConfigValue<?> cv, ForgeConfigSpec.ValueSpec vs, String key) {
            this.name = label(key, key);
            Object val = cv.get();
            int w = 160;
            if (val instanceof Boolean b) {
                ForgeConfigSpec.ConfigValue<Boolean> c = (ForgeConfigSpec.ConfigValue<Boolean>) cv;
                // its own ON / OFF: the game's would follow the game's language
                widget = CycleButton.<Boolean>builder(v -> Component.literal(v ? "ON" : "OFF").withStyle(v ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                        .withValues(true, false).withInitialValue(b).displayOnlyValue().create(0, 0, w, 20, Component.empty(), (btn, v) -> c.set(v));
            } else if (CHOICES.containsKey(key) && val instanceof String s) {
                ForgeConfigSpec.ConfigValue<String> c = (ForgeConfigSpec.ConfigValue<String>) cv;
                List<String> opts = CHOICES.get(key);
                widget = CycleButton.<String>builder(o -> label("choice." + o, o)).withValues(opts).withInitialValue(opts.contains(s) ? s : opts.get(0))
                        .displayOnlyValue().create(0, 0, w, 20, Component.empty(), (btn, v) -> c.set(v));
            } else if ((val instanceof Integer || val instanceof Double) && vs != null && vs.getRange() != null) {
                ForgeConfigSpec.Range<?> r = vs.getRange();
                double min = ((Number) r.getMin()).doubleValue(), max = ((Number) r.getMax()).doubleValue();
                boolean whole = val instanceof Integer;
                widget = new Slider(w, min, max, ((Number) val).doubleValue(), whole, v -> {
                    if (whole) ((ForgeConfigSpec.ConfigValue<Integer>) cv).set((int) Math.round(v));
                    else ((ForgeConfigSpec.ConfigValue<Double>) cv).set(Math.round(v * 100) / 100.0);
                });
            } else if (val instanceof List<?> list) {
                ForgeConfigSpec.ConfigValue<List<? extends String>> c = (ForgeConfigSpec.ConfigValue<List<? extends String>>) cv;
                EditBox box = new EditBox(font, 0, 0, w, 20, name);
                box.setMaxLength(2000);
                box.setValue(list.stream().map(String::valueOf).collect(Collectors.joining(", ")));
                onClose.add(() -> c.set(Arrays.stream(box.getValue().split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList()));
                widget = box;
            } else {
                ForgeConfigSpec.ConfigValue<String> c = (ForgeConfigSpec.ConfigValue<String>) cv;
                EditBox box = new EditBox(font, 0, 0, w, 20, name);
                box.setMaxLength(500);
                box.setValue(String.valueOf(val));
                box.setResponder(c::set);
                widget = box;
            }
            widget.setTooltip(tip(key, vs));
        }

        @Override
        public void render(GuiGraphics g, int i, int top, int left, int w, int h, int mx, int my, boolean hover, float pt) {
            g.drawString(font, name, left, top + 6, 0xFFFFFF);
            widget.setX(left + w - widget.getWidth());
            widget.setY(top);
            widget.render(g, mx, my, pt);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }
    }

    static class Slider extends AbstractSliderButton {
        final double min, max;
        final boolean whole;
        final java.util.function.DoubleConsumer set;

        Slider(int w, double min, double max, double v, boolean whole, java.util.function.DoubleConsumer set) {
            super(0, 0, w, 20, Component.empty(), (v - min) / Math.max(1e-9, max - min));
            this.min = min;
            this.max = max;
            this.whole = whole;
            this.set = set;
            updateMessage();
        }

        double now() {
            double v = min + value * (max - min);
            return whole ? Math.round(v) : Math.round(v * 100) / 100.0;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(whole ? String.valueOf((long) now()) : String.format("%.2f", now())));
        }

        @Override
        protected void applyValue() {
            set.accept(now());
        }
    }
}
