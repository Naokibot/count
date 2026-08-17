# RestartCountdown 1.4.0 review

## Original 1.3.0 finding

The supplied 1.3.0 JAR did not perform a server restart. `CountdownManager.EndAction` only contained KICK and TELEPORT; the KICK finish path created entry locks and kicked players but never called Spigot's restart API.

## 1.4.0 changes reviewed

- Added `EndAction.RESTART`.
- RESTART flushes player/world/config state, persists the post-restart access gate, then calls `Bukkit.spigot().restart()`.
- Added `RestartAccessManager`, with UUID-based allow entries stored in config.
- Login control uses `PlayerLoginEvent` so disallowed users are rejected before normal join processing.
- OP and `restartcountdown.access.bypass` are explicit emergency bypasses.
- Allowed-player lookup supports online players, previously seen offline players and known UUIDs; arbitrary unknown names are not converted into synthetic identities.
- The access gate survives a restart because `restart-access.active`, reason and UUID allow entries are written before the restart call.
- Existing KICK and TELEPORT modes remain available.
- Daily scheduling supports RESTART/KICK/TELEPORT.

## External dependency

No Bukkit plugin can resurrect a JVM after the process has exited without cooperation from the server host. Spigot's own `restart()` API delegates restart behavior to the server's restart configuration. If `settings.restart-script` is not correctly configured, Spigot documents that the server will stop. This plugin adds `/rcd restartstatus` and does not claim it can restart an unconfigured host.

## Runtime verification boundary

Source is built against the real Spigot 1.21.1 API in GitHub Actions. A full E2E process-restart test still requires the actual server host plus its `spigot.yml` and startup script, because those external files determine whether the stopped JVM is launched again.
