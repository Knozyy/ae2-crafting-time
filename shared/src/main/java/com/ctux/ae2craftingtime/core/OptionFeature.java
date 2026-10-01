package com.ctux.ae2craftingtime.core;

/** The stable setting keys and UI groups for independent feature switches. */
public enum OptionFeature {
    PLAN_ROWS(Owner.CLIENT, Group.DISPLAYS, "planRows"),
    PLAN_TOTAL(Owner.CLIENT, Group.DISPLAYS, "planTotal"),
    STATUS_ROWS(Owner.CLIENT, Group.DISPLAYS, "statusRows"),
    COMPACT_STATUS_AMOUNTS(Owner.CLIENT, Group.DISPLAYS, "compactStatusAmounts"),
    STATUS_TOTAL(Owner.CLIENT, Group.DISPLAYS, "statusTotal"),
    CPU_CARD_TOTAL(Owner.CLIENT, Group.DISPLAYS, "cpuCardTotal"),
    CRAFTING_TREE(Owner.CLIENT, Group.DISPLAYS, "showInTree"),
    ME_REQUESTER(Owner.CLIENT, Group.DISPLAYS, "meRequester"),
    TTC_COLORS(Owner.CLIENT, Group.DISPLAYS, "ttcColors"),
    ACCURACY_DETAILS(Owner.CLIENT, Group.DISPLAYS, "accuracyDetails"),
    DETAILED_TOOLTIPS(Owner.CLIENT, Group.DISPLAYS, "detailedTooltips"),
    CONTROL_HINTS(Owner.CLIENT, Group.DISPLAYS, "controlHints"),
    WAITING_STATUS(Owner.CLIENT, Group.WARNINGS, "waitingStatus"),
    COLLECTING_STATUS(Owner.CLIENT, Group.WARNINGS, "collectingStatus"),
    DELAYED_STATUS(Owner.CLIENT, Group.WARNINGS, "delayedStatus"),
    CHANCE_OUTPUT_STATUS(Owner.CLIENT, Group.WARNINGS, "chanceOutputStatus"),
    RECURRENT_STATUS(Owner.CLIENT, Group.WARNINGS, "recurrentStatus"),
    NO_PROVIDER_STATUS(Owner.CLIENT, Group.WARNINGS, "noProviderStatus"),
    NO_POWER_STATUS(Owner.CLIENT, Group.WARNINGS, "noPowerStatus"),
    NO_SPACE_STATUS(Owner.CLIENT, Group.WARNINGS, "noSpaceStatus"),
    NO_CHANNEL_STATUS(Owner.CLIENT, Group.WARNINGS, "noChannelStatus"),
    NO_TARGET_STATUS(Owner.CLIENT, Group.WARNINGS, "noTargetStatus"),
    INPUT_BLOCKED_STATUS(Owner.CLIENT, Group.WARNINGS, "inputBlockedStatus"),
    RECEIVE_CRAFT_WARNINGS(Owner.CLIENT, Group.WARNINGS, "receiveCraftWarnings"),
    TEXT_SHADOW(Owner.CLIENT, Group.APPEARANCE, "textShadow"),
    SHOW_EMOJI(Owner.CLIENT, Group.APPEARANCE, "showEmoji"),
    BADGE_BACKGROUND(Owner.CLIENT, Group.APPEARANCE, "badgeBackground"),
    PLAN_SORT_CONTROL(Owner.CLIENT, Group.CONTROLS, "planSortControl"),
    STATUS_SORT_CONTROL(Owner.CLIENT, Group.CONTROLS, "statusSortControl"),
    CPU_SORT_CONTROL(Owner.CLIENT, Group.CONTROLS, "cpuSortControl"),
    TTC_DETAILS_CLICK(Owner.CLIENT, Group.CONTROLS, "ttcDetailsClick"),
    RESET_HISTORY_CLICK(Owner.CLIENT, Group.CONTROLS, "resetHistoryClick"),
    PROVIDER_LOCATE_CLICK(Owner.CLIENT, Group.CONTROLS, "providerLocateClick"),
    PROFILING(Owner.SERVER, Group.GENERAL, "enabled"),
    SAVE_HISTORY(Owner.SERVER, Group.GENERAL, "saveHistory"),
    ACCURACY_RECORDING(Owner.SERVER, Group.DIAGNOSTICS, "accuracyRecording"),
    WAITING_TRACKING(Owner.SERVER, Group.DIAGNOSTICS, "waitingTracking"),
    DELAYED_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "delayedDetection"),
    RECURRENT_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "recurrentDetection"),
    NO_PROVIDER_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "noProviderDetection"),
    NO_POWER_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "noPowerDetection"),
    NO_SPACE_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "noSpaceDetection"),
    NO_CHANNEL_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "noChannelDetection"),
    NO_TARGET_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "noTargetDetection"),
    INPUT_BLOCKED_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "inputBlockedDetection"),
    NOTIFY_ON_DELAYED(Owner.SERVER, Group.NOTIFICATIONS, "notifyOnDelayed"),
    SHOW_CHAT_MESSAGES(Owner.SERVER, Group.NOTIFICATIONS, "showChatMessages"),
    ADVANCED_AE(Owner.SERVER, Group.INTEGRATIONS, "advancedAe"),
    NEO_ECO(Owner.SERVER, Group.INTEGRATIONS, "neoEco"),
    AE2_LIGHTNING_TECH(Owner.SERVER, Group.INTEGRATIONS, "ae2LightningTech"),
    APPLIED_MEKANISTICS(Owner.SERVER, Group.INTEGRATIONS, "appliedMekanistics"),
    CHANCE_OUTPUT_DETECTION(Owner.SERVER, Group.DIAGNOSTICS, "chanceOutputDetection"),
    CRAFTING_SUSPENSION(Owner.SERVER, Group.GENERAL, "craftingSuspension");

    public enum Owner { CLIENT, SERVER }
    public enum Group { DISPLAYS, WARNINGS, APPEARANCE, CONTROLS, GENERAL, DIAGNOSTICS, NOTIFICATIONS, INTEGRATIONS, ADVANCED }

    private final Owner owner;
    private final Group group;
    private final String key;

    OptionFeature(Owner owner, Group group, String key) {
        this.owner = owner;
        this.group = group;
        this.key = key;
    }

    public Owner owner() { return owner; }
    public Group group() { return group; }
    public String key() { return key; }

    public static OptionFeature statusFor(CraftingBlockReason reason) {
        return switch (reason) {
            case NO_PROVIDER -> NO_PROVIDER_STATUS;
            case NO_POWER -> NO_POWER_STATUS;
            case NO_TARGET -> NO_TARGET_STATUS;
            case NO_CHANNEL -> NO_CHANNEL_STATUS;
            case INPUT_BLOCKED, LOCKED -> INPUT_BLOCKED_STATUS;
        };
    }
}
