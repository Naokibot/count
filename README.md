# RestartCountdown 1.4.0

Spigot 1.21.1 / Java 21 plugin.

## New in 1.4.0

- Adds a real `RESTART` end action.
- `/rcd restart <seconds> <reason...>` calls Spigot's official `Bukkit.spigot().restart()` after the boss-bar countdown.
- The daily schedule now supports `action: RESTART` and defaults to it.
- Adds a persistent post-restart access gate.
- `/rcd allow add <player|UUID>` chooses players who may join after restart.
- Allowed players are stored by UUID and survive restart.
- `/rcd access off` reopens the server to everyone.
- OPs and `restartcountdown.access.bypass` users can always join for emergency administration.
- `/rcd restartstatus` shows the configured Spigot restart script and access-gate state.
- Manual and scheduled RESTART actions are refused if the configured restart script file cannot be found, preventing an accidental stop-only operation.
- When upgrading an untouched 1.3.0 default schedule, `action: KICK` is migrated to `RESTART`; customized KICK schedules are preserved.

## Important: what "real restart" means on Spigot

The plugin now invokes the real Spigot restart API. Spigot itself requires `settings.restart-script` in `spigot.yml` to point to a valid startup script. If the administrator has not configured restarting, Spigot documents that `restart()` will stop the server instead of starting a replacement process.

Typical examples:

Linux:
```yaml
settings:
  restart-script: ./start.sh
```

Windows (when your Spigot setup accepts the batch file path):
```yaml
settings:
  restart-script: start.bat
```

Use `/rcd restartstatus` before relying on automatic restarts. If the script is missing, 1.4.0 aborts the RESTART action rather than calling `restart()`.

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

## Safe maintenance flow

1. Add staff: `/rcd allow add Alice`
2. Add another staff member: `/rcd allow add Bob`
3. Check: `/rcd allow list`
4. Start: `/rcd restart 60 Plugin update`
5. After the server comes back, only allowed players and bypass users can enter.
6. When maintenance is done: `/rcd access off`
