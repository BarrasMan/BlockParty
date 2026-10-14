package dev.blockparty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /bp (alias of /blockparty) with all its subcommands and tab completion. */
final class Comandos implements TabExecutor {

    private static final List<String> PLAYER_SUBCOMMANDS = List.of("join", "leave", "list", "stats", "help");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("create", "delete", "pos1", "pos2", "setfloor",
            "setlobby", "setspawn", "setspectator", "setexit", "enable", "disable", "start", "stop", "reload");
    private static final List<String> MOD_SUBCOMMANDS = List.of("start", "stop");
    /** Subcommands whose second argument is an arena name. */
    private static final List<String> WITH_ARENA = List.of("join", "delete", "setfloor", "setlobby", "setspawn",
            "setspectator", "setexit", "enable", "disable", "start", "stop");

    private final BlockParty plugin;
    /** Corners marked with /bp pos1 and /bp pos2, per admin. */
    private final Map<UUID, Location[]> corners = new HashMap<>();

    Comandos(BlockParty plugin) {
        this.plugin = plugin;
    }

    private Mensajes msg() {
        return plugin.mensajes();
    }

    /** Moderators (blockparty.moderar) can start and stop games, but not set up arenas. */
    private static boolean canUse(CommandSender sender, String sub) {
        return sender.hasPermission("blockparty.admin")
                || (MOD_SUBCOMMANDS.contains(sub) && sender.hasPermission("blockparty.moderar"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBCOMMANDS.contains(sub)) {
            if (!canUse(sender, sub)) {
                msg().send(sender, "sin-permiso");
                return true;
            }
            admin(sender, sub, args);
            return true;
        }
        if (!sender.hasPermission("blockparty.play")) {
            msg().send(sender, "sin-permiso");
            return true;
        }
        switch (sub) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "list" -> list(sender);
            case "stats" -> stats(sender, args);
            default -> help(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------- players

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "uso", Map.of("uso", "/bp join <arena>"));
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            msg().send(sender, "arena-no-existe", Map.of("arena", args[1]));
            return;
        }
        plugin.joinArena(player, arena);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        Partida partida = plugin.partidaOf(player.getUniqueId());
        if (partida == null) {
            msg().send(sender, "no-estas-en-partida");
            return;
        }
        partida.leave(player, true);
        msg().send(sender, "has-salido", Map.of("arena", partida.arena.name));
    }

    private void list(CommandSender sender) {
        if (plugin.arenas().all().isEmpty()) {
            msg().send(sender, "lista-vacia");
            return;
        }
        msg().send(sender, "lista-titulo");
        for (Arena arena : plugin.arenas().all()) {
            Partida partida = plugin.existingPartida(arena);
            String state;
            if (!arena.enabled) {
                state = msg().raw("cartel.estado-desactivada", Map.of());
            } else if (partida == null || partida.estado() == Partida.Estado.ESPERANDO) {
                state = msg().raw("cartel.estado-esperando", Map.of());
            } else if (partida.estado() == Partida.Estado.CUENTA_ATRAS) {
                state = msg().raw("cartel.estado-empezando", Map.of());
            } else {
                state = msg().raw("cartel.estado-en-partida", Map.of());
            }
            sender.sendMessage(msg().get("lista-linea", Map.of(
                    "arena", arena.name,
                    "estado", state,
                    "jugadores", String.valueOf(partida == null ? 0 : partida.playerCount()),
                    "max", String.valueOf(arena.maxPlayers(plugin.ajustes())))));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        Estadisticas.Fila row;
        String name;
        if (args.length >= 2) {
            name = args[1];
            row = plugin.estadisticas().findByName(name);
        } else if (sender instanceof Player player) {
            name = player.getName();
            row = plugin.estadisticas().get(player.getUniqueId());
        } else {
            msg().send(sender, "uso", Map.of("uso", "/bp stats <player>"));
            return;
        }
        if (row == null) {
            msg().send(sender, "stats-sin-datos", Map.of("player", name));
            return;
        }
        for (var line : msg().list("stats", Map.of(
                "player", row.name,
                "jugadas", String.valueOf(row.played),
                "victorias", String.valueOf(row.wins),
                "mejor-ronda", String.valueOf(row.bestRound)))) {
            sender.sendMessage(line);
        }
    }

    private void help(CommandSender sender) {
        for (var line : msg().list("ayuda-jugador", Map.of())) {
            sender.sendMessage(line);
        }
        if (sender.hasPermission("blockparty.admin")) {
            for (var line : msg().list("ayuda-admin", Map.of())) {
                sender.sendMessage(line);
            }
        }
    }

    // ---------------------------------------------------------------- admins

    private void admin(CommandSender sender, String sub, String[] args) {
        if (sub.equals("reload")) {
            plugin.reloadAll(sender);
            return;
        }
        if (sub.equals("pos1") || sub.equals("pos2")) {
            corner(sender, sub.equals("pos1") ? 0 : 1);
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "uso", Map.of("uso", "/bp " + sub + " <arena>"));
            return;
        }
        String name = args[1];
        if (sub.equals("create")) {
            create(sender, name);
            return;
        }
        Arena arena = plugin.arenas().get(name);
        if (arena == null) {
            msg().send(sender, "arena-no-existe", Map.of("arena", name));
            return;
        }
        Map<String, String> arenaName = Map.of("arena", arena.name);
        switch (sub) {
            case "delete" -> {
                plugin.partida(arena).stop(false);
                plugin.arenas().delete(arena);
                plugin.forgetPartida(arena);
                msg().send(sender, "arena-borrada", arenaName);
            }
            case "setfloor" -> setFloor(sender, arena);
            case "setlobby", "setspawn", "setspectator", "setexit" -> setPoint(sender, arena, sub);
            case "enable" -> {
                String missing = arena.missing(msg());
                if (missing != null) {
                    msg().send(sender, "arena-incompleta", Map.of("arena", arena.name, "faltan", missing));
                    return;
                }
                arena.enabled = true;
                plugin.arenas().save();
                plugin.carteles().update(arena);
                msg().send(sender, "arena-activada", arenaName);
            }
            case "disable" -> {
                plugin.partida(arena).stop(false);
                arena.enabled = false;
                plugin.arenas().save();
                plugin.carteles().update(arena);
                msg().send(sender, "arena-desactivada", arenaName);
            }
            case "start" -> {
                Partida partida = plugin.partida(arena);
                if (partida.playerCount() == 0 || partida.isActive()) {
                    msg().send(sender, "no-se-puede-empezar", arenaName);
                    return;
                }
                partida.forceStart();
                msg().send(sender, "forzado-inicio", arenaName);
            }
            case "stop" -> {
                plugin.partida(arena).stop(false);
                msg().send(sender, "detenida", arenaName);
            }
            default -> help(sender);
        }
    }

    private void create(CommandSender sender, String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            msg().send(sender, "nombre-invalido");
            return;
        }
        if (plugin.arenas().get(name) != null) {
            msg().send(sender, "arena-ya-existe", Map.of("arena", name));
            return;
        }
        Arena arena = plugin.arenas().create(name);
        if (sender instanceof Player player) {
            arena.worldName = player.getWorld().getName();
            plugin.arenas().save();
        }
        msg().send(sender, "arena-creada", Map.of("arena", name));
    }

    private void corner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        Block block = player.getTargetBlockExact(50);
        if (block == null) {
            msg().send(sender, "mira-un-bloque");
            return;
        }
        corners.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[index] = block.getLocation();
        msg().send(sender, index == 0 ? "pos1-marcada" : "pos2-marcada", Map.of(
                "x", String.valueOf(block.getX()), "y", String.valueOf(block.getY()), "z", String.valueOf(block.getZ())));
    }

    private void setFloor(CommandSender sender, Arena arena) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        Partida partida = plugin.existingPartida(arena);
        if (partida != null && (partida.playerCount() > 0 || partida.estado() != Partida.Estado.ESPERANDO)) {
            msg().send(sender, "arena-en-uso", Map.of("arena", arena.name));
            return;
        }
        Location[] marked = corners.get(player.getUniqueId());
        Location a;
        Location b;
        if (marked != null && marked[0] != null && marked[1] != null) {
            a = marked[0];
            b = marked[1];
        } else if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            Location[] selection = SeleccionWorldEdit.corners(player);
            if (selection == null) {
                msg().send(sender, "sin-seleccion");
                return;
            }
            a = selection[0];
            b = selection[1];
        } else {
            msg().send(sender, "sin-seleccion");
            return;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            msg().send(sender, "esquinas-otro-mundo");
            return;
        }
        if (a.getBlockY() != b.getBlockY()) {
            msg().send(sender, "esquinas-altura");
            return;
        }
        int width = Math.abs(a.getBlockX() - b.getBlockX()) + 1;
        int depth = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
        int max = plugin.ajustes().maxFloorSize;
        if (width > max || depth > max) {
            msg().send(sender, "suelo-grande", Map.of("max", String.valueOf(max),
                    "ancho", String.valueOf(width), "largo", String.valueOf(depth)));
            return;
        }
        if (width < 3 || depth < 3) {
            msg().send(sender, "suelo-pequeno");
            return;
        }
        arena.worldName = a.getWorld().getName();
        arena.hasFloor = true;
        arena.minX = Math.min(a.getBlockX(), b.getBlockX());
        arena.maxX = Math.max(a.getBlockX(), b.getBlockX());
        arena.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        arena.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        arena.y = a.getBlockY();
        plugin.arenas().captureBackup(arena);
        plugin.arenas().save();
        corners.remove(player.getUniqueId());
        msg().send(sender, "suelo-guardado", Map.of("arena", arena.name, "ancho", String.valueOf(width),
                "largo", String.valueOf(depth), "bloques", String.valueOf(width * depth), "y", String.valueOf(arena.y)));
    }

    private void setPoint(CommandSender sender, Arena arena, String which) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        Location here = player.getLocation();
        switch (which) {
            case "setlobby" -> arena.lobby = here;
            case "setspawn" -> arena.spawn = here;
            case "setspectator" -> arena.spectator = here;
            default -> arena.exit = here;
        }
        plugin.arenas().save();
        msg().send(sender, "punto-guardado-" + which.substring(3), Map.of("arena", arena.name));
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("blockparty.play")) {
                options.addAll(PLAYER_SUBCOMMANDS);
            }
            if (sender.hasPermission("blockparty.admin")) {
                options.addAll(ADMIN_SUBCOMMANDS);
            } else if (sender.hasPermission("blockparty.moderar")) {
                options.addAll(MOD_SUBCOMMANDS);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (WITH_ARENA.contains(sub)) {
                options.addAll(plugin.arenas().names());
            } else if (sub.equals("stats")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    options.add(online.getName());
                }
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
