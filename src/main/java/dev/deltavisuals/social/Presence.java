package dev.deltavisuals.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who else uses Delta Visuals: a MANUAL list of nicks, kept on this computer only.
 *
 * Earlier versions asked a presence server about the players in the tab list. That network part is removed: this class
 * opens no connection and sends nothing anywhere. The Delta logo is shown next to the nicks you add yourself with
 * /delta user add NICK.
 *
 * File: .minecraft/config/delta_visuals/delta_users.json (a JSON array of nicks).
 */
public final class Presence {
    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Presence");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> USERS = ConcurrentHashMap.newKeySet();   // lower-case nicks

    private Presence() {}

    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("delta_visuals"); }

    public static void init() {
        USERS.clear();
        try {
            Path f = dir().resolve("delta_users.json");
            if (!Files.exists(f)) return;
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (e.isJsonArray()) for (JsonElement n : e.getAsJsonArray()) USERS.add(n.getAsString().toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            LOG.warn("Cannot read delta_users.json", ex);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(dir());
            JsonArray a = new JsonArray();
            for (String n : new TreeSet<>(USERS)) a.add(n);
            Files.writeString(dir().resolve("delta_users.json"), GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Cannot write delta_users.json", e);
        }
    }

    public static boolean addFriend(String nick) {
        boolean added = USERS.add(nick.toLowerCase(Locale.ROOT));
        if (added) save();
        return added;
    }

    public static boolean removeFriend(String nick) {
        boolean removed = USERS.remove(nick.toLowerCase(Locale.ROOT));
        if (removed) save();
        return removed;
    }

    public static List<String> friends() { return new ArrayList<>(new TreeSet<>(USERS)); }

    /** True if this nick is on the manual list. Cheap, any thread. The UUID is not used (nothing is looked up). */
    public static boolean isUser(UUID id, String name) {
        return name != null && !USERS.isEmpty() && USERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
