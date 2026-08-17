package com.example.restartcountdown;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final int MAX_REASON_LENGTH = 120;
    private CountdownManager countdownManager;
    private EntryLockManager entryLockManager;
    private MovementLockManager movementLockManager;
    private DailyScheduleManager dailyScheduleManager;
    private RestartAccessManager restartAccessManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        restartAccessManager = new RestartAccessManager(this);
        entryLockManager = new EntryLockManager(this);
        movementLockManager = new MovementLockManager(this);
        countdownManager = new CountdownManager(this, entryLockManager, movementLockManager, restartAccessManager);
        dailyScheduleManager = new DailyScheduleManager(this, countdownManager);

        PluginCommand command = getCommand("restartcountdown");
        if (command == null) {
            getLogger().severe("restartcountdown command is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(countdownManager, this);
        getServer().getPluginManager().registerEvents(entryLockManager, this);
        getServer().getPluginManager().registerEvents(movementLockManager, this);
        getServer().getPluginManager().registerEvents(restartAccessManager, this);
        restartAccessManager.load();
        entryLockManager.loadFromConfig();
        movementLockManager.start();
        dailyScheduleManager.start();
        getLogger().info("RestartCountdown v1.4.0 enabled. Real Spigot restart action is available.");
    }

    @Override
    public void onDisable() {
        if (dailyScheduleManager != null) dailyScheduleManager.stop();
        if (countdownManager != null) countdownManager.stopCountdown();
        if (movementLockManager != null) movementLockManager.stop();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("restartcountdown.use")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { usage(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "restart" -> restart(sender, args);
            case "restartstatus" -> restartStatus(sender);
            case "allow" -> allow(sender, args);
            case "access" -> access(sender, args);
            case "kick" -> kick(sender, args);
            case "tp", "teleport" -> tp(sender, args);
            case "cancel" -> { countdownManager.stopCountdown(); sender.sendMessage("§aカウントダウンを中止しました。"); }
            case "unlock" -> unlock(sender, args);
            case "unfreeze" -> unfreeze(sender, args);
            case "reload" -> {
                reloadConfig();
                restartAccessManager.load();
                entryLockManager.loadFromConfig();
                dailyScheduleManager.restart();
                sender.sendMessage("§a設定を再読み込みしました。");
            }
            default -> legacy(sender, args);
        }
        return true;
    }

    private void restart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c使い方: /rcd restart <秒> <理由...>");
            return;
        }
        Integer seconds = positiveInt(sender, args[1], "秒");
        String reason = join(args, 2);
        if (seconds == null || !validReason(sender, reason)) return;
        if (!isRestartConfigured()) {
            sender.sendMessage("§cSpigotのrestart-scriptが見つからないため、再起動を開始しません。");
            sender.sendMessage("§e/rcd restartstatus で設定を確認してください。");
            return;
        }
        countdownManager.startCountdown(seconds, 0L, reason, CountdownManager.EndAction.RESTART, null, null);
        sender.sendMessage("§a実再起動カウントダウンを開始しました。終了時にSpigot restart()を呼び出します。");
    }

    private void restartStatus(CommandSender sender) {
        String script = restartScript();
        boolean ready = isRestartConfigured();
        sender.sendMessage("§6[RestartCountdown] 再起動状態");
        sender.sendMessage("§7restart-script: §f" + (script.isBlank() ? "未設定" : script));
        sender.sendMessage("§7スクリプトファイル確認: " + (ready ? "§aOK" : "§c見つかりません"));
        sender.sendMessage("§7再起動後アクセス制限: " + (restartAccessManager.isActive() ? "§cON" : "§aOFF"));
        if (!ready) sender.sendMessage("§e安全のためRESTART処理は開始されません。spigot.ymlを設定してください。");
    }

    String restartScript() {
        String script = Bukkit.spigot().getConfig().getString("settings.restart-script", "");
        return script == null ? "" : script.trim();
    }

    boolean isRestartConfigured() {
        String command = restartScript();
        if (command.isBlank()) return false;
        String fileToken = firstCommandToken(command);
        return !fileToken.isBlank() && new File(fileToken).isFile();
    }

    private String firstCommandToken(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return "";
        char first = trimmed.charAt(0);
        if (first == '"' || first == '\'') {
            int closing = trimmed.indexOf(first, 1);
            return closing > 1 ? trimmed.substring(1, closing) : "";
        }
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) end++;
        return trimmed.substring(0, end);
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= 2) return;
        String action = getConfig().getString("scheduled-countdown.action", "KICK");
        boolean legacyDefaultSchedule = "KICK".equalsIgnoreCase(action)
                && getConfig().getBoolean("scheduled-countdown.enabled", true)
                && "Asia/Tokyo".equals(getConfig().getString("scheduled-countdown.timezone", "Asia/Tokyo"))
                && getConfig().getInt("scheduled-countdown.hour", 2) == 2
                && getConfig().getInt("scheduled-countdown.minute", 45) == 45
                && getConfig().getInt("scheduled-countdown.countdown-seconds", 900) == 900
                && getConfig().getLong("scheduled-countdown.post-action-minutes", 0L) == 0L
                && "定期再起動のため".equals(getConfig().getString("scheduled-countdown.reason", "定期再起動のため"));
        if (legacyDefaultSchedule) {
            getConfig().set("scheduled-countdown.action", "RESTART");
            getLogger().info("Migrated the untouched 1.3.0 scheduled countdown default from KICK to RESTART.");
        } else if ("KICK".equalsIgnoreCase(action)) {
            getLogger().info("Preserved customized scheduled-countdown.action=KICK during 1.4.0 migration.");
        }
        if (!getConfig().contains("restart-access.active")) getConfig().set("restart-access.active", false);
        if (!getConfig().contains("restart-access.reason")) getConfig().set("restart-access.reason", "サーバー再起動後のメンテナンス中です");
        if (!getConfig().contains("restart-access.enable-on-restart")) getConfig().set("restart-access.enable-on-restart", true);
        getConfig().set("config-version", 2);
        saveConfig();
    }

    private void allow(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§c使い方: /rcd allow <add|remove|list|clear> [player|UUID]"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                Map<UUID, String> allowed = restartAccessManager.allowedPlayers();
                if (allowed.isEmpty()) sender.sendMessage("§7許可プレイヤーはいません。");
                else {
                    sender.sendMessage("§6再起動後も入場できるプレイヤー:");
                    allowed.forEach((uuid, name) -> sender.sendMessage("§e- §f" + name + " §7(" + uuid + ")"));
                }
            }
            case "clear" -> { restartAccessManager.clear(); sender.sendMessage("§a許可プレイヤーを全削除しました。"); }
            case "add", "remove" -> {
                if (args.length < 3) { sender.sendMessage("§cプレイヤー名またはUUIDを指定してください。"); return; }
                Optional<OfflinePlayer> target = PlayerResolver.resolveKnown(args[2]);
                if (target.isEmpty()) { sender.sendMessage("§cそのプレイヤーはこのサーバーの参加履歴から確認できません。"); return; }
                OfflinePlayer p = target.get();
                if (args[1].equalsIgnoreCase("add")) {
                    restartAccessManager.add(p);
                    sender.sendMessage("§a許可しました: §f" + displayName(p));
                } else {
                    boolean removed = restartAccessManager.remove(p.getUniqueId());
                    sender.sendMessage(removed ? "§a許可を解除しました: §f" + displayName(p) : "§eそのプレイヤーは許可リストにいません。");
                }
            }
            default -> sender.sendMessage("§c使い方: /rcd allow <add|remove|list|clear> [player|UUID]");
        }
    }

    private void access(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage("§7再起動後アクセス制限: " + (restartAccessManager.isActive() ? "§cON" : "§aOFF"));
            sender.sendMessage("§7理由: §f" + restartAccessManager.reason());
            return;
        }
        if (args[1].equalsIgnoreCase("off")) {
            restartAccessManager.deactivate();
            sender.sendMessage("§a再起動後アクセス制限を解除しました。");
            return;
        }
        if (args[1].equalsIgnoreCase("on")) {
            String reason = args.length > 2 ? join(args, 2) : "サーバー再起動後のメンテナンス中です";
            if (!validReason(sender, reason)) return;
            restartAccessManager.activate(reason);
            sender.sendMessage("§a再起動後アクセス制限を有効化しました。");
            return;
        }
        sender.sendMessage("§c使い方: /rcd access <on|off|status> [理由]");
    }

    private void kick(CommandSender sender, String[] args) {
        int offset = targetOffset(args);
        if (args.length < 4 + offset) { sender.sendMessage("§c使い方: /rcd kick [player] <秒> <入場禁止分> <理由...>"); return; }
        Set<UUID> targets = resolveTargets(sender, args, offset);
        if (offset == 1 && targets == null) return;
        Integer seconds = positiveInt(sender, args[1 + offset], "秒");
        Long minutes = nonNegativeLong(sender, args[2 + offset], "入場禁止分");
        String reason = join(args, 3 + offset);
        if (seconds == null || minutes == null || !validReason(sender, reason)) return;
        countdownManager.startCountdown(seconds, minutes, reason, CountdownManager.EndAction.KICK, null, targets);
    }

    private void tp(CommandSender sender, String[] args) {
        int offset = targetOffset(args);
        if (args.length < 8 + offset) { sender.sendMessage("§c使い方: /rcd tp [player] <秒> <移動禁止分> <world> <x> <y> <z> <理由...>"); return; }
        Set<UUID> targets = resolveTargets(sender, args, offset);
        if (offset == 1 && targets == null) return;
        Integer seconds = positiveInt(sender, args[1 + offset], "秒");
        Long minutes = nonNegativeLong(sender, args[2 + offset], "移動禁止分");
        World world = Bukkit.getWorld(args[3 + offset]);
        Double x = number(sender, args[4 + offset], "x");
        Double y = number(sender, args[5 + offset], "y");
        Double z = number(sender, args[6 + offset], "z");
        String reason = join(args, 7 + offset);
        if (seconds == null || minutes == null || world == null || x == null || y == null || z == null || !validReason(sender, reason)) {
            if (world == null) sender.sendMessage("§cワールドが見つかりません。");
            return;
        }
        countdownManager.startCountdown(seconds, minutes, reason, CountdownManager.EndAction.TELEPORT,
                new Location(world, x, y, z), targets);
    }

    private int targetOffset(String[] args) {
        if (args.length < 2) return 0;
        try { Integer.parseInt(args[1]); return 0; } catch (NumberFormatException ignored) { return 1; }
    }

    private Set<UUID> resolveTargets(CommandSender sender, String[] args, int offset) {
        if (offset == 0) return null;
        Optional<OfflinePlayer> target = PlayerResolver.resolveKnown(args[1]);
        if (target.isEmpty()) { sender.sendMessage("§cプレイヤーが見つかりません: " + args[1]); return null; }
        if (!target.get().isOnline()) { sender.sendMessage("§cこのKICK/TP対象は現在オンラインである必要があります。"); return null; }
        return Set.of(target.get().getUniqueId());
    }

    private void unlock(CommandSender sender, String[] args) {
        if (args.length == 1) { entryLockManager.clearAllLocks(); sender.sendMessage("§a入場禁止を全解除しました。"); return; }
        Optional<OfflinePlayer> target = PlayerResolver.resolveKnown(args[1]);
        if (target.isEmpty()) { sender.sendMessage("§cプレイヤーが見つかりません。"); return; }
        sender.sendMessage(entryLockManager.clearPlayerLock(target.get().getUniqueId()) ? "§a個別入場禁止を解除しました。" : "§e個別入場禁止はありません。");
    }

    private void unfreeze(CommandSender sender, String[] args) {
        if (args.length == 1) { int n = movementLockManager.clearAllLocks(); sender.sendMessage("§a移動制限を全解除しました: " + n + "件"); return; }
        Optional<OfflinePlayer> target = PlayerResolver.resolveKnown(args[1]);
        if (target.isEmpty()) { sender.sendMessage("§cプレイヤーが見つかりません。"); return; }
        movementLockManager.clearLock(target.get().getUniqueId());
        sender.sendMessage("§a移動制限を解除しました。");
    }

    private void legacy(CommandSender sender, String[] args) {
        if (args.length != 1) { usage(sender); return; }
        Integer seconds = positiveInt(sender, args[0], "秒");
        if (seconds != null) countdownManager.startCountdown(seconds, 0, "サーバーメンテナンス", CountdownManager.EndAction.KICK, null, null);
    }

    private Integer positiveInt(CommandSender sender, String value, String label) {
        try { int n = Integer.parseInt(value); if (n <= 0) throw new NumberFormatException(); return n; }
        catch (NumberFormatException ex) { sender.sendMessage("§c" + label + "は1以上の整数にしてください。"); return null; }
    }

    private Long nonNegativeLong(CommandSender sender, String value, String label) {
        try { long n = Long.parseLong(value); if (n < 0) throw new NumberFormatException(); Math.multiplyExact(n, 60_000L); return n; }
        catch (RuntimeException ex) { sender.sendMessage("§c" + label + "は0以上の整数にしてください。"); return null; }
    }

    private Double number(CommandSender sender, String value, String label) {
        try { double n = Double.parseDouble(value); if (!Double.isFinite(n)) throw new NumberFormatException(); return n; }
        catch (NumberFormatException ex) { sender.sendMessage("§c" + label + "は有限の数値にしてください。"); return null; }
    }

    private boolean validReason(CommandSender sender, String reason) {
        if (reason.isBlank() || reason.length() > MAX_REASON_LENGTH) { sender.sendMessage("§c理由は1〜" + MAX_REASON_LENGTH + "文字にしてください。"); return false; }
        return true;
    }

    private String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim(); }
    private String displayName(OfflinePlayer p) { return p.getName() == null ? p.getUniqueId().toString() : p.getName(); }

    private void usage(CommandSender sender) {
        sender.sendMessage("§6/rcd restart <秒> <理由...> §7- 実際にSpigotを再起動");
        sender.sendMessage("§6/rcd restartstatus §7- restart-scriptとアクセス制限を確認");
        sender.sendMessage("§6/rcd allow <add|remove|list|clear> [player|UUID] §7- 再起動後の入場許可");
        sender.sendMessage("§6/rcd access <on|off|status> [理由] §7- 再起動後アクセス制限");
        sender.sendMessage("§6/rcd kick [player] <秒> <入場禁止分> <理由...>");
        sender.sendMessage("§6/rcd tp [player] <秒> <移動禁止分> <world> <x> <y> <z> <理由...>");
        sender.sendMessage("§6/rcd unlock [player] / unfreeze [player] / cancel / reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("restartcountdown.use")) return List.of();
        if (args.length == 1) return filter(List.of("restart", "restartstatus", "allow", "access", "kick", "tp", "cancel", "unlock", "unfreeze", "reload", "help"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("allow")) return filter(List.of("add", "remove", "list", "clear"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("access")) return filter(List.of("on", "off", "status"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("allow") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) return playerNames(args[2]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("unfreeze"))) return playerNames(args[1]);
        return List.of();
    }

    private List<String> playerNames(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName)
                .filter(n -> n != null && n.toLowerCase(Locale.ROOT).startsWith(p)).limit(100).toList();
    }

    private List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.startsWith(p)).collect(Collectors.toCollection(ArrayList::new));
    }
}
