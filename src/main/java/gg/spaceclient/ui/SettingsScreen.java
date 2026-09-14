package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.setting.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a list of settings, and a button for each sub-group.
 *
 * Used both for a module's top level and for each of its groups, so a module
 * with four categories of three options each reads as four buttons rather than
 * twelve rows.
 */
public class SettingsScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int PANEL_W = 340;

    private final Screen parent;
    private final String heading;
    private final String subheading;
    private final List<Setting> settings;
    private final List<SettingGroup> groups;

    /**
     * How far down the list we are, in pixels.
     *
     * This used to page instead, and the comment here said why: a wheel
     * handler needs the mouse event signature, and that signature changed in
     * this version. That is no longer a reason - ServersScreen answers it by
     * declaring both shapes and letting the version pick, and has been doing so
     * in the shipped client for a while. Paging through three pages to reach an
     * opacity slider is the kind of thing that reads as unfinished, so it goes.
     */
    private int scroll = 0;
    private int contentHeight = 0;

    /** The settings this screen lays out, kept for the colour labels. */
    private List<Setting> visibleSettings = List.of();

    /** Y positions of colour wheels, so their names can be drawn above them. */
    private final List<int[]> colourRows = new ArrayList<>();

    public SettingsScreen(Screen parent, String heading, String subheading,
                          List<Setting> settings, List<SettingGroup> groups) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.subheading = subheading;
        this.settings = settings;
        this.groups = groups;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        colourRows.clear();
        int left = panelLeft();

        int top = ScreenChrome.TOP;
        int bottom = ScreenChrome.bottomRow(this.height);

        // Laid out as one continuous list and then shifted by the scroll, so
        // the geometry does not have to know anything about pages.
        int y = top - scroll;

        for (SettingGroup group : groups) {
            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W, ROW_H,
                    () -> group.name() + "  >",
                    () -> false,
                    () -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(
                            this, group.name(), group.description(),
                            group.settings(), List.of()))
            ).asAction());
            y += ROW_H + GAP;
        }
        if (!groups.isEmpty()) y += GAP;

        visibleSettings = settings;

        for (Setting setting : settings) {
            if (setting instanceof BooleanSetting b) {
                this.addRenderableWidget(new FlatButton(
                        left, y, PANEL_W, ROW_H,
                        setting::getName, b::get,
                        () -> {
                            b.toggle();
                            SpaceClient.getConfigManager().save();
                        }
                ));
                y += ROW_H + GAP;

            } else if (setting instanceof ModeSetting m) {
                this.addRenderableWidget(new FlatButton(
                        left, y, PANEL_W, ROW_H,
                        () -> setting.getName() + ": " + m.get(),
                        () -> false,
                        () -> {
                            m.cycle();
                            SpaceClient.getConfigManager().save();
                        }
                ));
                y += ROW_H + GAP;

            } else if (setting instanceof IntSetting i) {
                this.addRenderableWidget(new SliderRow(
                        left, y, PANEL_W, ROW_H,
                        setting.getName(), i.get(), i.getMax(),
                        value -> {
                            i.set(value);
                            SpaceClient.getConfigManager().save();
                        }
                ));
                y += ROW_H + GAP;

            } else if (setting instanceof ColorSetting c) {
                colourRows.add(new int[]{y});

                // The wheel and its opacity sit side by side now. The wheel is
                // square and the panel is not, so a wheel on its own left most
                // of the row empty and pushed everything below it down a
                // screen's worth for no gain.
                int wheel = 84;
                this.addRenderableWidget(new ColorWheel(
                        left, y + 14, wheel, c,
                        () -> SpaceClient.getConfigManager().save()
                ));

                int sliderX = left + wheel + GAP * 2;
                int sliderW = PANEL_W - wheel - GAP * 2;
                this.addRenderableWidget(new SliderRow(
                        sliderX, y + 14 + wheel / 2 - ROW_H / 2, sliderW, ROW_H,
                        "Opacity", c.getAlpha(), 255, value -> {
                    c.setComponents(value, c.getRed(), c.getGreen(), c.getBlue());
                    SpaceClient.getConfigManager().save();
                }));

                y += 14 + wheel + GAP;
            }
        }

        contentHeight = (y + scroll) - top;

        this.addRenderableWidget(new FlatButton(
                left, bottom, PANEL_W, 24,
                () -> "Back",
                () -> false,
                this::onClose
        ).asAction());
    }

    /** How much of the list cannot be shown at once. */
    private int maxScroll() {
        int room = ScreenChrome.bottomRow(this.height) - ScreenChrome.TOP - GAP;
        return Math.max(0, contentHeight - room);
    }

    private boolean scrollBy(double amount) {
        int max = maxScroll();
        if (max <= 0) return false;

        int before = scroll;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount) * (ROW_H + GAP)));
        if (scroll != before) this.rebuildWidgets();
        return true;
    }

    // Both shapes, neither annotated: the wheel callback gained a second axis
    // and whichever one this version declares is the one that gets called.
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);

        int left = panelLeft();
        ScreenChrome.header(graphics, this.font, this.width, heading, subheading);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Hitboxes silently lose their per-category options when the world
        // render event is missing, so say that here rather than let the
        // settings look broken.
        if (heading.equalsIgnoreCase("Hitbox") && !gg.spaceclient.render.HitboxRenderer.isAvailable()) {
            graphics.text(this.font,
                    "Custom drawing unavailable - using the game's own hitbox view.",
                    left, this.height - 46, 0xFFFF9AAE, false);
            graphics.text(this.font,
                    "Colours, widths and arrows have no effect in that mode.",
                    left, this.height - 34, 0xFF9A95C9, false);
        }

        // Names above the colour wheels, which draw no label of their own.
        // Positions were recorded during layout, so they already carry the scroll.
        int index = 0;
        for (Setting setting : visibleSettings) {
            if (!(setting instanceof ColorSetting)) continue;
            if (index >= colourRows.size()) break;
            graphics.text(this.font, setting.getName(),
                    left, colourRows.get(index)[0], Theme.TEXT, false);
            index++;
        }

        // Only said when there is something below the fold, and only then.
        if (maxScroll() > 0) {
            String label = scroll < maxScroll() ? "scroll for more" : "end of list";
            graphics.text(this.font, label,
                    left + PANEL_W - this.font.width(label),
                    ScreenChrome.bottomRow(this.height) - 12, Theme.TEXT_DIM, false);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
