# BlockParty

A Block Party minigame for **Paper**. Music plays while everyone dances on a colorful floor. When the music stops, a color is called out, and every other color disappears. Anyone not standing on the right color falls and is out. The last player standing wins.

## Features

- **Rounds that get harder**: the music and the time to reach the color both get shorter round by round.
- **Five floor patterns**: mixed random tiles (1x1, 2x2, 3x3), stripes, rings, checkerboard and diagonals, each with a configurable chance.
- **Note block music**: four built-in melodies, and you can add your own in `config.yml`.
- **Power-ups** that appear on the floor: speed, super jump, head start (see the next color 1 second early), blindness for everyone else and an extra block that saves you once.
- **Several arenas** running at the same time, each with its own scoreboard, music and colors.
- **Join signs** that show the arena state and player count live.
- **Leave item**: a barrier in the hotbar; right click it to leave the game.
- **Players' things are always safe.** Inventory, armor, health, food, experience, effects and game mode are saved to disk on join and given back on leave. This also holds after a disconnect, a restart or a crash.
- **The floor is always restored.** A backup is taken when the floor is saved, and it is put back after every game, even after a crash.
- **Protections inside the arena**: no damage, no hunger, no PvP, no building, no item drops. Teleport commands like `/spawn` or `/home` are blocked, and you choose which commands are allowed.
- **Rewards**: money for the winner(s) and for taking part (needs Vault), plus console commands.
- **Statistics**: games played, wins and best round per player (`/bp stats`).
- **Lightweight**: the floor is painted a few blocks per tick, and no task runs while an arena is idle.
- **English and Spanish** messages included. Any other language can be added by translating one file.

## Requirements

| | |
|---|---|
| Server | **Paper 26.1.x** |
| Java | **25** |
| Optional | **Vault** + an economy plugin (for money rewards), **WorldEdit** (to select the floor with the wand) |

## Installation

1. Download `BlockParty-1.0.0.jar` and put it in your server's `plugins` folder.
2. Restart the server. A full restart is needed; `/reload` is not enough.
3. Build an arena and set it up. See **[docs/SETUP.md](docs/SETUP.md)**.

Quick setup, for an arena called `party`:

```
/bp create party
/bp pos1                  (looking at one corner of the floor)
/bp pos2                  (looking at the opposite corner, same height)
/bp setfloor party
/bp setlobby party        (standing in the waiting room)
/bp setspawn party        (standing on the middle of the floor)
/bp setspectator party    (standing in the spectator stand)
/bp setexit party         (optional: where players go when they leave)
/bp enable party
```

## Commands

Alias: `/bp` = `/blockparty`.

| Command | Description | Permission |
|---|---|---|
| `/bp join <arena>` | Join an arena | `blockparty.play` |
| `/bp leave` | Leave the game (or right click the leave item) | `blockparty.play` |
| `/bp list` | List the arenas and their state | `blockparty.play` |
| `/bp stats [player]` | Games played, wins and best round | `blockparty.play` |
| `/bp create <arena>` / `/bp delete <arena>` | Create or delete an arena | `blockparty.admin` |
| `/bp pos1` / `/bp pos2` | Mark the floor corners (the block you are looking at) | `blockparty.admin` |
| `/bp setfloor <arena>` | Save the floor (pos1/pos2 or the WorldEdit selection) | `blockparty.admin` |
| `/bp setlobby\|setspawn\|setspectator\|setexit <arena>` | Set a point where you stand | `blockparty.admin` |
| `/bp enable\|disable <arena>` | Enable or disable an arena | `blockparty.admin` |
| `/bp start <arena>` | Start now, without waiting for the countdown | `blockparty.admin` or `blockparty.moderar` |
| `/bp stop <arena>` | Stop the game (no winners, no rewards) | `blockparty.admin` or `blockparty.moderar` |
| `/bp reload` | Reload `config.yml` and the messages | `blockparty.admin` |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `blockparty.play` | everyone | Play: join, leave, list, stats, and join signs |
| `blockparty.admin` | op | Set up arenas, place and remove signs, everything else |
| `blockparty.moderar` | nobody | Start and stop games without being able to set up arenas (for moderators) |
| `blockparty.bypass.comandos` | nobody | Use any command while inside a game |

## Signs

Place a sign with `[BlockParty]` on the first line and the arena name on the second. It updates by itself (Waiting / Starting / In game / Disabled, and `3/16` players), and players join by clicking it. Admins remove it by breaking it while sneaking.

## Configuration

- `plugins/BlockParty/config.yml`: players, countdown, rounds and difficulty, floor colors and patterns, power-ups, leave item, allowed commands, rewards, sounds and melodies. Every setting is commented.
- `plugins/BlockParty/messages_<language>.yml`: every text, with `&` color codes and hex colors (`&#FF8800`).

Language: set `language: en` or `language: es` in `config.yml`. For another language (for example `fr`), set `language: fr` and restart or run `/bp reload`. BlockParty creates `messages_fr.yml` as a copy of the English file, ready to translate.

> The setting names in `config.yml` are in Spanish (for example `jugadores.minimo` = minimum players). The comments next to each one explain it in English.

After changing anything: `/bp reload`.

## Building from source

Requires **JDK 25** and **Maven**:

```
mvn package
```

The plugin jar is created at `target/BlockParty-1.0.0.jar`.

## Documentation

- **[docs/SETUP.md](docs/SETUP.md)**: how to build an arena, step-by-step setup, signs, useful settings and what happens when something goes wrong.
- **[docs/TESTING.md](docs/TESTING.md)**: a checklist to test everything on your server before opening it to players.

## License

[MIT](LICENSE)
