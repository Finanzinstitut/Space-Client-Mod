package gg.spaceclient.module;

import gg.spaceclient.net.Badges.Rank;

/**
 * Who may switch a module on.
 *
 * Read from the same list that decides the mark in front of a name, so giving
 * somebody a rank on the manage page is also what unlocks their features: there
 * is no second list to keep in step with the first.
 *
 * This is a gate in the client, and a client can be changed by whoever runs
 * it. It keeps features where they belong for everybody playing the build as
 * shipped; it is not a lock against somebody rebuilding the jar.
 */
public enum Access {

    /** Every Space Client player. */
    EVERYONE("", ""),

    /**
     * Any rank above the ordinary one. VIP is the rank meant to be handed out;
     * the team ranks above it would be odd to lock out of what VIPs get, and
     * testers need to reach a feature to test it.
     */
    VIP("Requires VIP", "VIP"),

    /** The people building the client. For features that are still being tuned. */
    STAFF("Dev & Owner only", "Dev");

    private final String reason;
    private final String tag;

    Access(String reason, String tag) {
        this.reason = reason;
        this.tag = tag;
    }

    /** The line shown in place of the description while locked. */
    public String reason() { return reason; }

    /** The short label beside the name. */
    public String tag() { return tag; }

    public boolean allows(Rank rank) {
        return switch (this) {
            case EVERYONE -> true;
            case VIP -> rank != null && rank != Rank.STANDARD;
            case STAFF -> rank == Rank.DEV || rank == Rank.OWNER;
        };
    }
}
