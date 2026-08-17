package com.example.restartcountdown;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

final class PlayerResolver {
    private PlayerResolver() { }

    static Optional<OfflinePlayer> resolveKnown(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String token = input.trim();
        try {
            UUID uuid = UUID.fromString(token);
            OfflinePlayer byId = Bukkit.getOfflinePlayer(uuid);
            if (byId.isOnline() || byId.hasPlayedBefore() || byId.getName() != null) return Optional.of(byId);
        } catch (IllegalArgumentException ignored) {
            // Name lookup below.
        }
        Player online = Bukkit.getPlayerExact(token);
        if (online != null) return Optional.of(online);
        String needle = token.toLowerCase(Locale.ROOT);
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).equals(needle)) return Optional.of(player);
        }
        return Optional.empty();
    }
}
