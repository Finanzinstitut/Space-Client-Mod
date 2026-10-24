package gg.spaceclient.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.spaceclient.SpaceClient;
import gg.spaceclient.session.LauncherAccounts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * The skin library, shared with the launcher.
 *
 * The launcher keeps every skin that passed through it in a folder next to its
 * settings, with an index.json beside the files. Reading and writing the very
 * same files is what makes it one library: a skin saved in the game is in the
 * launcher's grid, and the other way round. The format is the launcher's -
 * each file named after the SHA-1 of its bytes, so the same skin twice is one
 * entry.
 */
public final class SkinLibrary {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One saved skin, as the index describes it. */
    public record Entry(String id, String name, String variant, String file, long added) {
        public boolean slim() { return "slim".equals(variant); }
        public Path path() { return dir().resolve(file); }
    }

    private SkinLibrary() {}

    public static Path dir() {
        return LauncherAccounts.configDir().resolve("skins");
    }

    private static Path index() {
        return dir().resolve("index.json");
    }

    /** Every skin whose file is there, newest first. */
    public static synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : read()) {
            if (Files.exists(entry.path())) out.add(entry);
        }
        out.sort(Comparator.comparingLong(Entry::added).reversed());
        return out;
    }

    private static List<Entry> read() {
        List<Entry> out = new ArrayList<>();
        Path file = index();
        if (!Files.exists(file)) return out;
        try {
            JsonArray array = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
            for (var element : array) {
                JsonObject o = element.getAsJsonObject();
                out.add(new Entry(
                        str(o, "id"), str(o, "name"),
                        "slim".equals(str(o, "variant")) ? "slim" : "classic",
                        str(o, "file"),
                        o.has("added") ? o.get("added").getAsLong() : 0L));
            }
        } catch (Exception e) {
            SpaceClient.LOGGER.warn("Could not read the skin library: {}", e.getMessage());
        }
        return out;
    }

    private static void write(List<Entry> entries) throws Exception {
        JsonArray array = new JsonArray();
        for (Entry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("name", e.name());
            o.addProperty("variant", e.variant());
            o.addProperty("file", e.file());
            o.addProperty("added", e.added());
            array.add(o);
        }
        Files.createDirectories(dir());
        // Through a temporary file: the launcher may be reading this right now
        Path temp = index().resolveSibling("index.json.part");
        Files.writeString(temp, GSON.toJson(array));
        Files.move(temp, index(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Stores PNG bytes. The same image again keeps its entry and only takes
     * the new model.
     */
    public static synchronized Entry store(byte[] png, String name, boolean slim) throws Exception {
        if (!isPng(png)) throw new IllegalArgumentException("That file is not a PNG image.");
        String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(png));
        String file = id + ".png";
        Files.createDirectories(dir());
        Files.write(dir().resolve(file), png);

        String variant = slim ? "slim" : "classic";
        List<Entry> entries = read();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.id().equals(id)) {
                Entry updated = new Entry(e.id(), e.name(), variant, e.file(), e.added());
                entries.set(i, updated);
                write(entries);
                return updated;
            }
        }
        String display = name == null || name.isBlank() ? "Skin " + id.substring(0, 6) : name.trim();
        Entry entry = new Entry(id, display, variant, file, System.currentTimeMillis() / 1000);
        entries.add(entry);
        write(entries);
        return entry;
    }

    public static synchronized void setVariant(String id, boolean slim) throws Exception {
        List<Entry> entries = read();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.id().equals(id)) entries.set(i, new Entry(e.id(), e.name(), slim ? "slim" : "classic", e.file(), e.added()));
        }
        write(entries);
    }

    /** Forgets a skin. Whatever is on the account stays on it. */
    public static synchronized void delete(String id) throws Exception {
        List<Entry> entries = read();
        List<Entry> kept = new ArrayList<>();
        for (Entry e : entries) {
            if (e.id().equals(id)) Files.deleteIfExists(e.path());
            else kept.add(e);
        }
        write(kept);
    }

    /**
     * The drop folder: PNG files put in here go into the library the next
     * time it is looked at, and leave the folder. The way in for a version of
     * the game that has no file dialog of its own.
     */
    public static Path inbox() {
        return dir().resolve("add");
    }

    /** Takes in whatever is waiting in the drop folder; how many it took. */
    public static int takeInbox() {
        Path inbox = inbox();
        int taken = 0;
        try {
            Files.createDirectories(inbox);
            try (var files = Files.list(inbox)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase().endsWith(".png")) continue;
                    try {
                        store(Files.readAllBytes(file), name.substring(0, name.length() - 4), false);
                        Files.delete(file);
                        taken++;
                    } catch (Exception e) {
                        SpaceClient.LOGGER.warn("Could not take {} into the skin library: {}", name, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            SpaceClient.LOGGER.warn("Could not read the skin drop folder: {}", e.getMessage());
        }
        return taken;
    }

    static boolean isPng(byte[] bytes) {
        return bytes != null && bytes.length > 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
