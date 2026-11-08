package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.social.FriendList;
import dev.deltavisuals.util.I18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Friends: settings and hooks. The list itself (add, remove, storage) is {@link FriendList}, edited in the "Friends"
 * tab of the menu. Everything here is local: no network, nothing is sent or received.
 *
 *  - Don't hit friends: a melee hit on a listed player is cancelled on this client before it is sent.
 *  - Friends are highlighted in another colour (green by default) in Target ESP, Target HUD and Hitbox Fill.
 */
public final class Friends extends Module {
    private final Setting.Bool dontHit, highlight;
    private final Setting.Color friendColor;
    private long lastNotice;

    public Friends() {
        super("Friends", "Local friend list: no hitting friends and a green highlight. Nothing is sent anywhere", Category.FRIENDS, true);
        add(new Setting.Header("Protection"));
        dontHit = add(new Setting.Bool("Don't Hit Friends", true));
        add(new Setting.Header("Look"));
        highlight = add(new Setting.Bool("Highlight Friends", true));
        friendColor = add(new Setting.Color("Friend Color", 0xFF4ADE80, false));
        friendColor.visibleIf(highlight::get);
        add(new Setting.Header("Storage"));
        add(new Setting.Info("File", () -> "config/delta_visuals/friends.json"));
        add(new Setting.Info("Note", () -> "The list is local. A friend with a known UUID is matched by UUID, otherwise by nick"));
    }

    @Override public void onDisable() { FriendList.clearState(); }

    @Override
    public void onTick(MinecraftClient mc) { FriendList.tick(mc); }

    /** Colour (RGB) used for friends in ESP, Target HUD and Hitbox Fill. */
    public int color() { return friendColor.rgb(); }

    private static boolean listed(Entity e) {
        return e instanceof PlayerEntity p && FriendList.isFriend(p.getUuid(), p.getName().getString());
    }

    /** True for a listed player when friends are highlighted. */
    public boolean highlighted(Entity e) { return isEnabled() && highlight.get() && listed(e); }

    /** True when a melee hit on this entity must be cancelled. */
    public boolean protects(Entity e) {
        if (!isEnabled() || !dontHit.get() || !listed(e)) return false;
        long now = System.currentTimeMillis();
        if (now - lastNotice > 1500) {
            lastNotice = now;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Delta] " + I18n.t("Hit on a friend cancelled")), true);
        }
        return true;
    }
}
