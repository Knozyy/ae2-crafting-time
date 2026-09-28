package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.FeatureOptions;
import com.ctux.ae2craftingtime.core.OptionFeature;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native client option widgets. Each navigation action keeps the unsaved working copy. */
public final class OptionsScreen extends OptionsBaseScreen {
    private static final List<OptionFeature.Group> CLIENT_GROUPS = List.of(
            OptionFeature.Group.DISPLAYS, OptionFeature.Group.WARNINGS,
            OptionFeature.Group.APPEARANCE, OptionFeature.Group.CONTROLS);

    private final Screen parent;
    private final OptionsSession session;
    private final ClientConfig draft;
    private final OptionFeature.Group group;
    private final int page;
    private String error;
    private boolean reloadRequired;
    private final List<EditBox> inputs = new ArrayList<>();
    private List<String> resizeValues = List.of();
    private Button doneButton;
    private Button cancelButton;

    @Override
    public void tick() {
        super.tick();
        var result = session.poll();
        if (result == OptionsSession.SaveResult.SAVED) onClose();
        else if (result == OptionsSession.SaveResult.REJECTED) {
            doneButton.active = true;
            cancelButton.active = true;
            reloadRequired = session.source() != null && ClientServerOptions.snapshot() != null
                    && ClientServerOptions.snapshot().revision() != session.source().revision();
            doneButton.setMessage(Component.translatable(reloadRequired
                    ? "config.ae2craftingtime.reload" : "config.ae2craftingtime.save_failed"));
        }
    }

    public OptionsScreen(Screen parent) {
        this(parent, new OptionsSession(), OptionFeature.Group.DISPLAYS, 0);
    }

    OptionsScreen(Screen parent, OptionsSession session) {
        this(parent, session, OptionFeature.Group.DISPLAYS, 0);
    }

    private OptionsScreen(Screen parent, OptionsSession session, OptionFeature.Group group, int page) {
        super(Component.translatable("config.ae2craftingtime.title"));
        this.parent = parent;
        this.session = session;
        this.draft = session.client();
        this.group = group;
        this.page = page;
    }

    @Override
    protected void init() {
        resizeValues = inputs.stream().map(EditBox::getValue).toList();
        inputs.clear();
        int left = Math.max(4, (width - 430) / 2);
        int right = Math.min(width - 4, left + 430);
        int sidebarWidth = 104;
        int rows = ClientConfig.appearanceRowsPerPage(height);
        addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.client"), button -> {})
                .bounds(left, 30, 100, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.server"), button -> {
            if (commitInputs()) Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session));
        }).bounds(left + 105, 30, 100, 20).build());

        for (int i = 0; i < CLIENT_GROUPS.size(); i++) {
            var nextGroup = CLIENT_GROUPS.get(i);
            var button = addRenderableWidget(Button.builder(groupLabel(nextGroup), pressed ->
                    { if (commitInputs()) Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, nextGroup, 0)); })
                    .bounds(left, 61 + i * 25, sidebarWidth, 20).build());
            button.active = nextGroup != group;
        }

        var features = Arrays.stream(OptionFeature.values())
                .filter(feature -> feature.owner() == OptionFeature.Owner.CLIENT && feature.group() == group).toList();
        var defaults = new FeatureOptions(OptionFeature.Owner.CLIENT);
        int totalRows = group == OptionFeature.Group.APPEARANCE ? ClientConfig.appearanceRowCount(features.size())
                : features.size() + (group == OptionFeature.Group.CONTROLS ? 2 : 0);
        for (int i = page * rows; i < Math.min(totalRows, (page + 1) * rows); i++) {
            int y = 61 + (i - page * rows) * 28;
            if (group == OptionFeature.Group.APPEARANCE && i >= features.size()) {
                addAppearanceRow(ClientConfig.appearanceInputIndex(i, features.size(), 0),
                        left + sidebarWidth + 8, y, right - left - sidebarWidth - 8);
                continue;
            }
            if (i >= features.size()) {
                addSortRow(i - features.size(), left + sidebarWidth + 8, y, right - left - sidebarWidth - 8);
                continue;
            }
            var feature = features.get(i);
            var enabled = draft.features().enabled(feature);
            var label = Component.translatable("config.ae2craftingtime." + feature.key())
                    .append(": ").append(Component.translatable(enabled ? "options.on" : "options.off"));
            var toggle = addRenderableWidget(Button.builder(label, pressed -> {
                if (!commitInputs()) return;
                draft.features().setEnabled(feature, !draft.features().enabled(feature));
                Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, page));
            }).bounds(left + sidebarWidth + 8, y,
                    right - left - sidebarWidth - 8, 23).build());
            toggle.setTooltip(Tooltip.create(Component.translatable(
                    "config.ae2craftingtime.client_help",
                    Component.translatable("config.ae2craftingtime." + feature.key()),
                    Component.translatable(defaults.enabled(feature) ? "options.on" : "options.off"))));
        }

        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), pressed ->
                    { if (commitInputs()) Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, page - 1)); })
                    .bounds(right - 56, height - 84, 24, 20).build());
        }
        if ((page + 1) * rows < totalRows) {
            addRenderableWidget(Button.builder(Component.literal(">"), pressed ->
                    { if (commitInputs()) Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, page + 1)); })
                    .bounds(right - 28, height - 84, 24, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.reset_group"), pressed -> {
            for (var feature : features) draft.features().setEnabled(feature, defaults.enabled(feature));
            if (group == OptionFeature.Group.APPEARANCE) {
                for (var color : ClientConfig.Color.values()) draft.setColor(color, color.defaultRgb());
                draft.setBadgeOpacity(176);
            } else {
                if (group == OptionFeature.Group.CONTROLS) {
                    draft.setPlanSort(2);
                    draft.setStatusSort(2);
                }
            }
            Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, 0));
        }).bounds(left, height - 54, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.reset_all"), pressed -> {
            session.resetAll();
            Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, 0));
        }).bounds(left + 104, height - 54, 84, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), pressed -> onClose())
                .bounds(right - 158, height - 54, 76, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> save())
                .bounds(right - 78, height - 54, 76, 20).build());
        if (session.isSaving()) {
            doneButton.setMessage(Component.translatable("config.ae2craftingtime.saving"));
            lockWidgets();
        } else if (reloadRequired) doneButton.setMessage(Component.translatable("config.ae2craftingtime.reload"));
    }

    private void addAppearanceRow(int index, int x, int y, int width) {
        if (index < ClientConfig.appearanceColors().size()) {
            var color = ClientConfig.appearanceColors().get(index);
            var label = Component.translatable("config.ae2craftingtime.color." + color.name().toLowerCase(Locale.ROOT));
            addRenderableWidget(Button.builder(label, button -> {})
                    .bounds(x, y, width - 84, 23).build()).active = false;
            var input = new EditBox(font, x + width - 80, y, 80, 23, label);
            input.setMaxLength(7);
            input.setValue(resizeValues.size() > inputs.size() ? resizeValues.get(inputs.size())
                    : String.format(Locale.ROOT, "#%06X", draft.color(color)));
            input.setTooltip(Tooltip.create(Component.translatable("config.ae2craftingtime.color_help",
                    String.format(Locale.ROOT, "#%06X", color.defaultRgb()))));
            input.setResponder(value -> {
                if (value.matches("#[0-9a-fA-F]{6}"))
                    draft.setColor(color, Integer.parseInt(value.substring(1), 16));
            });
            addRenderableWidget(input);
            inputs.add(input);
        } else {
            var label = Component.translatable("config.ae2craftingtime.badgeOpacity");
            addRenderableWidget(Button.builder(label, button -> {})
                    .bounds(x, y, width - 84, 23).build()).active = false;
            var input = new EditBox(font, x + width - 80, y, 80, 23, label);
            input.setMaxLength(3);
            input.setValue(resizeValues.size() > inputs.size() ? resizeValues.get(inputs.size())
                    : Integer.toString(draft.badgeOpacity()));
            input.setTooltip(Tooltip.create(Component.translatable("config.ae2craftingtime.opacity_help")));
            input.setResponder(value -> {
                try { draft.setBadgeOpacity(Integer.parseInt(value)); }
                catch (IllegalArgumentException ignored) { }
            });
            addRenderableWidget(input);
            inputs.add(input);
        }
    }

    private void addSortRow(int index, int x, int y, int width) {
        int value = index == 0 ? draft.planSort() : draft.statusSort();
        var label = Component.translatable(index == 0 ? "config.ae2craftingtime.planSort"
                : "config.ae2craftingtime.statusSort").append(": ").append(TtcText.sortMode(value));
        var sort = addRenderableWidget(Button.builder(label, button -> {
            if (index == 0) draft.setPlanSort((draft.planSort() + 1) % 3);
            else draft.setStatusSort((draft.statusSort() + 1) % 3);
            Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, page));
        }).bounds(x, y, width, 23).build());
        sort.setTooltip(Tooltip.create(Component.translatable("config.ae2craftingtime.sort_help")));
    }

    private void save() {
        if (reloadRequired) {
            session.reload();
            Minecraft.getInstance().setScreen(new OptionsScreen(parent, session, group, page));
            return;
        }
        if (!commitInputs()) return;
        try {
            switch (session.save()) {
                case SAVED -> onClose();
                case WAITING -> {
                    doneButton.setMessage(Component.translatable("config.ae2craftingtime.saving"));
                    lockWidgets();
                }
                case STALE -> {
                    doneButton.setMessage(Component.translatable("config.ae2craftingtime.reload"));
                    reloadRequired = true;
                }
                case REJECTED -> throw new IllegalStateException("Unexpected save result");
            }
        } catch (IOException exception) {
            error = exception.getMessage();
            doneButton.setMessage(Component.translatable("config.ae2craftingtime.save_failed"));
        }
    }

    private boolean commitInputs() {
        if (group != OptionFeature.Group.APPEARANCE) return true;
        try {
            int featureRows = (int) Arrays.stream(OptionFeature.values())
                    .filter(feature -> feature.owner() == OptionFeature.Owner.CLIENT && feature.group() == group).count();
            int firstVisibleRow = page * ClientConfig.appearanceRowsPerPage(height);
            for (int i = 0; i < inputs.size(); i++) {
                int index = ClientConfig.appearanceInputIndex(firstVisibleRow, featureRows, i);
                var value = inputs.get(i).getValue();
                if (index < ClientConfig.appearanceColors().size()) {
                    if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid RGB");
                    draft.setColor(ClientConfig.appearanceColors().get(index), Integer.parseInt(value.substring(1), 16));
                } else draft.setBadgeOpacity(Integer.parseInt(value));
            }
            return true;
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage();
            doneButton.setMessage(Component.translatable("config.ae2craftingtime.save_failed"));
            return false;
        }
    }

    private void lockWidgets() {
        children().forEach(child -> {
            if (child instanceof AbstractWidget widget) widget.active = false;
            if (child instanceof EditBox input) input.setEditable(false);
        });
    }

    @Override
    public void onClose() {
        if (session.isSaving()) return;
        Minecraft.getInstance().setScreen(parent);
    }


    private static Component groupLabel(OptionFeature.Group group) {
        return Component.translatable("config.ae2craftingtime.group." + group.name().toLowerCase(java.util.Locale.ROOT));
    }
}
