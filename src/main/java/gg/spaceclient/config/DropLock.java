package gg.spaceclient.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The items Drop Lock keeps in your inventory, by registry id
 * ("minecraft:totem_of_undying"). The id rather than the description id the
 * Item Shower uses because it is the one name an item keeps across versions
 * and languages, and the one the item grid lists them by.
 */
public final class DropLock {

    private static final Set<String> locked = Collections.synchronizedSet(new LinkedHashSet<>());

    private DropLock() {}

    /** The registry id of a stack's item, or null for an empty stack. */
    public static String idOf(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) return null;
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isLocked(String id) {
        return id != null && locked.contains(id);
    }

    public static boolean isLocked(ItemStack stack) {
        return isLocked(idOf(stack));
    }

    public static void toggle(String id) {
        if (id == null) return;
        if (!locked.remove(id)) locked.add(id);
    }

    public static void clear() {
        locked.clear();
    }

    public static int count() {
        return locked.size();
    }

    public static void save(JsonObject json) {
        JsonArray items = new JsonArray();
        synchronized (locked) {
            for (String id : locked) items.add(id);
        }
        json.add("items", items);
    }

    public static void load(JsonObject json) {
        locked.clear();
        if (!json.has("items")) return;
        for (JsonElement element : json.getAsJsonArray("items")) {
            try {
                locked.add(element.getAsString());
            } catch (Throwable ignored) {
                // One odd entry is skipped, not the whole list
            }
        }
    }
}
