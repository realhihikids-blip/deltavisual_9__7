package dev.deltavisuals.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.deltavisuals.util.Compat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * The friend list. 100% LOCAL: this class opens no connection and sends nothing anywhere. The only thing it touches
 * outside the game is one file on this computer:
 *
 *     .minecraft/config/delta_visuals/friends.json     {"version": 2, "friends": [{"name": "...", "uuid": "..."}]}
 *
 * A friend is added by Minecraft nick or by UUID. Matching rule (the safe one):
 *  - a friend with a known UUID is recognised by the UUID only (a nick can be re-assigned to somebody else later);
 *  - a friend added by nick alone is recognised by the nick until that player is seen in the tab list, then the
 *    UUID is saved and used from then on. Only a real Mojang UUID (version 4) is saved this way: the made-up UUIDs
 *    of offline-mode servers are not, because they would not match on other servers.
 *
 * "In tab list" only says that the player is in the tab list of the server you are on right now. It is not an
 * online status: knowing who is online anywhere else would need the network, which this mod does not use.
 */
public final class FriendList {
    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Friends");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$");

    public static final class Friend {
        public volatile String name = "", uuid = "";
        public volatile boolean inTab;

        public String display() { return !name.isEmpty() ? name : "UUID " + uuid.substring(0, Math.min(8, uuid.length())); }
    }

    private static final List<Friend> FRIENDS = new CopyOnWriteArrayList<>();
    private static volatile Set<String> uuidSet = Set.of(), nameOnlySet = Set.of();
    private static volatile String message = "";
    private static volatile long messageAt, nextTab;

    private FriendList() {}

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("delta_visuals").resolve("friends.json"); }

    // ------------------------------------------------------------------ storage

    public static void init() {
        FRIENDS.clear();
        boolean legacy = false;
        try {
            if (Files.exists(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
                legacy = o.has("secret");     // file of 0.9.6: it held a secret for the removed server; it is dropped on the next save
                if (o.has("friends")) {
                    for (JsonElement e : o.getAsJsonArray("friends")) {
                        try {
                            JsonObject f = e.getAsJsonObject();
                            if (f.has("code") && !f.get("code").getAsString().isEmpty()) legacy = true;
                            Friend fr = new Friend();
                            fr.name = f.has("name") ? f.get("name").getAsString().trim() : "";
                            String u = f.has("uuid") ? f.get("uuid").getAsString().trim() : "";
                            fr.uuid = UUID_RE.matcher(u).matches() ? normalize(u) : "";
                            if (fr.name.isEmpty() && fr.uuid.isEmpty()) continue;   // a code-only friend of 0.9.6 cannot be used locally
                            if (!fr.name.isEmpty() && !NICK.matcher(fr.name).matches()) continue;
                            FRIENDS.add(fr);
                        } catch (Exception ignored) { /* skip a broken entry, keep the rest */ }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Cannot read friends.json", e);
        }
        rebuild();
        if (legacy) {
            save();
            LOG.info("friends.json was written by 0.9.6: the old secret and friend codes were removed from the file");
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            JsonObject o = new JsonObject();
            o.addProperty("version", 2);
            JsonArray a = new JsonArray();
            for (Friend f : FRIENDS) {
                JsonObject j = new JsonObject();
                j.addProperty("name", f.name);
                j.addProperty("uuid", f.uuid);
                a.add(j);
            }
            o.add("friends", a);
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Cannot write friends.json", e);
        }
    }

    private static void rebuild() {
        Set<String> u = new HashSet<>(), n = new HashSet<>();
        for (Friend f : FRIENDS) {
            if (!f.uuid.isEmpty()) u.add(f.uuid);
            else if (!f.name.isEmpty()) n.add(f.name.toLowerCase(Locale.ROOT));
        }
        uuidSet = u;
        nameOnlySet = n;
    }

    private static String normalize(String u) {
        String h = u.replace("-", "").toLowerCase(Locale.ROOT);
        return h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-" + h.substring(16, 20) + "-" + h.substring(20);
    }

    /** True for a real Mojang account UUID (version 4); offline-mode servers make up version 3 UUIDs. */
    private static boolean mojangUuid(String u) {
        try { return UUID.fromString(u).version() == 4; } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------------ queries

    public static List<Friend> list() { return FRIENDS; }
    public static String message() { return System.currentTimeMillis() - messageAt < 6000 ? message : ""; }
    public static void notice(String m) { message = m; messageAt = System.currentTimeMillis(); }

    /** True if this player is on the list. Cheap (set lookups), any thread. */
    public static boolean isFriend(UUID id, String name) {
        if (id != null && uuidSet.contains(id.toString().toLowerCase(Locale.ROOT))) return true;
        return name != null && !nameOnlySet.isEmpty() && nameOnlySet.contains(name.toLowerCase(Locale.ROOT));
    }

    private static Friend byUuid(String u) { for (Friend f : FRIENDS) if (f.uuid.equals(u)) return f; return null; }
    private static Friend byName(String n) { for (Friend f : FRIENDS) if (f.name.equalsIgnoreCase(n)) return f; return null; }

    // ------------------------------------------------------------------ edit

    /** {name, uuid} of the player with this nick or UUID in the current tab list, or null. */
    private static String[] inTabList(MinecraftClient mc, String nick, String uuid) {
        if (mc == null || mc.getNetworkHandler() == null) return null;
        for (Object o : Compat.playerList(mc.getNetworkHandler())) {
            if (!(o instanceof PlayerListEntry e)) continue;
            String n = Compat.profileName(e.getProfile());
            UUID id = Compat.profileId(e.getProfile());
            if (n == null || id == null) continue;
            if ((nick != null && n.equalsIgnoreCase(nick)) || (uuid != null && id.toString().equalsIgnoreCase(uuid)))
                return new String[]{n, id.toString().toLowerCase(Locale.ROOT)};
        }
        return null;
    }

    /** Adds a friend by Minecraft nick or by UUID. The result is shown through {@link #message()}. */
    public static void add(String raw) {
        String in = raw == null ? "" : raw.trim();
        if (in.isEmpty()) { notice("Type a nick or a UUID"); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        Friend f = new Friend();
        if (UUID_RE.matcher(in).matches()) {
            String u = normalize(in);
            if (byUuid(u) != null) { notice("Already in the list"); return; }
            String[] seen = inTabList(mc, null, u);
            f.uuid = u;
            f.name = seen != null ? seen[0] : "";
            FRIENDS.add(f);
            rebuild();
            save();
            notice("Friend added");
        } else if (NICK.matcher(in).matches()) {
            if (byName(in) != null) { notice("Already in the list"); return; }
            String[] seen = inTabList(mc, in, null);
            if (seen != null && byUuid(seen[1]) != null) { notice("Already in the list"); return; }
            f.name = seen != null ? seen[0] : in;
            if (seen != null && mojangUuid(seen[1])) f.uuid = seen[1];
            FRIENDS.add(f);
            rebuild();
            save();
            notice(f.uuid.isEmpty() ? "Added. The UUID is saved when this player is seen in the tab list" : "Friend added");
        } else {
            notice("Not a valid nick or UUID");
        }
    }

    public static void remove(Friend f) {
        FRIENDS.remove(f);
        rebuild();
        save();
        notice("Friend removed");
    }

    // ------------------------------------------------------------------ tab list (local only)

    /** Once a second: marks who is in the tab list and completes nick-only friends with a real UUID. */
    public static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (now < nextTab) return;
        nextTab = now + 1000;
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null || FRIENDS.isEmpty()) { clearState(); return; }
        boolean changed = false;
        for (Friend f : FRIENDS) {
            String[] seen = f.uuid.isEmpty() ? inTabList(mc, f.name, null) : inTabList(mc, null, f.uuid);
            f.inTab = seen != null;
            if (seen == null) continue;
            if (f.uuid.isEmpty() && mojangUuid(seen[1]) && byUuid(seen[1]) == null) { f.uuid = seen[1]; changed = true; }
            if (!f.uuid.isEmpty() && !seen[0].equals(f.name)) { f.name = seen[0]; changed = true; }   // follow a renamed account
        }
        if (changed) { rebuild(); save(); }
    }

    public static void clearState() {
        for (Friend f : FRIENDS) f.inTab = false;
    }
}
