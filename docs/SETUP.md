# BlockParty – Setup guide

## 1. Installation

1. Put `BlockParty-1.0.0.jar` in your server's `plugins` folder.
2. Restart the server. A full restart is needed; `/reload` is not enough.
3. The folder `plugins/BlockParty` is created with:
   - `config.yml`: timings, rounds, colors, power-ups, leave item, rewards, music…
   - `messages_en.yml`: every text, in the language set in `config.yml` (`language: en` by default; use `es` for Spanish).
   - `arenas.yml`, `estadisticas.yml` (statistics) and the folders `suelos/` (floor backups) and `jugadores/` (saved player states). The plugin manages them; there is no need to edit them.

Optional plugins:
- **Vault** with an economy plugin (for example EssentialsX): without it no money is given, but everything else works the same.
- **WorldEdit**: to select the floor with the WorldEdit wand.

## 2. How to build the arena

Side view:

```
        [ spectator stand ]            ← outside the floor area, closed
  |                                 |
  |   ←── 4+ blocks of air ──→      |  ← barrier walls (5 blocks high)
  |                                 |
  |#################################|  ← FLOOR (a single layer)
  |                                 |
  |   5+ blocks of air (empty)      |  ← players who fall are eliminated here
  |                                 |
```

### The floor
- **A single flat layer**: every tile at the same height (Y).
- **Size**: at most 64×64 (`suelo.tamano-maximo`). For 16 players, 20×20 to 32×32 works best. Smaller floors are lighter for the server and more exciting.
- **Any block**: when you save the floor, the plugin takes a backup and always puts it back as it was after each game.
- During the game the floor is painted with wool, concrete or terracotta, depending on `suelo.tipo-bloque`.

### Under the floor
- Leave **at least 5 blocks of air** below it. A player is eliminated when they drop **2 blocks** below the floor (`rondas.bloques-de-caida`), and they are teleported to the spectator point right away.
- **Don't put anything solid less than 3 blocks** below the floor: someone could stand on it without reaching the elimination height.
- No lava or void is needed: falling players are teleported before they reach the bottom.

### Walls and ceiling
- Surround the floor with **walls 5 blocks high**, ideally made of **barrier** blocks (invisible) or glass. That way nobody can leave or climb on them with the super jump.
- Leave **4 blocks of free space** above the floor.
- If someone still manages to stay off the floor (on a wall, some stairs…), they are eliminated when the blocks are removed.

### Lobby (waiting room)
- A **separate, closed room**, for example next to the arena or above it. Players wait here until the game starts.
- It needs nothing special: while waiting, players take no damage, don't get hungry and can't break blocks.

### Spectators
- A **closed stand or platform** with a view of the floor, **outside the floor area**. If it is above the floor, give it its own glass floor so nobody falls onto the game floor.
- Eliminated players wait here until the game ends (or until they leave with `/bp leave` or the leave item).

### Start and exit points
- **Start**: on top of the floor, in the middle, 1 block high. Everyone appears here when the game starts.
- **Exit** (optional): where everyone goes after the game or when leaving. Without it, each player goes back to where they were before joining.

### WorldGuard (optional)
The plugin already prevents damage, hunger, PvP, building and dropping items for players in a game, even if a WorldGuard region allows them. Still, a region over the whole arena with `build deny` is a good idea, so nobody from outside can damage it.

## 3. Setup commands, step by step

Example with an arena called `party`:

1. `/bp create party`
2. Mark two **opposite** corners of the floor, at the same height:
   - Look at the first corner: `/bp pos1`
   - Look at the opposite corner: `/bp pos2`
   - With WorldEdit you can select the floor with the wand (`//wand`) instead.
3. `/bp setfloor party`. Saves the floor and takes the backup.
4. Stand in the lobby: `/bp setlobby party`
5. Stand on the middle of the floor: `/bp setspawn party`
6. Stand in the spectator stand: `/bp setspectator party`
7. (Optional) Stand at the exit: `/bp setexit party`
8. `/bp enable party`. If something is missing, it tells you exactly what.
9. Try it: join with `/bp join party` and, if you are alone, `/bp start party` to start without waiting for more players.

To change something later:
- **The floor:** mark it again and repeat `/bp setfloor`. The arena can't be in a game.
- **A point:** repeat its command at the new spot.

Other admin commands:

| Command | What it does |
|---|---|
| `/bp disable <arena>` | Disable the arena (stops the game if there is one) |
| `/bp start <arena>` | Start now, without the countdown or the minimum players |
| `/bp stop <arena>` | Stop the game (no winners, no rewards) |
| `/bp delete <arena>` | Delete the arena |
| `/bp reload` | Reload `config.yml` and the messages |

Player commands: `/bp join <arena>`, `/bp leave`, `/bp list`, `/bp stats [player]`.

### Moderators
Give `blockparty.moderar` to let someone start and stop games (`/bp start`, `/bp stop`) without being able to set up or delete arenas.

## 4. Signs

1. Place a sign (only admins can create them).
2. Write:
   - Line 1: `[BlockParty]`
   - Line 2: the arena name, for example `party`
3. The sign changes by itself and shows the state (**Waiting / Starting / In game / Disabled**) and the players (`3/16`).
4. Players join by **clicking** the sign.
5. To remove it: break it **while sneaking**. Only admins can break it.

Tip: **wax the sign with honeycomb** after creating it, so nobody can edit its text.

## 5. Useful settings (config.yml)

The setting names are in Spanish; the comments in `config.yml` explain each one in English.

| Setting | What it does |
|---|---|
| `language` | `en` or `es` (or any code, see the README) |
| `jugadores.minimo` / `maximo` | Minimum players to start and maximum per arena |
| `sala-espera.cuenta-atras` / `cuenta-atras-llena` | Countdown: 30 s, or 10 s when the arena is full |
| `rondas.maximo` | Maximum rounds (if several players reach it, they all win) |
| `rondas.musica-*` / `reaccion-*` / `rondas-hasta-minimo` | Increasing difficulty |
| `suelo.tipo-bloque` | `WOOL`, `CONCRETE` or `TERRACOTTA` |
| `suelo.colores` | Color palette and the names that are called out |
| `suelo.patrones` | Chance of each pattern |
| `suelo.bloques-por-tick` | Lower it (for example to 512) if you notice lag spikes while the floor is painted |
| `potenciadores` | Power-ups: on/off, chance and each type |
| `objeto-salir` | Leave item in the hotbar (a barrier in the last slot by default): right click = `/bp leave`. It can be disabled or moved to another item or slot. Its name and description are in the messages file (`objeto-salir-nombre`) |
| `comandos-permitidos` | Commands allowed inside a game |
| `recompensas` | Money (winner 50, taking part 5) and console commands with `{player}` |
| `musica.melodias` | Add your own melodies with the same format |

After changing anything: `/bp reload`.

## 6. If something goes wrong

| Situation | What the plugin does |
|---|---|
| A player disconnects in the middle of a game | They leave it. When they join again they get back their inventory, health, experience, effects and game mode, and appear at the exit (or where they were before joining). |
| The server stops or restarts in the middle of a game | The floor is restored right away and everyone gets their things back. |
| The server crashes | On the next start the floor is restored, and each player gets their things back when they join. |
