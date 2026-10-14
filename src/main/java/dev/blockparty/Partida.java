package dev.blockparty;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Runtime of one arena: lobby, countdown, rounds and the end of the game. Everything
 * is driven by a single task that runs every tick, and only while it has work to do
 * (countdown, a game, or blocks left to change).
 */
final class Partida {

    enum Estado { ESPERANDO, CUENTA_ATRAS, JUGANDO, TERMINANDO, RESTAURANDO }

    private enum Fase { PINTANDO, MUSICA, REACCION, QUITANDO, CAYENDO }

    /** One pending block change of the floor. */
    private record Cambio(int index, BlockData data) {
    }

    private static final BlockData AIR = Material.AIR.createBlockData();

    private final BlockParty plugin;
    final Arena arena;
    private final Random random = new Random();

    private Estado estado = Estado.ESPERANDO;
    private Fase fase;
    private BukkitTask task;
    private long tickCount;

    /** Everyone inside the arena: waiting, alive or spectating. */
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> startedWith = new LinkedHashSet<>();
    private final List<UUID> fellThisRound = new ArrayList<>();
    private final Map<UUID, Integer> roundReached = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    private int countdownLeft;
    private int round;
    private int phaseTicks;
    private int reactionTicks;
    private long startMillis;

    private final Deque<Cambio> queue = new ArrayDeque<>();
    private List<ColorBP> palette = List.of();
    private int[] grid;
    private int target;

    private Melodia melody;
    private int noteIndex;
    private int noteTicks;

    private Item powerup;
    private final Set<UUID> earlyReveal = new HashSet<>();
    private final Set<UUID> extraBlock = new HashSet<>();

    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
    private final Marcador scoreboard;

    Partida(BlockParty plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.scoreboard = new Marcador(plugin.mensajes().get("marcador.titulo"));
    }

    Estado estado() {
        return estado;
    }

    int playerCount() {
        return players.size();
    }

    boolean isAlive(UUID id) {
        return estado == Estado.JUGANDO && alive.contains(id);
    }

    boolean isActive() {
        return estado == Estado.JUGANDO || estado == Estado.TERMINANDO;
    }

    boolean canJoin() {
        return (estado == Estado.ESPERANDO || estado == Estado.CUENTA_ATRAS)
                && players.size() < arena.maxPlayers(plugin.ajustes());
    }

    // ---------------------------------------------------------------- joining and leaving

    boolean join(Player player) {
        if (!EstadoJugador.save(plugin.getDataFolder(), player, arena.name, plugin.getLogger())) {
            plugin.mensajes().send(player, "error-guardar");
            return false;
        }
        EstadoJugador.prepare(player);
        plugin.giveLeaveItem(player);
        player.teleport(arena.lobby);
        players.add(player.getUniqueId());
        names.put(player.getUniqueId(), player.getName());
        plugin.track(player.getUniqueId(), this);
        scoreboard.show(player);
        if (estado == Estado.CUENTA_ATRAS) {
            player.showBossBar(bar);
        }

        broadcast("entra", Map.of("player", player.getName(), "jugadores", String.valueOf(players.size()),
                "max", String.valueOf(arena.maxPlayers(plugin.ajustes()))));
        int min = arena.minPlayers(plugin.ajustes());
        if (estado == Estado.ESPERANDO && players.size() >= min) {
            startCountdown();
        } else if (estado == Estado.ESPERANDO) {
            broadcast("faltan-jugadores", Map.of("faltan", String.valueOf(min - players.size())));
        }
        if (estado == Estado.CUENTA_ATRAS && players.size() >= arena.maxPlayers(plugin.ajustes())
                && countdownLeft > plugin.ajustes().countdownFull) {
            countdownLeft = plugin.ajustes().countdownFull;
            broadcast("arena-llena", Map.of("segundos", String.valueOf(countdownLeft)));
        }
        updateScoreboard();
        plugin.carteles().update(arena);
        return true;
    }

    /**
     * Removes a player. With {@code restore} they get their things back now; without it
     * (disconnect) they get them back when they connect again.
     */
    void leave(Player player, boolean restore) {
        UUID id = player.getUniqueId();
        if (!players.remove(id)) {
            return;
        }
        boolean wasAlive = alive.remove(id);
        earlyReveal.remove(id);
        extraBlock.remove(id);
        plugin.untrack(id);
        scoreboard.hide(player);
        player.hideBossBar(bar);
        if (restore) {
            EstadoJugador.restore(plugin.getDataFolder(), player, arena.exit, plugin.getLogger());
        }
        broadcast("sale", Map.of("player", player.getName()));

        if (estado == Estado.CUENTA_ATRAS && players.size() < arena.minPlayers(plugin.ajustes())) {
            estado = Estado.ESPERANDO;
            hideBarFromAll();
            broadcast("cuenta-atras-cancelada", Map.of());
            stopTaskIfIdle();
        } else if (estado == Estado.JUGANDO && wasAlive) {
            checkEarlyEnd();
        } else if (estado == Estado.JUGANDO && players.isEmpty()) {
            finishWith(List.of());
        }
        updateScoreboard();
        plugin.carteles().update(arena);
    }

    /** During a game: ends it if nobody (or only one, in a real match) is left standing. */
    private void checkEarlyEnd() {
        if (alive.isEmpty()) {
            finishWith(List.of());
        } else if (alive.size() == 1 && startedWith.size() >= 2) {
            finishWith(new ArrayList<>(alive));
        }
    }

    // ---------------------------------------------------------------- task

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTaskIfIdle() {
        if (task != null && estado == Estado.ESPERANDO && queue.isEmpty()) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCount++;
        processQueue();
        switch (estado) {
            case CUENTA_ATRAS -> countdownTick();
            case JUGANDO -> {
                checkFalls();
                if (estado == Estado.JUGANDO) {
                    gameTick();
                }
            }
            case TERMINANDO -> {
                if (--phaseTicks <= 0) {
                    finish();
                }
            }
            case RESTAURANDO -> {
                if (queue.isEmpty()) {
                    plugin.arenas().markClean(arena);
                    estado = Estado.ESPERANDO;
                    plugin.carteles().update(arena);
                }
            }
            case ESPERANDO -> stopTaskIfIdle();
        }
        if (tickCount % 20 == 0) {
            updateScoreboard();
        }
    }

    private void processQueue() {
        if (queue.isEmpty() || arena.world() == null) {
            return;
        }
        int budget = plugin.ajustes().blocksPerTick;
        while (budget-- > 0 && !queue.isEmpty()) {
            Cambio change = queue.poll();
            arena.block(change.index()).setBlockData(change.data(), false);
        }
    }

    // ---------------------------------------------------------------- countdown

    private void startCountdown() {
        estado = Estado.CUENTA_ATRAS;
        countdownLeft = players.size() >= arena.maxPlayers(plugin.ajustes())
                ? plugin.ajustes().countdownFull : plugin.ajustes().countdown;
        bar.color(BossBar.Color.GREEN);
        forEachPlayer(p -> p.showBossBar(bar));
        updateCountdownBar();
        broadcast("cuenta-atras-empieza", Map.of("segundos", String.valueOf(countdownLeft)));
        ensureTask();
        plugin.carteles().update(arena);
    }

    private void countdownTick() {
        if (tickCount % 20 != 0) {
            return;
        }
        countdownLeft--;
        if (countdownLeft <= 0) {
            startGame();
            return;
        }
        updateCountdownBar();
        forEachPlayer(p -> p.setLevel(countdownLeft));
        if (countdownLeft <= 5 || countdownLeft == 10) {
            Component main = plugin.mensajes().get("titulo-cuenta-atras", Map.of("segundos", String.valueOf(countdownLeft)));
            forEachPlayer(p -> {
                p.showTitle(title(main, Component.empty(), 0, 900, 100));
                plugin.sound(p, "cuenta-atras", 1f);
            });
        }
    }

    private void updateCountdownBar() {
        bar.name(plugin.mensajes().get("barra-cuenta-atras", Map.of("segundos", String.valueOf(countdownLeft))));
        bar.progress(Math.max(0f, Math.min(1f, countdownLeft / (float) plugin.ajustes().countdown)));
    }

    // ---------------------------------------------------------------- game

    /** Starts now (also used by /bp start). */
    void forceStart() {
        if ((estado == Estado.ESPERANDO || estado == Estado.CUENTA_ATRAS) && !players.isEmpty()) {
            ensureTask();
            startGame();
        }
    }

    private void startGame() {
        estado = Estado.JUGANDO;
        round = 1;
        startMillis = System.currentTimeMillis();
        palette = new ArrayList<>(plugin.ajustes().colors);
        startedWith.clear();
        startedWith.addAll(players);
        alive.clear();
        alive.addAll(players);
        roundReached.clear();
        plugin.arenas().markDirty(arena);

        Component start = plugin.mensajes().get("titulo-empieza");
        Component startSub = plugin.mensajes().get("subtitulo-empieza");
        forEachPlayer(p -> {
            EstadoJugador.prepare(p);
            plugin.giveLeaveItem(p);
            p.teleport(arena.spawn);
            p.showTitle(title(start, startSub, 100, 1500, 300));
        });
        broadcast("empieza", Map.of("jugadores", String.valueOf(players.size())));
        plugin.carteles().update(arena);
        beginRound();
    }

    private void beginRound() {
        fase = Fase.PINTANDO;
        fellThisRound.clear();
        Ajustes.Patron patron = Patrones.pick(plugin.ajustes().patterns, random);
        grid = Patrones.generate(patron, arena.width(), arena.depth(), palette.size(), random);
        target = grid[random.nextInt(grid.length)];
        for (int i = 0; i < grid.length; i++) {
            queue.add(new Cambio(i, palette.get(grid[i]).data()));
        }
        clearHotbars();
        removePowerup();
    }

    private void gameTick() {
        switch (fase) {
            case PINTANDO -> {
                if (queue.isEmpty()) {
                    startMusic();
                }
            }
            case MUSICA -> {
                playMelodyTick();
                phaseTicks--;
                if (phaseTicks == 20) {
                    revealEarly();
                }
                if (phaseTicks <= 0) {
                    reveal();
                }
            }
            case REACCION -> {
                phaseTicks--;
                bar.progress(Math.max(0f, phaseTicks / (float) reactionTicks));
                if (phaseTicks <= 0) {
                    removeOtherColors();
                }
            }
            case QUITANDO -> {
                if (queue.isEmpty()) {
                    fase = Fase.CAYENDO;
                    phaseTicks = (int) Math.round(plugin.ajustes().fallPause * 20);
                }
            }
            case CAYENDO -> {
                if (--phaseTicks <= 0) {
                    endRound();
                }
            }
        }
    }

    private void startMusic() {
        fase = Fase.MUSICA;
        phaseTicks = Math.max(20, (int) Math.round(plugin.ajustes().musicSeconds(round) * 20));
        List<Melodia> melodies = plugin.ajustes().melodies;
        melody = melodies.get(random.nextInt(melodies.size()));
        noteIndex = 0;
        noteTicks = 0;
        bar.color(BossBar.Color.PINK);
        bar.name(plugin.mensajes().get("barra-musica", Map.of("ronda", String.valueOf(round))));
        bar.progress(1f);
        forEachPlayer(p -> p.showBossBar(bar));
        maybeSpawnPowerup();
    }

    private void playMelodyTick() {
        if (--noteTicks > 0) {
            return;
        }
        noteTicks = melody.ticksPerNote();
        float[] step = melody.step(noteIndex++);
        float volume = plugin.ajustes().musicVolume;
        forEachPlayer(p -> {
            for (float pitch : step) {
                plugin.playKey(p, melody.sound(), volume, pitch);
            }
        });
    }

    /** "See the next color 1 second before" power-up. */
    private void revealEarly() {
        ColorBP color = palette.get(target);
        for (UUID id : earlyReveal) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && alive.contains(id)) {
                player.showTitle(title(Component.empty(),
                        plugin.mensajes().get("subtitulo-adelanto", colorPlaceholders(color)), 0, 1200, 0));
                fillHotbar(player, color);
            }
        }
        earlyReveal.clear();
    }

    private void reveal() {
        fase = Fase.REACCION;
        reactionTicks = Math.max(10, (int) Math.round(plugin.ajustes().reactionSeconds(round) * 20));
        phaseTicks = reactionTicks;
        removePowerup();
        ColorBP color = palette.get(target);
        Map<String, String> placeholders = colorPlaceholders(color);
        Component main = plugin.mensajes().get("titulo-color", placeholders);
        Component sub = plugin.mensajes().get("subtitulo-color", placeholders);
        bar.color(BossBar.Color.WHITE);
        bar.name(plugin.mensajes().get("barra-color", placeholders));
        bar.progress(1f);
        int stayMillis = reactionTicks * 50;
        forEachPlayer(p -> {
            p.showTitle(title(main, sub, 0, stayMillis, 150));
            plugin.sound(p, "parar-musica", 1f);
            if (alive.contains(p.getUniqueId())) {
                fillHotbar(p, color);
            }
        });
    }

    private void removeOtherColors() {
        fase = Fase.QUITANDO;
        boolean[] keep = new boolean[grid.length];
        for (int i = 0; i < grid.length; i++) {
            keep[i] = grid[i] == target;
        }
        // "Extra block" power-up: the block under the player's feet survives.
        for (UUID id : extraBlock) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !alive.contains(id)) {
                continue;
            }
            Location feet = player.getLocation();
            int index = arena.indexAt(feet.getBlockX(), feet.getBlockZ());
            if (index >= 0 && feet.getY() >= arena.y && feet.getY() < arena.y + 2.5) {
                keep[index] = true;
                if (grid[index] != target) {
                    queue.add(new Cambio(index, palette.get(target).data()));
                }
                plugin.mensajes().send(player, "potenciador-bloque-usado");
            }
        }
        extraBlock.clear();
        for (int i = 0; i < grid.length; i++) {
            if (!keep[i]) {
                queue.add(new Cambio(i, AIR));
            }
        }
        clearHotbars();
        bar.progress(0f);
        // Anyone not standing over the floor (a wall edge, stairs...) would never fall: out.
        for (UUID id : new ArrayList<>(alive)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Location at = player.getLocation();
                if (arena.indexAt(at.getBlockX(), at.getBlockZ()) < 0) {
                    eliminate(player);
                }
            }
        }
    }

    private void endRound() {
        int max = plugin.ajustes().maxRounds;
        if (alive.isEmpty()) {
            // Everyone left fell in the same round: they all win.
            finishWith(new ArrayList<>(fellThisRound));
        } else if (alive.size() == 1 && startedWith.size() >= 2) {
            finishWith(new ArrayList<>(alive));
        } else if (round >= max) {
            finishWith(new ArrayList<>(alive));
        } else {
            round++;
            broadcast("ronda-superada", Map.of("ronda", String.valueOf(round), "vivos", String.valueOf(alive.size())));
            beginRound();
        }
    }

    private void checkFalls() {
        double limit = arena.y - plugin.ajustes().fallDepth;
        for (UUID id : new ArrayList<>(alive)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            Location location = player.getLocation();
            if (location.getY() < limit || !location.getWorld().getName().equals(arena.worldName)) {
                eliminate(player);
            }
        }
    }

    private void eliminate(Player player) {
        UUID id = player.getUniqueId();
        alive.remove(id);
        fellThisRound.add(id);
        roundReached.put(id, round);
        earlyReveal.remove(id);
        extraBlock.remove(id);
        player.getInventory().clear();
        plugin.giveLeaveItem(player);
        player.setFallDistance(0f);
        player.teleport(arena.spectator);
        player.showTitle(title(plugin.mensajes().get("titulo-eliminado"),
                plugin.mensajes().get("subtitulo-eliminado", Map.of("ronda", String.valueOf(round))), 100, 2000, 400));
        broadcast("eliminado", Map.of("player", player.getName(), "vivos", String.valueOf(alive.size())));
        forEachPlayer(p -> plugin.sound(p, "eliminado", 1f));
        // The winner is decided at the end of the round (endRound): if the last two fall
        // together, both must count as having fallen in the final round.
    }

    // ---------------------------------------------------------------- power-ups

    private void maybeSpawnPowerup() {
        Ajustes ajustes = plugin.ajustes();
        if (!ajustes.powerupsEnabled || ajustes.powerups.isEmpty() || random.nextDouble() * 100 >= ajustes.powerupChance) {
            return;
        }
        int index = random.nextInt(arena.size());
        Location location = arena.block(index).getLocation().add(0.5, 1.2, 0.5);
        ItemStack stack = new ItemStack(ajustes.powerupMaterial);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.powerupKey(), PersistentDataType.STRING, arena.name);
        // A random id stops two power-ups from merging into one stack.
        meta.getPersistentDataContainer().set(plugin.powerupIdKey(), PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        powerup = location.getWorld().dropItem(location, stack, item -> {
            item.setVelocity(new Vector(0, 0, 0));
            item.setPickupDelay(10);
            item.setGlowing(true);
            item.setUnlimitedLifetime(true);
            item.customName(plugin.mensajes().get("potenciador-nombre"));
            item.setCustomNameVisible(true);
        });
        broadcast("potenciador-aparece", Map.of());
        forEachPlayer(p -> plugin.sound(p, "potenciador-aparece", 1f));
    }

    private void removePowerup() {
        if (powerup != null) {
            powerup.remove();
            powerup = null;
        }
    }

    /** Called when an alive player picks up this arena's power-up. */
    void pickUpPowerup(Player player, Item item) {
        item.remove();
        if (item.equals(powerup)) {
            powerup = null;
        }
        if (!alive.contains(player.getUniqueId())) {
            return;
        }
        Ajustes ajustes = plugin.ajustes();
        Ajustes.Potenciador type = pickPowerup(ajustes.powerups);
        plugin.sound(player, "potenciador-coger", 1f);
        switch (type) {
            case VELOCIDAD -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, ajustes.speedSeconds * 20, 1, false, true, true));
            case SUPERSALTO -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.JUMP_BOOST, ajustes.jumpSeconds * 20, 2, false, true, true));
            case ADELANTO -> earlyReveal.add(player.getUniqueId());
            case CEGUERA -> {
                for (UUID id : alive) {
                    Player other = Bukkit.getPlayer(id);
                    if (other != null && !other.equals(player)) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                                ajustes.blindSeconds * 20, 0, false, false, true));
                        plugin.mensajes().send(other, "potenciador-ceguera-recibida", Map.of("player", player.getName()));
                    }
                }
            }
            case BLOQUE_EXTRA -> extraBlock.add(player.getUniqueId());
        }
        plugin.mensajes().send(player, "potenciador-" + type.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private Ajustes.Potenciador pickPowerup(Map<Ajustes.Potenciador, Double> weights) {
        double total = 0;
        for (double weight : weights.values()) {
            total += weight;
        }
        double roll = random.nextDouble() * total;
        for (Map.Entry<Ajustes.Potenciador, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    // ---------------------------------------------------------------- end of the game

    private void finishWith(List<UUID> winners) {
        if (estado != Estado.JUGANDO) {
            return;
        }
        estado = Estado.TERMINANDO;
        phaseTicks = plugin.ajustes().endSeconds * 20;
        removePowerup();
        clearHotbars();
        hideBarFromAll();
        for (UUID id : alive) {
            roundReached.put(id, round);
        }

        List<String> winnerNames = new ArrayList<>();
        for (UUID id : winners) {
            winnerNames.add(names.getOrDefault(id, "?"));
        }
        String joined = String.join(", ", winnerNames);
        Map<String, String> placeholders = Map.of("ganadores", joined, "ronda", String.valueOf(round),
                "arena", arena.name);
        Component main = winners.isEmpty() ? plugin.mensajes().get("titulo-sin-ganador")
                : plugin.mensajes().get(winners.size() == 1 ? "titulo-ganador" : "titulo-ganadores", placeholders);
        Component sub = plugin.mensajes().get("subtitulo-final", placeholders);
        forEachPlayer(p -> {
            p.showTitle(title(main, sub, 100, plugin.ajustes().endSeconds * 1000 - 500, 400));
            plugin.sound(p, "victoria", 1f);
        });
        if (winners.isEmpty()) {
            broadcast("final-sin-ganador", placeholders);
        } else if (plugin.ajustes().announceGlobally) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.mensajes().send(online, "final-ganadores-global", placeholders);
            }
        } else {
            broadcast("final-ganadores-global", placeholders);
        }

        giveRewardsAndStats(winners);
        plugin.carteles().update(arena);
    }

    private void giveRewardsAndStats(List<UUID> winners) {
        Ajustes ajustes = plugin.ajustes();
        boolean rewards = startedWith.size() >= ajustes.minPlayersForRewards;
        if (!rewards) {
            broadcast("sin-recompensas", Map.of("minimo", String.valueOf(ajustes.minPlayersForRewards)));
        }
        double perWinner = winners.isEmpty() ? 0
                : ajustes.splitWinnerMoney ? Math.floor(ajustes.winnerMoney * 100 / winners.size()) / 100 : ajustes.winnerMoney;

        for (UUID id : startedWith) {
            String name = names.getOrDefault(id, "?");
            boolean won = winners.contains(id);
            plugin.estadisticas().record(id, name, won, roundReached.getOrDefault(id, round));
            // Only those still in the arena at the end are rewarded (leaving early gives nothing).
            if (!rewards || !players.contains(id)) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            Player online = offline.getPlayer();
            if (won) {
                plugin.reward(offline, perWinner, ajustes.winnerCommands, name);
                if (online != null && perWinner > 0) {
                    plugin.mensajes().send(online, "recompensa-ganador", Map.of("dinero", plugin.formatMoney(perWinner)));
                }
            }
            plugin.reward(offline, ajustes.participationMoney, ajustes.participationCommands, name);
            if (online != null && ajustes.participationMoney > 0) {
                plugin.mensajes().send(online, "recompensa-participar",
                        Map.of("dinero", plugin.formatMoney(ajustes.participationMoney)));
            }
        }
        plugin.estadisticas().save();
    }

    /** Sends everyone back and puts the original floor back (a little each tick). */
    private void finish() {
        for (UUID id : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                scoreboard.hide(player);
                player.hideBossBar(bar);
                EstadoJugador.restore(plugin.getDataFolder(), player, arena.exit, plugin.getLogger());
            }
            plugin.untrack(id);
        }
        resetRuntime();
        enqueueRestore();
        estado = Estado.RESTAURANDO;
        ensureTask();
        plugin.carteles().update(arena);
    }

    private void resetRuntime() {
        players.clear();
        alive.clear();
        startedWith.clear();
        fellThisRound.clear();
        roundReached.clear();
        names.clear();
        earlyReveal.clear();
        extraBlock.clear();
        removePowerup();
        round = 0;
        queue.clear();
    }

    private void enqueueRestore() {
        if (arena.backup == null) {
            return;
        }
        for (int i = 0; i < arena.backup.length; i++) {
            queue.add(new Cambio(i, arena.backup[i]));
        }
    }

    /**
     * Stops the game without winners or rewards (/bp stop, disabling or deleting the arena).
     * With {@code now} (server shutdown) the floor and the players are restored immediately.
     */
    void stop(boolean now) {
        if (estado == Estado.ESPERANDO && players.isEmpty() && queue.isEmpty()) {
            return;
        }
        if (!now) {
            broadcast("partida-detenida", Map.of());
        }
        for (UUID id : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                scoreboard.hide(player);
                player.hideBossBar(bar);
                EstadoJugador.restore(plugin.getDataFolder(), player, arena.exit, plugin.getLogger());
            }
            plugin.untrack(id);
        }
        boolean floorTouched = estado != Estado.ESPERANDO && estado != Estado.CUENTA_ATRAS || !queue.isEmpty();
        resetRuntime();
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (floorTouched) {
            if (now) {
                plugin.arenas().restoreNow(arena);
                plugin.arenas().markClean(arena);
                estado = Estado.ESPERANDO;
            } else {
                enqueueRestore();
                estado = Estado.RESTAURANDO;
                ensureTask();
            }
        } else {
            estado = Estado.ESPERANDO;
        }
        plugin.carteles().update(arena);
    }

    // ---------------------------------------------------------------- helpers

    private void fillHotbar(Player player, ColorBP color) {
        ItemStack item = new ItemStack(color.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.mensajes().get("objeto-color", colorPlaceholders(color)));
        item.setItemMeta(meta);
        int leaveSlot = plugin.leaveItemSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (slot != leaveSlot) {
                player.getInventory().setItem(slot, item);
            }
        }
    }

    private void clearHotbars() {
        int leaveSlot = plugin.leaveItemSlot();
        for (UUID id : alive) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                for (int slot = 0; slot < 9; slot++) {
                    if (slot != leaveSlot) {
                        player.getInventory().setItem(slot, null);
                    }
                }
            }
        }
    }

    private Map<String, String> colorPlaceholders(ColorBP color) {
        return Map.of("color", color.legacyColor() + color.name(), "nombre", color.name(), "c", color.legacyColor());
    }

    private void hideBarFromAll() {
        forEachPlayer(p -> p.hideBossBar(bar));
    }

    private void forEachPlayer(Consumer<Player> action) {
        for (UUID id : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        Map<String, String> all = new HashMap<>(placeholders);
        all.putIfAbsent("arena", arena.name);
        forEachPlayer(p -> plugin.mensajes().send(p, key, all));
    }

    private static Title title(Component main, Component sub, int fadeInMillis, int stayMillis, int fadeOutMillis) {
        return Title.title(main, sub, Title.Times.times(Duration.ofMillis(fadeInMillis),
                Duration.ofMillis(Math.max(0, stayMillis)), Duration.ofMillis(fadeOutMillis)));
    }

    private void updateScoreboard() {
        if (players.isEmpty()) {
            return;
        }
        long seconds = estado == Estado.JUGANDO || estado == Estado.TERMINANDO
                ? (System.currentTimeMillis() - startMillis) / 1000 : 0;
        Map<String, String> placeholders = Map.of(
                "arena", arena.name,
                "jugadores", String.valueOf(players.size()),
                "max", String.valueOf(arena.maxPlayers(plugin.ajustes())),
                "min", String.valueOf(arena.minPlayers(plugin.ajustes())),
                "ronda", String.valueOf(round),
                "rondas", String.valueOf(plugin.ajustes().maxRounds),
                "vivos", String.valueOf(alive.size()),
                "tiempo", String.format("%d:%02d", seconds / 60, seconds % 60),
                "segundos", String.valueOf(countdownLeft));
        String key = switch (estado) {
            case ESPERANDO, RESTAURANDO -> "marcador.esperando";
            case CUENTA_ATRAS -> "marcador.cuenta-atras";
            case JUGANDO, TERMINANDO -> "marcador.jugando";
        };
        scoreboard.lines(plugin.mensajes().list(key, placeholders));
    }
}
