package gg.spaceclient.module;

public enum Category {
    HUD("HUD"),
    VISUAL("Visual"),
    COMBAT("Combat"),
    UTILITY("Utility");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
