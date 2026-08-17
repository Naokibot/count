package com.example.restartcountdown;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

final class RestartAccessManager implements Listener {
    private static final String ROOT = "restart-access";
    private final Main plugin;
    private final Map<UUID, String> allowed = new LinkedHashMap<>();
    private boolean active;
    private String reason;

    RestartAccessManager(Main plugin) {
        this.plugin = plugin;
    }

    void load() {
        active = plugin.getConfig().getBoolean(ROOT + ".active", false);
        reason = plugin.getConfig().getString(ROOT + ".reason", "サーバー再起動後のメンテナンス中です");
        allowed.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ROOT + ".allowed-players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                allowed.put(uuid, section.getString(key, uuid.toString()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid restart access UUID in config: " + key);
            }
        }
    }

    boolean isActive() { return active; }
    String reason() { return reason; }
    Map<UUID, String> allowedPlayers() { return Map.copyOf(allowed); }

    void activate(String newReason) {
        active = true;
        if (newReason != null && !newReason.isBlank()) reason = newReason;
        save();
    }

    void deactivate() {
        active = false;
        save();
    }

    void add(OfflinePlayer player) {
        String name = player.getName();
        allowed.put(player.getUniqueId(), name == null ? player.getUniqueId().toString() : name);
        save();
    }

    boolean remove(UUID uuid) {
        boolean changed = allowed.remove(uuid) != null;
        if (changed) save();
        return changed;
    }

    void clear() {
        allowed.clear();
        save();
    }

    boolean isAllowed(UUID uuid) {
        return allowed.containsKey(uuid);
    }

    private void save() {
        plugin.getConfig().set(ROOT + ".active", active);
        plugin.getConfig().set(ROOT + ".reason", reason);
        plugin.getConfig().set(ROOT + ".allowed-players", null);
        for (Map.Entry<UUID, String> entry : allowed.entrySet()) {
            plugin.getConfig().set(ROOT + ".allowed-players." + entry.getKey(), entry.getValue());
        }
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!active) return;
        var player = event.getPlayer();
        if (player.isOp() || player.hasPermission("restartcountdown.access.bypass") || isAllowed(player.getUniqueId())) return;
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                "§c現在、再起動後のメンテナンス中です。\n§7理由: §f" + reason);
    }
}
