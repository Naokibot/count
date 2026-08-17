package com.example.restartcountdown;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

final class CountdownManager implements Listener {
    enum EndAction { KICK, TELEPORT, RESTART }

    private final Main plugin;
    private final EntryLockManager entryLockManager;
    private final MovementLockManager movementLockManager;
    private final RestartAccessManager restartAccessManager;
    private BossBar bossBar;
    private BukkitTask countdownTask;
    private int initialSeconds;
    private int remainingSeconds;
    private long postActionMinutes;
    private String reason;
    private EndAction endAction;
    private Location teleportDestination;
    private Set<UUID> targetPlayerIds;

    CountdownManager(Main plugin, EntryLockManager entryLockManager,
                     MovementLockManager movementLockManager, RestartAccessManager restartAccessManager) {
        this.plugin = plugin;
        this.entryLockManager = entryLockManager;
        this.movementLockManager = movementLockManager;
        this.restartAccessManager = restartAccessManager;
    }

    void startCountdown(int seconds, long postActionMinutes, String reason, EndAction action,
                        Location teleportDestination, Set<UUID> targets) {
        if (seconds <= 0) throw new IllegalArgumentException("seconds must be positive");
        stopCountdown();
        this.initialSeconds = seconds;
        this.remainingSeconds = seconds;
        this.postActionMinutes = postActionMinutes;
        this.reason = reason;
        this.endAction = action;
        this.teleportDestination = teleportDestination == null ? null : teleportDestination.clone();
        this.targetPlayerIds = targets == null ? null : Set.copyOf(targets);
        this.bossBar = Bukkit.createBossBar(title(), action == EndAction.RESTART ? BarColor.RED : BarColor.YELLOW, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) if (isTarget(player)) bossBar.addPlayer(player);
        updateBossBar();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        remainingSeconds--;
        if (remainingSeconds <= 0) { finishCountdown(); return; }
        updateBossBar();
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        bossBar.setTitle(title());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, remainingSeconds / (double) initialSeconds)));
    }

    private String title() {
        String action = switch (endAction) {
            case RESTART -> "再起動";
            case KICK -> "メンテナンス";
            case TELEPORT -> "移動";
        };
        return "§c" + action + "まで §f" + formatTime(remainingSeconds) + " §7- " + reason;
    }

    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return min > 0 ? String.format("%d:%02d", min, sec) : sec + "秒";
    }

    private void finishCountdown() {
        cancelTask();
        try {
            switch (endAction) {
                case KICK -> finishKickAction();
                case TELEPORT -> finishTeleportAction();
                case RESTART -> finishRestartAction();
            }
        } finally {
            removeBossBar();
            resetState();
        }
    }

    private void finishKickAction() {
        if (targetPlayerIds == null) entryLockManager.startGlobalLock(postActionMinutes, reason);
        else for (UUID uuid : targetPlayerIds) entryLockManager.startPlayerLock(uuid, postActionMinutes, reason);
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (!player.isOp() && isTarget(player)) player.kickPlayer("§cサーバーメンテナンスのため切断されました。\n§7" + reason);
        }
    }

    private void finishTeleportAction() {
        if (teleportDestination == null) return;
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (!isTarget(player) || player.isOp()) continue;
            movementLockManager.startLock(player, teleportDestination, postActionMinutes, reason);
        }
    }

    private void finishRestartAction() {
        if (!plugin.isRestartConfigured()) {
            Bukkit.broadcastMessage("§c再起動を中止しました。Spigotのrestart-scriptが見つかりません。");
            plugin.getLogger().severe("Restart aborted: configured restart-script is missing: " + plugin.restartScript());
            return;
        }
        if (plugin.getConfig().getBoolean("restart-access.enable-on-restart", true)) restartAccessManager.activate(reason);
        Bukkit.savePlayers();
        for (var world : Bukkit.getWorlds()) world.save();
        plugin.saveConfig();
        Bukkit.broadcastMessage("§cサーバーを再起動します。");
        Bukkit.spigot().restart();
    }

    void stopCountdown() {
        cancelTask();
        removeBossBar();
        resetState();
    }

    private void cancelTask() { if (countdownTask != null) countdownTask.cancel(); countdownTask = null; }
    private void removeBossBar() { if (bossBar != null) bossBar.removeAll(); bossBar = null; }

    private void resetState() {
        initialSeconds = 0;
        remainingSeconds = 0;
        postActionMinutes = 0;
        reason = null;
        endAction = null;
        teleportDestination = null;
        targetPlayerIds = null;
    }

    boolean isRunning() { return countdownTask != null; }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isRunning() && bossBar != null && isTarget(event.getPlayer())) bossBar.addPlayer(event.getPlayer());
    }

    private boolean isTarget(Player player) { return targetPlayerIds == null || targetPlayerIds.contains(player.getUniqueId()); }
}
