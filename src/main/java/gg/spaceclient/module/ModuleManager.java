package gg.spaceclient.module;

import gg.spaceclient.modules.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central registry. Adding a module is one line here. */
public class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        register(new FpsModule());
        register(new CpsModule());
        register(new CoordinatesModule());
        register(new PingModule());
        register(new ClockModule());
        register(new SpeedometerModule());
        register(new SessionModule());
        register(new KeystrokesModule());
        register(new MouseTrackerModule());
    }

    private void register(Module module) {
        modules.put(module.getId(), module);
    }

    public Module get(String id) { return modules.get(id); }

    public List<Module> getAll() { return new ArrayList<>(modules.values()); }

    public List<HudModule> getHudModules() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : modules.values()) {
            if (m instanceof HudModule hud) out.add(hud);
        }
        return out;
    }

    public void onTick() {
        for (Module m : modules.values()) {
            if (m.isEnabled()) m.onTick();
        }
    }
}
