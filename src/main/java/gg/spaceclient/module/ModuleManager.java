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
        register(new MemoryModule());
        register(new MemoryWarnModule());
        register(new ServerHudModule());
        register(new CompassModule());
        register(new TravelModule());
        register(new ZoomModule());
        register(new HitboxModule());
        register(new ChunkModule());
        register(new ServerInfoModule());
        register(new CrosshairInfoModule());
        register(new YawLockModule());
        register(new InputRateModule());
        register(new CoordsCopyModule());
        register(new WaveyCapeModule());
        register(new TpsModule());
        register(new ArmorModule());
        register(new MusicModule());
        register(new HitColorModule());
        register(new TwitchModule());
        register(new DirectionModule());
        register(new NetherPortalModule());
        register(new ConnectionModule());
        register(new SpacePlayersModule());
        register(new DayTimeModule());
        register(new InventoryModule());
        register(new SuppliesModule());
        register(new HealthModule());
        register(new DurabilityModule());
        register(new SaturationModule());
        register(new FullbrightModule());
        register(new ContainerBrandModule());
        register(new OverlayModule());
        register(new ScoreboardModule());
        register(new ClipModule());
        register(new NutritionModule());
        register(new ServerModule());
        register(new ItemPhysicsModule());
        register(new FpsBoostModule());
        register(new ReachModule());
        register(new BlockHighlightModule());
        register(new EffectsModule());
        register(new TotemPopModule());
        register(new LightLevelModule());
        register(new MotionBlurModule());
    }

    private void register(Module module) {
        modules.put(module.getId(), module);
    }

    public Module get(String id) { return modules.get(id); }

    public List<Module> getAll() { return new ArrayList<>(modules.values()); }

    private List<HudModule> hudCache = null;
    private int hudCacheFor = -1;

    /**
     * The HUD elements, in registration order.
     *
     * Asked for every frame by the HUD loop, which used to build a new list
     * each time - a fresh array and a type check per module, four hundred
     * times a second, for a set that only changes when a module is registered.
     * Rebuilt when the number of modules differs from the last build.
     * The returned list is read-only.
     */
    public List<HudModule> getHudModules() {
        List<HudModule> cached = hudCache;
        if (cached != null && hudCacheFor == modules.size()) return cached;

        List<HudModule> out = new ArrayList<>();
        for (Module m : modules.values()) {
            if (m instanceof HudModule hud) out.add(hud);
        }
        hudCache = java.util.Collections.unmodifiableList(out);
        hudCacheFor = modules.size();
        return hudCache;
    }

    public void onTick() {
        for (Module m : modules.values()) {
            m.syncAccess();
            if (m.isEnabled()) m.onTick();
        }
    }
}
