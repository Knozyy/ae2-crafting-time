package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.FeatureOptions;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.ServerConfig;
import com.ctux.ae2craftingtime.core.ServerOptionsWire;
import com.ctux.ae2craftingtime.mc1201.net.ServerOptionsUpdateC2S;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-effective options; only an operator can submit edits. */
public final class ServerOptionsScreen extends OptionsBaseScreen {
    private static final List<OptionFeature.Group> GROUPS = List.of(OptionFeature.Group.GENERAL,
            OptionFeature.Group.DIAGNOSTICS, OptionFeature.Group.NOTIFICATIONS,
            OptionFeature.Group.INTEGRATIONS, OptionFeature.Group.ADVANCED);
    private final Screen parent;
    private final OptionsSession session;
    private final ServerOptionsWire.Snapshot source;
    private final ServerConfig draft;
    private final OptionFeature.Group group;
    private final int page;
    private final List<EditBox> inputs = new ArrayList<>();
    private List<String> resizeValues = List.of();
    private Button doneButton;
    private Button cancelButton;
    private boolean reloadRequired;

    @Override
    public void tick() {
        super.tick();
        if (source == null && ClientServerOptions.snapshot() != null) {
            session.refreshIfMissing();
            Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session));
        }
        var result = session.poll();
        if (result == OptionsSession.SaveResult.SAVED) onClose();
        else if (result == OptionsSession.SaveResult.REJECTED) {
            doneButton.active = true;
            if (cancelButton != null) cancelButton.active = true;
            reloadRequired = source != null && ClientServerOptions.snapshot() != null
                    && ClientServerOptions.snapshot().revision() != source.revision();
            doneButton.setMessage(Component.translatable(reloadRequired
                    ? "config.ae2craftingtime.reload" : "config.ae2craftingtime.save_failed"));
        }
    }

    public ServerOptionsScreen(Screen parent) {
        this(parent, new OptionsSession());
    }

    ServerOptionsScreen(Screen parent, OptionsSession session) {
        this(parent, session, OptionFeature.Group.GENERAL, 0);
    }

    private ServerOptionsScreen(Screen parent, OptionsSession session, OptionFeature.Group group, int page) {
        super(Component.translatable("config.ae2craftingtime.title"));
        this.parent = parent;
        this.session = session;
        session.refreshIfMissing();
        this.source = session.source();
        this.draft = session.server();
        this.group = group;
        this.page = page;
    }

    @Override
    protected void init() {
        resizeValues = inputs.stream().map(EditBox::getValue).toList();
        inputs.clear();
        int left = Math.max(4, (width - 430) / 2);
        int right = Math.min(width - 4, left + 430);
        int rows = Math.max(2, (height - 145) / 28);
        addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.client"), button ->
                { if (commitInputs()) Minecraft.getInstance().setScreen(new OptionsScreen(parent, session)); })
                .bounds(left, 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.server"), button -> {})
                .bounds(left + 105, 30, 100, 20).build()).active = false;
        if (source == null) {
            addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.server_loading"), button -> {})
                    .bounds(left + 112, 65, 300, 23).build()).active = false;
            doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                    .bounds(right - 78, height - 54, 76, 20).build());
            return;
        }
        for (int i = 0; i < GROUPS.size(); i++) {
            var next = GROUPS.get(i);
            var button = addRenderableWidget(Button.builder(groupLabel(next), pressed -> {
                if (commitInputs()) Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session, next, 0));
            }).bounds(left, 61 + i * 25, 104, 20).build());
            button.active = next != group;
        }
        var features = Arrays.stream(OptionFeature.values())
                .filter(value -> value.owner() == OptionFeature.Owner.SERVER && value.group() == group).toList();
        var tooltipDefaults = new FeatureOptions(OptionFeature.Owner.SERVER);
        int totalRows = features.size() + (group == OptionFeature.Group.ADVANCED ? 4 : 0);
        for (int i = page * rows; i < Math.min(totalRows, (page + 1) * rows); i++) {
            int x = left + 112;
            int y = 61 + (i - page * rows) * 28;
            int rowWidth = right - x;
            if (i >= features.size()) {
                addNumericRow(i - features.size(), x, y, rowWidth);
                continue;
            }
            var feature = features.get(i);
            boolean enabled = draft.features().enabled(feature);
            boolean available = integrationAvailable(feature);
            var label = Component.translatable("config.ae2craftingtime." + feature.key())
                    .append(": ").append(Component.translatable(enabled ? "options.on" : "options.off"));
            if (!available) label.append(" (").append(Component.translatable("config.ae2craftingtime.unavailable"))
                    .append(")");
            var toggle = addRenderableWidget(Button.builder(label, pressed -> {
                draft.features().setEnabled(feature, !draft.features().enabled(feature));
                Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session, group, page));
            }).bounds(x, y, rowWidth, 23).build());
            toggle.active = source.editable() && available;
            toggle.setTooltip(Tooltip.create(Component.translatable(
                    "config.ae2craftingtime.server_help",
                    Component.translatable("config.ae2craftingtime." + feature.key()),
                    Component.translatable(tooltipDefaults.enabled(feature) ? "options.on" : "options.off"))));
        }
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("<"), pressed -> {
            if (commitInputs()) Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session, group, page - 1));
        }).bounds(right - 56, height - 84, 24, 20).build());
        if ((page + 1) * rows < totalRows) addRenderableWidget(Button.builder(Component.literal(">"), pressed -> {
            if (commitInputs()) Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session, group, page + 1));
        }).bounds(right - 28, height - 84, 24, 20).build());
        if (!source.editable()) addRenderableWidget(Button.builder(
                Component.translatable("config.ae2craftingtime.read_only"), button -> {})
                .bounds(left + 112, height - 104, right - left - 112, 20).build()).active = false;
        var resetGroup = addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.reset_group"), pressed -> {
            var defaults = new ServerConfig();
            for (var feature : features) draft.features().setEnabled(feature, defaults.features().enabled(feature));
            if (group == OptionFeature.Group.ADVANCED) resetNumeric();
            Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session, group, 0));
        }).bounds(left, height - 54, 100, 20).build());
        resetGroup.active = source.editable();
        var resetAll = addRenderableWidget(Button.builder(Component.translatable("config.ae2craftingtime.reset_all"), pressed -> {
            session.resetAll();
            Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session, group, 0));
        }).bounds(left + 104, height - 54, 84, 20).build());
        resetAll.active = source.editable();
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), pressed -> onClose())
                .bounds(right - 158, height - 54, 76, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> save())
                .bounds(right - 78, height - 54, 76, 20).build());
        if (session.isSaving()) {
            doneButton.setMessage(Component.translatable("config.ae2craftingtime.saving"));
            lockWidgets();
        } else if (reloadRequired) doneButton.setMessage(Component.translatable("config.ae2craftingtime.reload"));
    }

    private void addNumericRow(int index, int x, int y, int width) {
        String[] keys = {"maxSamples", "outlierMultiplier", "minimumNoProgressSeconds", "typicalDurationMultiplier"};
        String[] values = {Integer.toString(draft.maxSamples()), Double.toString(draft.outlierMultiplier()),
                Integer.toString(draft.minimumNoProgressSeconds()), Double.toString(draft.typicalDurationMultiplier())};
        var label = Component.translatable("config.ae2craftingtime." + keys[index]);
        addRenderableWidget(Button.builder(label, button -> {}).bounds(x, y, width - 84, 23).build()).active = false;
        var input = new EditBox(font, x + width - 80, y, 80, 23, label);
        input.setMaxLength(16);
        input.setValue(resizeValues.size() > inputs.size() ? resizeValues.get(inputs.size()) : values[index]);
        String[] defaults = {"10", "4.0", "10", "2.0"};
        String[] ranges = {"1-100", "1-1000", "1-3600", "1-1000"};
        input.setTooltip(Tooltip.create(Component.translatable("config.ae2craftingtime.numeric_help",
                defaults[index], ranges[index])));
        input.setEditable(source.editable());
        addRenderableWidget(input);
        inputs.add(input);
    }

    private boolean commitInputs() {
        if (source == null || !source.editable() || group != OptionFeature.Group.ADVANCED) return true;
        try {
            int start = page * Math.max(2, (height - 145) / 28);
            for (int i = 0; i < inputs.size(); i++) {
                String value = inputs.get(i).getValue();
                switch (start + i) {
                    case 0 -> draft.setMaxSamples(Integer.parseInt(value));
                    case 1 -> draft.setOutlierMultiplier(Double.parseDouble(value));
                    case 2 -> draft.setMinimumNoProgressSeconds(Integer.parseInt(value));
                    case 3 -> draft.setTypicalDurationMultiplier(Double.parseDouble(value));
                    default -> throw new IllegalArgumentException("Unknown option");
                }
            }
            return true;
        } catch (IllegalArgumentException error) {
            doneButton.setMessage(Component.translatable("config.ae2craftingtime.save_failed"));
            return false;
        }
    }

    private void resetNumeric() {
        var defaults = new ServerConfig();
        draft.setMaxSamples(defaults.maxSamples());
        draft.setOutlierMultiplier(defaults.outlierMultiplier());
        draft.setMinimumNoProgressSeconds(defaults.minimumNoProgressSeconds());
        draft.setTypicalDurationMultiplier(defaults.typicalDurationMultiplier());
    }

    private void save() {
        if (reloadRequired) {
            session.reload();
            Minecraft.getInstance().setScreen(new ServerOptionsScreen(parent, session));
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
        } catch (java.io.IOException error) {
            doneButton.setMessage(Component.translatable("config.ae2craftingtime.save_failed"));
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
        if (!session.isSaving()) Minecraft.getInstance().setScreen(parent);
    }

    private static Component groupLabel(OptionFeature.Group group) {
        return Component.translatable("config.ae2craftingtime.group." + group.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean integrationAvailable(OptionFeature feature) {
        String id = switch (feature) {
            case ADVANCED_AE -> "advanced_ae";
            case NEO_ECO -> "neoecoae";
            case AE2_LIGHTNING_TECH -> "ae2lt";
            case APPLIED_MEKANISTICS -> "appmek";
            default -> null;
        };
        return id == null || IntegrationLog.available(id);
    }
}
