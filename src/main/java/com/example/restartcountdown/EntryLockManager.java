package com.example.restartcountdown;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

final class EntryLockManager implements Listener {
    private final Main plugin;
    private long globalUntil;
    private String globalReason = "サーバーメンテナンスのため";
    private final Map<UUID, LockData> playerLocks = new HashMap<>();

    EntryLockManager(Main plugin) { this.plugin = plugin; }

    void loadFromConfig() {
        globalUntil = plugin.getConfig().getLong("entry-lock.until-epoch-millis", 0L);
        globalReason = plugin.getConfig().getString("entry-lock.reason", "サーバーメンテナンスのため");
        playerLocks.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("player-entry-locks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long until = section.getLong(key + ".until-epoch-millis", 0L);
                    String reason = section.getString(key + ".reason", "サーバーメンテナンスのため");
                    if (until > System.currentTimeMillis()) playerLocks.put(uuid, new LockData(until, reason));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid player entry lock UUID: " + key);
                }
            }
        }
        cleanupExpired();
    }

    void startGlobalLock(long minutes, String reason) {
        if (minutes <= 0) { clearGlobalLock(); return; }
        globalUntil = expiry(minutes);
        globalReason = reason;
        saveState();
    }

    void startPlayerLock(UUID uuid, long minutes, String reason) {
        if (minutes <= 0) { clearPlayerLock(uuid); return; }
        playerLocks.put(uuid, new LockData(expiry(minutes), reason));
        saveState();
    }

    void clearGlobalLock() { globalUntil = 0L; saveState(); }
    boolean clearPlayerLock(UUID uuid) { boolean r = playerLocks.remove(uuid) != null; if (r) saveState(); return r; }
    void clearAllLocks() { globalUntil = 0L; playerLocks.clear(); saveState(); }

    private long expiry(long minutes) {
        return Math.addExact(System.currentTimeMillis(), Math.multiplyExact(minutes, 60_000L));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        cleanupExpired();
        if (event.getPlayer().isOp()) return;
        long now = System.currentTimeMillis();
        if (globalUntil > now) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message(globalUntil, globalReason));
            return;
        }
        LockData lock = playerLocks.get(event.getPlayer().getUniqueId());
        if (lock != null && lock.until() > now) event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message(lock.until(), lock.reason()));
    }

    private String message(long until, String reason) {
        long seconds = Math.max(0L, Duration.ofMillis(until - System.currentTimeMillis()).toSeconds());
        return "§c現在サーバーへ入れません。\n§7理由: §f" + reason + "\n§7残り: §f" + format(seconds);
    }

    private String format(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return m + "分" + s + "秒";
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        if (globalUntil != 0 && globalUntil <= now) { globalUntil = 0; changed = true; }
        changed |= playerLocks.entrySet().removeIf(e -> e.getValue().until() <= now);
        if (changed) saveState();
    }

    private void saveState() {
        plugin.getConfig().set("entry-lock.until-epoch-millis", globalUntil);
        plugin.getConfig().set("entry-lock.reason", globalReason);
        plugin.getConfig().set("player-entry-locks", null);
        for (var e : playerLocks.entrySet()) {
            String p = "player-entry-locks." + e.getKey();
            plugin.getConfig().set(p + ".until-epoch-millis", e.getValue().until());
            plugin.getConfig().set(p + ".reason", e.getValue().reason());
        }
        plugin.saveConfig();
    }

    private record LockData(long until, String reason) { }
}
