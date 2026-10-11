package gg.spaceclient.access;

/**
 * Carries the hit colour decision on an entity's render state.
 *
 * The decision needs the entity - who it is, whether it is in reach - but the
 * drawing step only sees the render state, which is rebuilt every frame and
 * holds nothing that identifies the entity. So it is made during extraction
 * and handed across on the state, the same way the item id is.
 *
 * Lives outside the mixin package for the reason ItemIdHolder spells out.
 */
public interface TintHolder {
    /** Nothing to colour: the game draws the entity as it always would. */
    int NONE = 0;
    /** The entity was just hit. */
    int HIT = 1;
    /** The entity is within reach. */
    int REACH = 2;

    int spaceclient$tint();
    void spaceclient$setTint(int tint);
}
