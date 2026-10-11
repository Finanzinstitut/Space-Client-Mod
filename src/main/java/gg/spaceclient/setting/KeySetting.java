package gg.spaceclient.setting;

import com.google.gson.JsonObject;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A key binding, shown in the client's own settings rather than only in the
 * game's controls screen.
 *
 * <h2>Why this stores nothing</h2>
 *
 * Every other setting here owns its value and writes it into the client's
 * config. This one does not, on purpose: the game already keeps key bindings
 * in options.txt, and it is the game that decides whether a key fires. A second
 * copy in the client's config would be a second answer to the same question,
 * and the first time somebody used the controls screen the two would disagree
 * with no non-arbitrary rule for which wins.
 *
 * So this is a window onto the game's binding. It reads it, it writes it, and
 * both screens keep showing the same thing because there is only one thing.
 */
public class KeySetting extends Setting {

    private final IntSupplier read;
    private final IntConsumer write;

    public KeySetting(String id, String name, String description,
                      IntSupplier read, IntConsumer write) {
        super(id, name, description);
        this.read = read;
        this.write = write;
    }

    /** The GLFW code currently bound, or GLFW_KEY_UNKNOWN for none. */
    public int get() { return read.getAsInt(); }

    public void set(int code) { write.accept(code); }

    /**
     * Written and read by the game, not here.
     *
     * Empty rather than absent so the config code needs no special case: it
     * walks every setting a module has and asks each to save itself, and a
     * setting that answers "nothing to save" is a smaller change than an
     * exception in the loop for this one kind.
     */
    @Override
    public void save(JsonObject json) {}

    @Override
    public void load(JsonObject json) {}
}
