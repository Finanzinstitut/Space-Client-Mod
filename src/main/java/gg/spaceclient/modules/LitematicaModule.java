package gg.spaceclient.modules;

import gg.spaceclient.compat.Litematica;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Litematica helper: what a schematic says that Litematica only shows on
 * request, and filling containers the way the schematic has them.
 *
 * <h2>Redstone values</h2>
 *
 * A repeater's delay is a torch a pixel further along, and a comparator's
 * mode a torch that is lit or not - easy to get wrong when building and hard
 * to see on a ghost block. Every repeater, comparator, note block and
 * inverted daylight sensor of the schematic near you gets a label: white
 * while it is still to be built, green once the real block matches, red with
 * the real value when it was placed wrong.
 *
 * <h2>Containers</h2>
 *
 * The schematic keeps what is in each chest, hopper, dropper and so on.
 * Opening the real one fills it from your inventory, slot for slot and with
 * exactly the counts the schematic has - item sorters depend on them. With
 * Easy Place, a container you have just placed is opened, filled and closed
 * again by itself. Whatever you do not carry is listed afterwards.
 *
 * The filling is ordinary inventory clicks, a few per tick, the same as doing
 * it by hand. Nothing is taken from the schematic that the game would not
 * also show you.
 */
public class LitematicaModule extends Module {
    private static LitematicaModule instance;

    private final BooleanSetting labels = new BooleanSetting(
            "labels", "Show redstone values", "Repeater ticks, comparator mode and note pitch over schematic blocks", true);
    private final BooleanSetting labelsPlaced = new BooleanSetting(
            "labels_placed", "Also when built right", "Keep a green label on blocks that already match", true);
    private final IntSetting range = new IntSetting(
            "range", "Range (blocks)", "How far around you labels are shown", 20, 6, 48);

    private final BooleanSetting fill = new BooleanSetting(
            "fill", "Fill containers", "Put the schematic's items into a container when you open it", true);
    private final BooleanSetting openPlaced = new BooleanSetting(
            "open_placed", "Open after placing", "Open and fill a container right after you place it", true);
    private final BooleanSetting closeFilled = new BooleanSetting(
            "close_filled", "Close when filled", "Close a container this opened by itself once it is full", true);
    private final IntSetting speed = new IntSetting(
            "speed", "Clicks per tick", "How fast items are moved; lower on strict servers", 8, 1, 40);

    public LitematicaModule() {
        super("litematica", "Litematica Helper",
                "Redstone values over schematic blocks, and containers filled like the schematic (needs Litematica)",
                true);
        addGroups(
                SettingGroup.of("Redstone", "Labels over schematic blocks", labels, labelsPlaced, range),
                SettingGroup.of("Containers", "Items from the schematic", fill, openPlaced, closeFilled, speed)
        );
        instance = this;
    }

    // ---------------------------------------------------------------- labels

    /** Finished labels, read by the renderer: packed positions, text, colour. */
    private long[] labelPos = new long[0];
    private String[] labelText = new String[0];
    private int[] labelColour = new int[0];
    private float[] labelLift = new float[0];

    public static final int TO_BUILD = 0xFFFFFF, MATCHES = 0x55FF55, WRONG = 0xFF5555;
    private static final int MAX_LABELS = 400;
    private static final int BUDGET = 8000;
    private static final int REFRESH_TICKS = 10;

    private final List<Long> buildingPos = new ArrayList<>();
    private final List<String> buildingText = new ArrayList<>();
    private final List<Integer> buildingColour = new ArrayList<>();
    private final List<Float> buildingLift = new ArrayList<>();
    private boolean searching = false;
    private int ox, oy, oz, r, h, cursor, total, sinceSearch = 0;

    /** The module while labels should be drawn, else null. */
    public static LitematicaModule drawing() {
        LitematicaModule m = instance;
        return m != null && m.isEnabled() && m.labels.get() && m.labelPos.length > 0 ? m : null;
    }

    public long[] labelPositions() { return labelPos; }
    public String[] labelTexts() { return labelText; }
    public int[] labelColours() { return labelColour; }
    /** How tall each labelled block is, so its label sits just over it. */
    public float[] labelLifts() { return labelLift; }

    private void searchLabels(ClientLevel level, Level schematic) {
        sinceSearch++;
        if (!searching && sinceSearch >= REFRESH_TICKS) {
            BlockPos here = mc.player.blockPosition();
            ox = here.getX();
            oy = here.getY();
            oz = here.getZ();
            r = range.get();
            h = Math.max(4, r / 2);
            int side = 2 * r + 1;
            total = side * side * (2 * h + 1);
            cursor = 0;
            buildingPos.clear();
            buildingText.clear();
            buildingColour.clear();
            buildingLift.clear();
            searching = true;
            sinceSearch = 0;
        }
        if (!searching) return;

        int side = 2 * r + 1;
        int layer = side * side;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean wantPlaced = labelsPlaced.get();
        int end = Math.min(total, cursor + BUDGET);
        for (; cursor < end; cursor++) {
            int y = oy + cursor / layer - h;
            int rest = cursor % layer;
            pos.set(ox + rest % side - r, y, oz + rest / side - r);
            BlockState wanted = schematic.getBlockState(pos);
            if (wanted.isAir()) continue;
            String text = valueOf(wanted);
            if (text == null) continue;

            BlockState real = level.getBlockState(pos);
            int colour;
            if (real.getBlock() != wanted.getBlock()) {
                colour = TO_BUILD;
            } else {
                String actual = valueOf(real);
                if (text.equals(actual)) {
                    if (!wantPlaced) continue;
                    colour = MATCHES;
                } else {
                    colour = WRONG;
                    text = text + " (" + (actual == null ? "off" : actual) + ")";
                }
            }
            if (buildingPos.size() >= MAX_LABELS) continue;
            buildingPos.add(pos.asLong());
            buildingText.add(text);
            buildingColour.add(colour);
            float lift;
            try {
                var shape = wanted.getShape(schematic, pos);
                lift = shape.isEmpty() ? 0.2f : (float) shape.max(Direction.Axis.Y);
            } catch (Throwable t) {
                lift = 1f;
            }
            buildingLift.add(lift);
        }

        if (cursor >= total) {
            int n = buildingPos.size();
            long[] p = new long[n];
            String[] t = new String[n];
            int[] c = new int[n];
            float[] l = new float[n];
            for (int i = 0; i < n; i++) {
                l[i] = buildingLift.get(i);
                p[i] = buildingPos.get(i);
                t[i] = buildingText.get(i);
                c[i] = buildingColour.get(i);
            }
            // Handed over in one go, so the renderer never sees half a search
            labelText = t;
            labelColour = c;
            labelLift = l;
            labelPos = p;
            searching = false;
        }
    }

    /** The value worth a label on a block, or null for blocks without one. */
    static String valueOf(BlockState state) {
        if (state.hasProperty(RepeaterBlock.DELAY) && state.getBlock() instanceof RepeaterBlock) {
            return state.getValue(RepeaterBlock.DELAY) + "t";
        }
        if (state.getBlock() instanceof ComparatorBlock && state.hasProperty(ComparatorBlock.MODE)) {
            return state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? "Subtract" : "Compare";
        }
        if (state.getBlock() instanceof NoteBlock && state.hasProperty(NoteBlock.NOTE)) {
            return "Note " + state.getValue(NoteBlock.NOTE);
        }
        if (state.getBlock() instanceof DaylightDetectorBlock && state.hasProperty(DaylightDetectorBlock.INVERTED)) {
            return state.getValue(DaylightDetectorBlock.INVERTED) ? "Inverted" : null;
        }
        return null;
    }

    // ---------------------------------------------------------------- containers

    /** A container being filled: its menu, and what each of its slots should hold. */
    private static final class Job {
        final int containerId;
        final BlockPos pos;
        final ItemStack[] wanted;
        final boolean openedByUs;
        final Set<Integer> skipped = new HashSet<>();
        final Map<String, Integer> missing = new LinkedHashMap<>();
        int moved = 0;
        // A stack picked up and still being handed out one by one
        int carrySource = -1, carryTarget = -1, carryLeft = 0;

        Job(int containerId, BlockPos pos, ItemStack[] wanted, boolean openedByUs) {
            this.containerId = containerId;
            this.pos = pos;
            this.wanted = wanted;
            this.openedByUs = openedByUs;
        }
    }

    /** A block just used, to see whether a container of the schematic appears there. */
    private record Placed(BlockPos pos, long until) {}

    private Job job = null;
    private int handledContainer = -1;
    private final List<Placed> placed = new ArrayList<>();
    /** Containers already filled, so a closed one is not opened again. */
    private final Set<Long> done = new HashSet<>();
    private BlockPos opening = null;
    private long openingUntil = 0;
    private boolean ourClick = false;
    private long ticks = 0;
    private ClientLevel lastLevel = null;

    /**
     * Called before every use of a block, from the game's own call - which is
     * also the one Easy Place makes. The block clicked and the one beside it,
     * where a placed block would go, are remembered for a moment.
     */
    public static void onUseBlock(BlockHitResult hit) {
        LitematicaModule m = instance;
        if (m == null || !m.isEnabled() || m.ourClick || hit == null) return;
        if (!m.fill.get() || !m.openPlaced.get()) return;
        if (m.mc.level == null || m.placed.size() > 64) return;
        Level schematic = Litematica.schematicWorld();
        if (schematic == null) return;
        long until = m.ticks + 40;
        for (BlockPos pos : new BlockPos[]{hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection())}) {
            BlockState wanted = schematic.getBlockState(pos);
            // Only where the right block is not there yet: a block that is
            // already right was clicked to be opened, not placed
            if (wanted.isAir() || m.mc.level.getBlockState(pos).getBlock() == wanted.getBlock()) continue;
            if (!(schematic.getBlockEntity(pos) instanceof Container)) continue;
            m.placed.add(new Placed(pos.immutable(), until));
        }
    }

    @Override
    public void onTick() {
        ticks++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.gameMode == null) {
            job = null;
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            done.clear();
            placed.clear();
            job = null;
            labelPos = new long[0];
        }
        Level schematic = Litematica.schematicWorld();
        if (schematic == null) {
            labelPos = new long[0];
            job = null;
            return;
        }

        if (labels.get()) searchLabels(level, schematic);
        else labelPos = new long[0];

        if (!fill.get()) {
            job = null;
            placed.clear();
            return;
        }

        net.minecraft.client.gui.screens.Screen current = gg.spaceclient.compat.Screens.current();
        if (current instanceof AbstractContainerScreen<?> screen) {
            AbstractContainerMenu menu = screen.getMenu();
            if (job != null && job.containerId != menu.containerId) job = null;
            if (job == null && menu.containerId != handledContainer) {
                handledContainer = menu.containerId;
                job = start(menu, schematic, level);
            }
            if (job != null) step(menu);
        } else {
            job = null;
            if (current == null) openPlaced(schematic, level);
        }
    }

    /** The container a just opened menu belongs to, and what it should hold. */
    private Job start(AbstractContainerMenu menu, Level schematic, ClientLevel level) {
        boolean ours = opening != null && ticks <= openingUntil;
        BlockPos pos = ours ? opening : null;
        opening = null;
        if (pos == null) {
            HitResult hit = mc.hitResult;
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
            pos = block.getBlockPos();
        }
        BlockState wanted = schematic.getBlockState(pos);
        if (wanted.isAir() || level.getBlockState(pos).getBlock() != wanted.getBlock()) return null;

        ItemStack[] items = itemsOf(schematic, pos, wanted);
        if (items == null) return null;
        boolean any = false;
        for (ItemStack stack : items) any |= !stack.isEmpty();
        if (!any) return null;

        int slots = 0;
        for (Slot slot : menu.slots) if (!(slot.container instanceof Inventory)) slots++;
        // A different container than the schematic's: leave it alone
        if (slots != items.length) return null;
        return new Job(menu.containerId, pos, items, ours);
    }

    /**
     * The schematic's items for a container, in the order the game's menu
     * shows them. A double chest is two block entities; the game puts the
     * half whose type is RIGHT first.
     */
    private static ItemStack[] itemsOf(Level schematic, BlockPos pos, BlockState state) {
        BlockEntity entity = schematic.getBlockEntity(pos);
        if (!(entity instanceof Container container)) return null;
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
            if (schematic.getBlockEntity(otherPos) instanceof Container other) {
                boolean right = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
                return join(right ? container : other, right ? other : container);
            }
        }
        return join(container, null);
    }

    private static ItemStack[] join(Container first, Container second) {
        int a = first.getContainerSize(), b = second == null ? 0 : second.getContainerSize();
        ItemStack[] out = new ItemStack[a + b];
        for (int i = 0; i < a; i++) out[i] = first.getItem(i).copy();
        for (int i = 0; i < b; i++) out[a + i] = second.getItem(i).copy();
        return out;
    }

    /** A few clicks of the job; the rest next tick. */
    private void step(AbstractContainerMenu menu) {
        Job j = job;
        int budget = speed.get();
        while (budget > 0) {
            if (j.carryLeft > 0) {
                Slot target = menu.slots.get(j.carryTarget);
                click(menu, j.carryTarget, 1);
                budget--;
                j.carryLeft--;
                j.moved++;
                if (j.carryLeft == 0 || menu.getCarried().isEmpty() || target.getItem().getCount() >= target.getMaxStackSize()) {
                    j.carryLeft = 0;
                    // What is left goes back where it came from
                    if (!menu.getCarried().isEmpty()) {
                        click(menu, j.carrySource, 0);
                        budget--;
                    }
                }
                continue;
            }
            // Something on the cursor that is not ours: hands off
            if (!menu.getCarried().isEmpty()) {
                finish(j, false);
                return;
            }
            if (!next(menu, j)) {
                finish(j, true);
                return;
            }
            budget--;
        }
    }

    /**
     * Starts the next transfer, or returns false when nothing more can be
     * moved. A whole source stack that fits goes in with one click; otherwise
     * the stack is picked up and handed out one item per right click.
     */
    private boolean next(AbstractContainerMenu menu, Job j) {
        int index = 0;
        for (int s = 0; s < menu.slots.size(); s++) {
            Slot slot = menu.slots.get(s);
            if (slot.container instanceof Inventory) continue;
            int i = index++;
            if (i >= j.wanted.length || j.skipped.contains(i)) continue;
            ItemStack want = j.wanted[i];
            if (want.isEmpty()) continue;
            ItemStack have = slot.getItem();
            if (!have.isEmpty() && !ItemStack.isSameItemSameComponents(have, want)) {
                j.skipped.add(i);
                continue;
            }
            int need = want.getCount() - have.getCount();
            if (need <= 0) continue;
            if (!slot.mayPlace(want)) {
                j.skipped.add(i);
                continue;
            }

            int source = sourceFor(menu, want);
            if (source < 0) {
                j.skipped.add(i);
                j.missing.merge(want.getHoverName().getString(), need, Integer::sum);
                continue;
            }
            int available = menu.slots.get(source).getItem().getCount();
            click(menu, source, 0);
            if (available <= need) {
                click(menu, s, 0);
                j.moved += available;
            } else {
                j.carrySource = source;
                j.carryTarget = s;
                j.carryLeft = need;
            }
            return true;
        }
        return false;
    }

    /** A slot of the player's own inventory holding this exact item, or -1. */
    private static int sourceFor(AbstractContainerMenu menu, ItemStack want) {
        for (int s = 0; s < menu.slots.size(); s++) {
            Slot slot = menu.slots.get(s);
            if (!(slot.container instanceof Inventory)) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, want) && slot.mayPickup(mc.player)) return s;
        }
        return -1;
    }

    private void click(AbstractContainerMenu menu, int slot, int button) {
        //#if MC >= 26.1
        mc.gameMode.handleContainerInput(menu.containerId, slot, button,
                net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
        //#else
        //$$ mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button,
        //$$         net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        //#endif
    }

    private void finish(Job j, boolean complete) {
        job = null;
        done.add(j.pos.asLong());
        if (!j.missing.isEmpty()) {
            StringBuilder line = new StringBuilder("Missing: ");
            int shown = 0;
            for (var e : j.missing.entrySet()) {
                if (shown++ > 0) line.append(", ");
                if (shown > 4) {
                    line.append("+").append(j.missing.size() - 4).append(" more");
                    break;
                }
                line.append(e.getValue()).append("x ").append(e.getKey());
            }
            overlay(line.toString());
        } else if (complete && j.moved > 0) {
            overlay("Filled like the schematic");
        }
        if (complete && j.openedByUs && closeFilled.get() && j.missing.isEmpty() && mc.player != null) {
            mc.player.closeContainer();
        }
    }

    /** Opens a container that was just placed where the schematic has one with items. */
    private void openPlaced(Level schematic, ClientLevel level) {
        if (!openPlaced.get() || placed.isEmpty()) return;
        placed.removeIf(p -> ticks > p.until() || done.contains(p.pos().asLong()));
        for (Placed p : placed) {
            BlockPos pos = p.pos();
            BlockState wanted = schematic.getBlockState(pos);
            if (level.getBlockState(pos).getBlock() != wanted.getBlock()) continue;
            // Give the server a moment to confirm the block before using it
            if (p.until() - ticks > 36) return;
            ItemStack[] items = itemsOf(schematic, pos, wanted);
            boolean any = false;
            if (items != null) for (ItemStack stack : items) any |= !stack.isEmpty();
            if (!any) {
                done.add(pos.asLong());
                continue;
            }
            // Sneaking would place the held block instead of opening; wait
            if (mc.player.isShiftKeyDown()) return;
            if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 25) continue;

            done.add(pos.asLong());
            opening = pos;
            openingUntil = ticks + 40;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            ourClick = true;
            try {
                Litematica.withoutEasyPlace(() -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit));
            } finally {
                ourClick = false;
            }
            return;
        }
    }

    private void overlay(String text) {
        if (mc.player == null) return;
        //#if MC >= 26.1
        mc.player.sendOverlayMessage(Component.literal(text));
        //#else
        //$$ mc.player.displayClientMessage(Component.literal(text), true);
        //#endif
    }

    @Override
    protected void onDisable() {
        job = null;
        placed.clear();
        labelPos = new long[0];
        searching = false;
    }
}
