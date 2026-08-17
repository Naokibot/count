# RestartCountdown 1.4.0

Spigot 1.21.1 / Java 21 plugin.

## 1.4.0

- Adds a real `RESTART` end action.
- `/rcd restart <seconds> <reason...>` calls Spigot's official `Bukkit.spigot().restart()` after the boss-bar countdown.
- The daily schedule supports `action: RESTART` and defaults to it.
- Adds a persistent post-restart access gate.
- `/rcd allow add <player|UUID>` chooses players who may join after restart.
- Allowed players are stored by UUID and survive restart.
- `/rcd access off` reopens the server to everyone.
- OPs and `restartcountdown.access.bypass` users can always join for emergency administration.
- `/rcd restartstatus` shows the configured Spigot restart script and access-gate state.

## Important

The plugin invokes the real Spigot restart API. Spigot itself requires `settings.restart-script` in `spigot.yml` to point to a valid startup script. If restarting is not configured, Spigot documents that `restart()` will stop the server instead of starting a replacement process.

Use `/rcd restartstatus` before relying on automatic restarts.

## Commands

- `/rcd restart <seconds> <reason...>`
- `/rcd restartstatus`
- `/rcd allow add <player|UUID>`
- `/rcd allow remove <player|UUID>`
- `/rcd allow list`
- `/rcd allow clear`
- `/rcd access on [reason]`
- `/rcd access off`
- `/rcd access status`
- Existing: `kick`, `tp`, `cancel`, `unlock`, `unfreeze`, `reload`

## Maintenance flow

1. `/rcd allow add Alice`
2. `/rcd allow add Bob`
3. `/rcd allow list`
4. `/rcd restart 60 Plugin update`
5. After the server comes back, only allowed players and bypass users can enter.
6. `/rcd access off` when maintenance is finished.
