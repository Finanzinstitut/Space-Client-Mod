package gg.spaceclient.module;

import gg.spaceclient.modules.hud.*;
import gg.spaceclient.modules.visual.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central registry. Adding a module means one line in register(). */
public class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        register(new FpsModule());
        register(new CpsModule());
        register(new CoordinatesModule());
        register(new PingModule());
        register(new ClockModule());
        register(new KeystrokesModule());
        register(new MouseTrackerModule());
        register(new ArmorStatusModule());
        register(new FullbrightModule());
        register(new HitboxModule());
        register(new ZoomModule());
        register(new ToggleSprintModule());
        register(new ReachModule());
        register(new ComboCounterModule());
        register(new TpsModule());
        register(new SaturationModule());
        register(new SpeedometerModule());
        register(new PotionStatusModule());
        register(new SessionModule());
        register(new ItemCounterModule());
        register(new DurabilityAlertModule());
        register(new TntTimerModule());
        register(new AutoReconnectModule());
        register(new TimeChangerModule());
        register(new BadgeModule());
    }

    private void register(Module module) {
        modules.put(module.getId(), module);
    }

    public Module get(String id) {
        return modules.get(id);
    }

    public List<Module> getAll() {
        return new ArrayList<>(modules.values());
    }

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
