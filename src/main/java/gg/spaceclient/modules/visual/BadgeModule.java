package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ModeSetting;

import java.util.Arrays;

/**
 * The Jupiter badge shown in front of Space Client users' names.
 *
 * The twist worth having: SELF_ONLY vs EVERYONE. Most clients force their badge
 * on you with no way to hide other people's, which turns a busy lobby into a
 * wall of icons. Here you choose.
 */
public class BadgeModule extends Module {
    private final ModeSetting scope = new ModeSetting(
            "scope", "Show for", "Whose badge to display",
            Arrays.asList("EVERYONE", "SELF_ONLY", "OTHERS_ONLY"), "EVERYONE");

    private final BooleanSetting inNametags = new BooleanSetting(
            "nametags", "Above heads", "Draw the badge in the floating name tag", true);

    private final BooleanSetting inTabList = new BooleanSetting(
            "tab_list", "In tab list", "Draw the badge in the player list", true);

    private final BooleanSetting inChat = new BooleanSetting(
            "chat", "In chat", "Prefix chat messages from users", false);

    public BadgeModule() {
        super("badge", "Jupiter Badge", "Marks Space Client users with a Jupiter icon", Category.VISUAL);
        addSettings(scope, inNametags, inTabList, inChat);
    }

    public boolean showFor(boolean isSelf) {
        if (scope.is("SELF_ONLY")) return isSelf;
        if (scope.is("OTHERS_ONLY")) return !isSelf;
        return true;
    }

    public boolean inNametags() { return inNametags.get(); }
    public boolean inTabList() { return inTabList.get(); }
    public boolean inChat() { return inChat.get(); }
}
