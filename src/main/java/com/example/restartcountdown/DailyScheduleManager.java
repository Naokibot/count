package com.example.restartcountdown;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

final class DailyScheduleManager {
    private final Main plugin;
    private final CountdownManager countdownManager;
    private BukkitTask scheduledTask;

    DailyScheduleManager(Main plugin, CountdownManager countdownManager) {
        this.plugin = plugin;
        this.countdownManager = countdownManager;
    }

    void start() {
        stop();
        ScheduleSettings settings = readSettings();
        if (settings == null || !settings.enabled()) return;
        ZonedDateTime now = ZonedDateTime.now(settings.zone());
        ZonedDateTime next = now.with(settings.time());
        if (!next.isAfter(now)) next = next.plusDays(1);
        scheduleAt(settings, next);
    }

    void stop() { if (scheduledTask != null) scheduledTask.cancel(); scheduledTask = null; }
    void restart() { start(); }

    private void scheduleAt(ScheduleSettings settings, ZonedDateTime next) {
        long millis = Math.max(0L, Duration.between(ZonedDateTime.now(settings.zone()), next).toMillis());
        long ticks = Math.max(1L, (millis + 49L) / 50L);
        scheduledTask = Bukkit.getScheduler().runTaskLater(plugin, () -> { startScheduledCountdown(settings); start(); }, ticks);
        plugin.getLogger().info("Next scheduled countdown: " + next);
    }

    private void startScheduledCountdown(ScheduleSettings settings) {
        if (countdownManager.isRunning()) {
            plugin.getLogger().warning("Scheduled countdown skipped because another countdown is running.");
            return;
        }
        Location destination = settings.action() == CountdownManager.EndAction.TELEPORT ? readTeleportDestination() : null;
        if (settings.action() == CountdownManager.EndAction.TELEPORT && destination == null) return;
        countdownManager.startCountdown(settings.countdownSeconds(), settings.postActionMinutes(), settings.reason(),
                settings.action(), destination, null);
    }

    private ScheduleSettings readSettings() {
        try {
            boolean enabled = plugin.getConfig().getBoolean("scheduled-countdown.enabled", true);
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("scheduled-countdown.timezone", "Asia/Tokyo"));
            LocalTime time = LocalTime.of(plugin.getConfig().getInt("scheduled-countdown.hour", 2),
                    plugin.getConfig().getInt("scheduled-countdown.minute", 45));
            int seconds = plugin.getConfig().getInt("scheduled-countdown.countdown-seconds", 900);
            long post = plugin.getConfig().getLong("scheduled-countdown.post-action-minutes", 0L);
            if (seconds <= 0 || post < 0) throw new IllegalArgumentException("invalid scheduled countdown duration");
            String reason = plugin.getConfig().getString("scheduled-countdown.reason", "定期再起動のため");
            String raw = plugin.getConfig().getString("scheduled-countdown.action", "RESTART");
            CountdownManager.EndAction action = CountdownManager.EndAction.valueOf(raw.toUpperCase(Locale.ROOT));
            return new ScheduleSettings(enabled, zone, time, seconds, post, reason, action);
        } catch (DateTimeException | IllegalArgumentException ex) {
            plugin.getLogger().severe("Invalid scheduled-countdown config: " + ex.getMessage());
            return null;
        }
    }

    private Location readTeleportDestination() {
        String worldName = plugin.getConfig().getString("scheduled-countdown.teleport.world", "world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Scheduled teleport world is not loaded: " + worldName);
            return null;
        }
        double x = plugin.getConfig().getDouble("scheduled-countdown.teleport.x", 0.0);
        double y = plugin.getConfig().getDouble("scheduled-countdown.teleport.y", 100.0);
        double z = plugin.getConfig().getDouble("scheduled-countdown.teleport.z", 0.0);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new Location(world, x, y, z);
    }

    private record ScheduleSettings(boolean enabled, ZoneId zone, LocalTime time, int countdownSeconds,
                                    long postActionMinutes, String reason, CountdownManager.EndAction action) { }
}
