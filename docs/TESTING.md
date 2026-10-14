# BlockParty – Testing checklist

Tick each test as you do it. Games need **2 players**: a friend or a second account.

**To test faster**, temporarily set in `config.yml`:
- `rondas.maximo: 3`
- `sala-espera.cuenta-atras: 10`
- `potenciadores.probabilidad: 100`

Then run `/bp reload`, and put the values back when you are done.

## A. Installation and setup
- [ ] The server starts and the console shows `[BlockParty] … arena(s) loaded.`, with no errors.
- [ ] `/bp create test`, `/bp pos1`, `/bp pos2` and `/bp setfloor test` → message with the floor size.
- [ ] `/bp setfloor` with the corners at different heights → tells you they must be at the same height.
- [ ] A floor bigger than 64×64 → rejected.
- [ ] With WorldEdit: select with `//wand` (no pos1/pos2) and `/bp setfloor test` → works.
- [ ] `/bp enable test` before setting the points → says what is missing.
- [ ] `setlobby`, `setspawn`, `setspectator` and `setexit`, then `enable` → "ready to play".
- [ ] Tab completion suggests subcommands and arena names.

## B. Signs
- [ ] An admin writes `[BlockParty]` / `test` → the sign changes and shows "Waiting 0/16".
- [ ] A player without permission tries to create one → can't.
- [ ] Click the sign → you join. The sign goes to 1/16.
- [ ] When the game starts the sign shows "In game"; when it ends it goes back to "Waiting".
- [ ] `/bp disable test` → "Disabled". Breaking it while sneaking (admin) removes it.

## C. Joining and leaving
- [ ] Join with items in your inventory, low health, some hunger, experience, a potion effect, and in survival mode.
- [ ] On joining: empty inventory except the **"Leave" barrier** in the last hotbar slot, adventure mode, full health and food, in the lobby.
- [ ] Right click with the barrier in hand (looking at the air and looking at a block) → you leave just like with `/bp leave` and get your things back; the barrier does not stay in your inventory.
- [ ] The barrier can't be dropped (Q), moved in the inventory or swapped to the off hand (F), and it can't be placed.
- [ ] The sidebar scoreboard appears (Arena, Players, Minimum).
- [ ] `/bp leave` → you get back **exactly** what you had (items, armor, off hand, health, food, experience, effects, game mode) and go to the exit (or where you were).
- [ ] In the lobby: `/spawn`, `/home` and `/tpa` are blocked; `/msg` and `/bp leave` work.
- [ ] You can't break or place blocks, drop items, deal damage or take it.

## D. Countdown
- [ ] With 1 player: "1 more player(s) needed to start".
- [ ] When the 2nd joins: the 30 s countdown starts, with a boss bar and big numbers in the last 5 seconds.
- [ ] If one leaves during the countdown → it is cancelled.
- [ ] With the arena full (set `maximo: 2` to test it) → drops to 10 s.

## E. The game
- [ ] At the start everyone appears at the start point and the floor is painted with a color pattern.
- [ ] A note block melody plays; the bar says "♪ Round 1 ♪".
- [ ] When it stops: the bell rings, the color is called out in big letters ("RED!" or whichever) in its color, the bar counts down and the hotbar fills with that color's block, **except the last slot, where the barrier stays**.
- [ ] During the game and once eliminated (in the stand), the barrier is still there and lets you leave.
- [ ] When time runs out, every block of the other colors disappears.
- [ ] Whoever was not on the right color falls, sees "ELIMINATED!", hears the sound and appears in the stand.
- [ ] The floor is painted again with another pattern and the next round starts.
- [ ] Different patterns show up: mixed tiles, stripes, rings, checkerboard and diagonals.
- [ ] Round by round the music gets shorter and there is less time to react.
- [ ] The sidebar shows the round, the players alive and the time.
- [ ] Standing on a wall edge or off the floor → eliminated when the blocks are removed.
- [ ] Nobody gets eliminated in the lobby or the stand, and there is no fall damage.

## F. Power-ups (with `probabilidad: 100`)
- [ ] A glowing named star appears during the music; players already eliminated can't pick it up.
- [ ] **Speed**: you run faster for a few seconds.
- [ ] **Super jump**: you jump much higher.
- [ ] **Head start**: 1 second before the music stops you see "Next color: …" and the block in your hotbar.
- [ ] **Blindness**: everyone else gets 3 s of blindness and a message.
- [ ] **Extra block**: stand on a wrong color → your block does not disappear and you see "Your extra block saved you!".
- [ ] With `potenciadores.activado: false` they no longer appear.

## G. End and rewards
- [ ] **One winner**: when only one is left → title "X WINS!", announcement to the whole server, 50 to the winner and 5 to each player (both see it in chat). Needs Vault + an economy plugin.
- [ ] **Several winners**: both reach the last round alive (`maximo: 3`) → both win and split the 50 (25 each).
- [ ] **Everyone falls at once**: both stand on a wrong color in the same round → both win.
- [ ] After a few seconds everyone gets their things back and the floor is back to the original.
- [ ] `/bp stats` → games played, wins and best round. `/bp stats <other>` works.
- [ ] A game forced with only 1 player (`/bp start`) → says there are no rewards.
- [ ] (Optional) A command in `comandos-ganador`, for example `give {player} diamond 1` → it runs.

## H. Disconnects and crashes
- [ ] **Disconnect during a game**: quit in the middle of a round → the game goes on for the others. When you join again you get everything back and appear at the exit.
- [ ] **Disconnect in the lobby** → same.
- [ ] **Normal stop during a game**: `stop` from the console in the middle of a round → on the next start the floor is the original one and players have their things.
- [ ] **Crash**: in the middle of a round, kill the server process (not a normal stop) → on the next start the console says "Floor of arena 'test' restored after an unexpected shutdown." When they join, players get their things back.

## I. Several arenas and performance
- [ ] Create a second arena and play in both at once → they are independent (scoreboard, music, colors).
- [ ] If you have [spark](https://spark.lucko.me/), check `/spark tps` during a game → it should stay at 20. If you notice lag spikes while the floor is painted, lower `suelo.bloques-por-tick`.
- [ ] With no games running, `/spark profiler` shows no BlockParty tasks.

## J. Configuration and languages
- [ ] Change a text in `messages_en.yml` and run `/bp reload` → the new text shows.
- [ ] Set `language: es` and run `/bp reload` → `messages_es.yml` is created and messages are in Spanish.
- [ ] Set `suelo.tipo-bloque: CONCRETE` and run `/bp reload` → the next game uses concrete.
- [ ] Add your own melody in `musica.melodias` → it plays in some round.
