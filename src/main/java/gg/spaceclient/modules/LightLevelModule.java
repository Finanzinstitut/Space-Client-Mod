package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Light level: a readout of the light where you stand, and a cross on every
 * block around you that a monster could spawn on - red where one can, green
 * where the light keeps them away.
 *
 * <h2>The rule</h2>
 *
 * The same test the game uses, read from the dimension rather than assumed: a
 * monster needs the block light at its feet to be no more than the
 * dimension's limit (0 in the overworld) and no brighter than the top of the
 * dimension's light test (7). The block it stands on has to accept a spawn
 * (no glass, slabs, leaves, bedrock...) and the space itself has to be empty
 * enough. Sky light only matters by the clock - open ground is safe by day
 * and not by night - so such spots count as red, and can be shown in their
 * own colour instead.
 *
 * <h2>Why it does not lag</h2>
 *
 * The world is never searched while drawing. The search runs on the game tick
 * with a fixed budget of blocks per tick, so a whole area takes a handful of
 * ticks and no single tick does more than a few thousand cheap lookups. Its
 * result is a flat array handed to the renderer only when complete, and the
 * renderer draws all of it in one batch. A new search starts when you have
 * moved, or every second for blocks placed and torches lit, never more often.
 */
public class LightLevelModule extends HudModule {
    private static LightLevelModule instance;

    private final BooleanSetting crosses = new BooleanSetting(
            "crosses", "Show crosses", "Mark spawnable blocks around you in the world", true);
    private final BooleanSetting showSafe = new BooleanSetting(
            "show_safe", "Show green crosses", "Also mark blocks that are lit well enough", true);
    private final IntSetting range = new IntSetting(
            "range", "Range (blocks)", "How far around you blocks are marked", 16, 4, 32);
    private final IntSetting height = new IntSetting(
            "height", "Height (blocks)", "How far above and below you blocks are marked", 10, 3, 24);
    private final BooleanSetting nightColour = new BooleanSetting(
            "night_yellow", "Night-only in yellow", "Blocks under open sky, where monsters spawn only at night", false);

    private final ColorSetting safe = new ColorSetting(
            "safe_color", "Safe colour", "Cross where no monster can spawn", 0xD040E040);
    private final ColorSetting danger = new ColorSetting(
            "danger_color", "Spawn colour", "Cross where monsters can spawn", 0xE0FF3030);
    private final ColorSetting night = new ColorSetting(
            "night_color", "Night colour", "Cross where monsters spawn only at night", 0xE0FFD030);
    private final ColorSetting text = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    public LightLevelModule() {
        super("lightlevel", "Light Level", "Light where you stand, and red or green crosses where monsters can spawn",
                0.02f, 0.45f, false);
        requires(gg.spaceclient.module.Access.STAFF);
        addGroups(
                SettingGroup.of("Crosses", "Marks on the ground around you",
                        crosses, showSafe, range, height, nightColour),
                SettingGroup.of("Colours", "What each mark means",
                        danger, safe, night, text)
        );
        instance = this;
    }

    /** The module when crosses should be drawn, else null. */
    public static LightLevelModule drawing() {
        LightLevelModule m = instance;
        return m != null && m.isEnabled() && m.crosses.get() ? m : null;
    }

    // ---------------------------------------------------------------- the readout

    private String readout() { return cachedText(this::buildReadout); }

    private String buildReadout() {
        if (mc.player == null || mc.level == null) return "Light --";
        BlockPos feet = mc.player.blockPosition();
        int block = mc.level.getBrightness(LightLayer.BLOCK, feet);
        int sky = mc.level.getBrightness(LightLayer.SKY, feet);
        return "Light " + block + "   Sky " + sky;
    }

    @Override
    public int getWidth() { return mc.font.width(readout()); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, readout(), x, y, text.get(), true);
    }

    // ---------------------------------------------------------------- the search

    /** Blocks looked at per tick. A few thousand cheap lookups, well under a millisecond. */
    private static final int BUDGET = 6000;
    /** Ticks between searches when standing still, for torches placed and blocks broken. */
    private static final int REFRESH_TICKS = 20;
    /** More than this many marks is a mess on screen anyway. */
    private static final int MAX_MARKS = 24000;

    /** Finished marks: packed positions and their kind, read by the renderer. */
    private long[] marks = new long[0];
    private byte[] kinds = new byte[0];
    private int markCount = 0;

    // The search in progress
    private long[] building = new long[1024];
    private byte[] buildingKinds = new byte[1024];
    private int buildingCount = 0;
    private boolean searching = false;
    private int ox, oy, oz, r, h, cursor, total;
    private int sinceSearch = 0;
    private BlockPos lastOrigin = null;
    private ClientLevel lastLevel = null;
    private int limit, maxTest;
    private boolean sky;

    public static final byte SAFE = 0, DANGER = 1, NIGHT = 2;

    /** A monster the spawn rules are checked for; any surface monster gives the same answer. */
    //#if MC >= 26.2
    private static final EntityType<?> SPAWN_TEST = net.minecraft.world.entity.EntityTypes.ZOMBIE;
    //#else
    //$$ private static final EntityType<?> SPAWN_TEST = EntityType.ZOMBIE;
    //#endif

    public int markCount() { return markCount; }
    public long[] marks() { return marks; }
    public byte[] kinds() { return kinds; }
    public int colourFor(byte kind) {
        return switch (kind) {
            case DANGER -> danger.get();
            case NIGHT -> night.get();
            default -> safe.get();
        };
    }

    @Override
    public void onTick() {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !crosses.get()) {
            markCount = 0;
            searching = false;
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            markCount = 0;
            searching = false;
        }

        sinceSearch++;
        BlockPos here = mc.player.blockPosition();
        boolean moved = lastOrigin == null || lastOrigin.distManhattan(here) >= 2;
        if (!searching && (moved || sinceSearch >= REFRESH_TICKS)) start(level, here);
        if (searching) step(level);
    }

    @Override
    protected void onDisable() {
        markCount = 0;
        searching = false;
        lastOrigin = null;
    }

    private void start(ClientLevel level, BlockPos origin) {
        lastOrigin = origin;
        sinceSearch = 0;
        ox = origin.getX();
        oy = origin.getY();
        oz = origin.getZ();
        r = range.get();
        h = height.get();
        cursor = 0;
        int side = 2 * r + 1;
        total = side * side * (2 * h + 1);
        buildingCount = 0;
        var type = level.dimensionType();
        limit = type.monsterSpawnBlockLightLimit();
        maxTest = type.monsterSpawnLightTest().maxInclusive();
        sky = type.hasSkyLight();
        searching = true;
    }

    private void step(ClientLevel level) {
        int side = 2 * r + 1;
        int layer = side * side;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        boolean wantSafe = showSafe.get();
        boolean wantNight = nightColour.get();
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        int end = Math.min(total, cursor + BUDGET);
        for (; cursor < end; cursor++) {
            int dy = cursor / layer - h;
            int rest = cursor % layer;
            int x = ox + rest % side - r;
            int z = oz + rest / side - r;
            int y = oy + dy;
            if (y <= minY || y >= maxY) continue;

            pos.set(x, y, z);
            BlockState space = level.getBlockState(pos);
            // The common case first: inside stone, nothing to do
            if (!space.isAir() && space.isCollisionShapeFullBlock(level, pos)) continue;
            below.set(x, y - 1, z);
            BlockState ground = level.getBlockState(below);
            if (ground.isAir()) continue;
            if (!ground.isValidSpawn(level, below, SPAWN_TEST)) continue;
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, space, space.getFluidState(), SPAWN_TEST)) continue;

            int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
            byte kind;
            if (blockLight > limit || blockLight > maxTest) {
                kind = SAFE;
            } else if (sky && wantNight && level.getBrightness(LightLayer.SKY, pos) > maxTest) {
                kind = NIGHT;
            } else {
                kind = DANGER;
            }
            if (kind == SAFE && !wantSafe) continue;
            add(BlockPos.asLong(x, y, z), kind);
        }

        if (cursor >= total) {
            // Handed over in one go, so the renderer never sees half a search
            marks = java.util.Arrays.copyOf(building, buildingCount);
            kinds = java.util.Arrays.copyOf(buildingKinds, buildingCount);
            markCount = buildingCount;
            searching = false;
        }
    }

    private void add(long packed, byte kind) {
        if (buildingCount >= MAX_MARKS) return;
        if (buildingCount == building.length) {
            building = java.util.Arrays.copyOf(building, building.length * 2);
            buildingKinds = java.util.Arrays.copyOf(buildingKinds, buildingKinds.length * 2);
        }
        building[buildingCount] = packed;
        buildingKinds[buildingCount] = kind;
        buildingCount++;
    }
}
