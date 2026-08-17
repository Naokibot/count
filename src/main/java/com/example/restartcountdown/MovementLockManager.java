package com.example.restartcountdown;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

final class MovementLockManager implements Listener {
    private final Main plugin;
    private final Map<UUID, MovementLock> locks = new HashMap<>();
    private final Set<UUID> teleportBypass = new HashSet<>();
    private BukkitTask cleanupTask;

    MovementLockManager(Main plugin) { this.plugin = plugin; }

    void start() {
        loadAll();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::removeExpiredLocks, 20L, 20L);
    }

    void stop() {
        if (cleanupTask != null) cleanupTask.cancel();
        cleanupTask = null;
        saveAll();
    }

    void startLock(Player player, Location anchor, long minutes, String reason) {
        clearLock(player.getUniqueId(), false);
        if (minutes <= 0) { saveAll(); return; }
        long until = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(minutes, 60_000L));
        Location normalized = anchor.clone();
        locks.put(player.getUniqueId(), new MovementLock(until, normalized, reason));
        teleportWithBypass(player, normalized);
        saveAll();
    }

    void clearLock(UUID uuid) { clearLock(uuid, true); }
    private void clearLock(UUID uuid, boolean save) { locks.remove(uuid); teleportBypass.remove(uuid); if (save) saveAll(); }
    int clearAllLocks() { int size = locks.size(); locks.clear(); teleportBypass.clear(); saveAll(); return size; }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        MovementLock lock = active(event.getPlayer());
        if (lock == null) return;
        Location to = event.getTo();
        Location anchor = lock.anchor();
        if (sameBlock(to, anchor)) return;
        Location adjusted = anchor.clone();
        adjusted.setYaw(to.getYaw());
        adjusted.setPitch(to.getPitch());
        event.setTo(adjusted);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (teleportBypass.remove(id)) return;
        MovementLock lock = active(event.getPlayer());
        if (lock != null && event.getTo() != null && !sameBlock(event.getTo(), lock.anchor())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c現在は移動が制限されています。");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MovementLock lock = active(event.getPlayer());
        if (lock != null) teleportWithBypass(event.getPlayer(), lock.anchor());
    }

    private void teleportWithBypass(Player player, Location location) {
        teleportBypass.add(player.getUniqueId());
        player.teleport(location);
    }

    private MovementLock active(Player player) {
        MovementLock lock = locks.get(player.getUniqueId());
        if (lock != null && lock.until() <= System.currentTimeMillis()) { clearLock(player.getUniqueId()); return null; }
        return lock;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void removeExpiredLocks() {
        long now = System.currentTimeMillis();
        if (locks.entrySet().removeIf(e -> e.getValue().until() <= now)) saveAll();
    }

    private void loadAll() {
        locks.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("movement-locks");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String base = key + ".";
                long until = section.getLong(base + "until", 0L);
                String worldName = section.getString(base + "world");
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (until <= System.currentTimeMillis() || world == null) continue;
                Location loc = new Location(world, section.getDouble(base + "x"), section.getDouble(base + "y"), section.getDouble(base + "z"),
                        (float) section.getDouble(base + "yaw"), (float) section.getDouble(base + "pitch"));
                locks.put(uuid, new MovementLock(until, loc, section.getString(base + "reason", "メンテナンス")));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid movement-lock UUID: " + key);
            }
        }
    }

    private void saveAll() {
        plugin.getConfig().set("movement-locks", null);
        for (var e : locks.entrySet()) {
            String p = "movement-locks." + e.getKey();
            MovementLock l = e.getValue();
            plugin.getConfig().set(p + ".until", l.until());
            plugin.getConfig().set(p + ".world", l.anchor().getWorld().getName());
            plugin.getConfig().set(p + ".x", l.anchor().getX());
            plugin.getConfig().set(p + ".y", l.anchor().getY());
            plugin.getConfig().set(p + ".z", l.anchor().getZ());
            plugin.getConfig().set(p + ".yaw", l.anchor().getYaw());
            plugin.getConfig().set(p + ".pitch", l.anchor().getPitch());
            plugin.getConfig().set(p + ".reason", l.reason());
        }
        plugin.saveConfig();
    }

    private record MovementLock(long until, Location anchor, String reason) { }
}
