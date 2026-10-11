package gg.spaceclient.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How large each item draws, in three places.
 *
 * The point of this is the pile. When someone dies in a fight their inventory
 * lands as thirty item entities on top of each other, and the one that decides
 * the next thirty seconds - a totem - looks exactly like the thirty-nine cobble
 * next to it. Making one item type larger on the ground turns finding it from
 * reading a heap into seeing a shape.
 *
 * Keyed on the item's description id rather than a registry lookup. That id is
 * a plain string the stack already carries, which means this class never has to
 * touch the item registry and never has to be updated when that moves.
 */
public final class ItemSizes {

    /** A size of 1 changes nothing, which is what every item starts at. */
    public static final float MIN = 0.25f;
    public static final float MAX = 4.0f;

    public record Sizes(float hotbar, float hand, float ground) {
        public static final Sizes DEFAULT = new Sizes(1f, 1f, 1f);

        public boolean isDefault() {
            return hotbar == 1f && hand == 1f && ground == 1f;
        }

        public Sizes withHotbar(float value) { return new Sizes(clamp(value), hand, ground); }
        public Sizes withHand(float value) { return new Sizes(hotbar, clamp(value), ground); }
        public Sizes withGround(float value) { return new Sizes(hotbar, hand, clamp(value)); }
    }

    private static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /**
     * Insertion ordered, so the settings screen lists items in the order they
     * were configured rather than reshuffling on every load.
     */
    private static final Map<String, Sizes> overrides = new LinkedHashMap<>();

    /**
     * The key an item is stored under.
     *
     * In one place, because the settings screen and the three renderers have to
     * agree exactly - a key computed two ways is a setting that silently never
     * matches. `ItemStack.getDescriptionId()` does not exist on 26.2; the id
     * lives on the Item, not the stack.
     */
    public static String keyFor(net.minecraft.world.item.ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) return null;
            return stack.getItem().getDescriptionId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The id the item shower uses for its "every item" row. */
    public static final String ALL_ITEMS = "*";

    /** Applies to every item, on top of any item's own size. */
    private static Sizes global = Sizes.DEFAULT;

    public static Sizes global() { return global; }

    public static Sizes get(String itemId) {
        if (ALL_ITEMS.equals(itemId)) return global;
        if (itemId == null) return Sizes.DEFAULT;
        return overrides.getOrDefault(itemId, Sizes.DEFAULT);
    }

    /**
     * The size an item is actually drawn at: its own setting times the one for
     * every item. So "everything in my hand at 80%" still lets the totem be
     * twice that.
     */
    public static Sizes effective(String itemId) {
        Sizes own = itemId == null ? Sizes.DEFAULT : overrides.getOrDefault(itemId, Sizes.DEFAULT);
        if (global.isDefault()) return own;
        return new Sizes(clamp(own.hotbar() * global.hotbar()),
                clamp(own.hand() * global.hand()),
                clamp(own.ground() * global.ground()));
    }

    public static void set(String itemId, Sizes sizes) {
        if (ALL_ITEMS.equals(itemId)) {
            global = sizes;
            return;
        }
        if (itemId == null) return;
        // Defaults are removed rather than stored, so the config stays a list
        // of decisions instead of a copy of the item list
        if (sizes.isDefault()) overrides.remove(itemId);
        else overrides.put(itemId, sizes);
    }

    public static Map<String, Sizes> all() { return overrides; }

    public static void clear() {
        overrides.clear();
        global = Sizes.DEFAULT;
    }

    private static JsonObject write(Sizes sizes) {
        JsonObject entry = new JsonObject();
        entry.addProperty("hotbar", sizes.hotbar());
        entry.addProperty("hand", sizes.hand());
        entry.addProperty("ground", sizes.ground());
        return entry;
    }

    private static Sizes read(JsonObject value) {
        return new Sizes(
                clamp(value.get("hotbar").getAsFloat()),
                clamp(value.get("hand").getAsFloat()),
                clamp(value.get("ground").getAsFloat()));
    }

    public static void save(JsonObject json) {
        JsonObject items = new JsonObject();
        overrides.forEach((id, sizes) -> items.add(id, write(sizes)));
        json.add("items", items);
        json.add("global", write(global));
    }

    public static void load(JsonObject json) {
        overrides.clear();
        global = Sizes.DEFAULT;
        try {
            if (json.has("global")) global = read(json.getAsJsonObject("global"));
        } catch (Exception ignored) {
            // A malformed global entry leaves every item at its own size
        }
        if (!json.has("items")) return;

        JsonObject items = json.getAsJsonObject("items");
        for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
            try {
                overrides.put(entry.getKey(), read(entry.getValue().getAsJsonObject()));
            } catch (Exception ignored) {
                // One malformed entry should not cost the rest of the list
            }
        }
    }

    private ItemSizes() {}
}
