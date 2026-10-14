package dev.blockparty;

import java.util.Iterator;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Join signs: line 1 [BlockParty], line 2 the arena. They show its state and players. */
final class Carteles implements Listener {

    private static final String TAG = "[blockparty]";

    private final BlockParty plugin;

    Carteles(BlockParty plugin) {
        this.plugin = plugin;
    }

    static String key(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";"
                + location.getBlockZ();
    }

    private Arena arenaOfSign(Block block) {
        String key = key(block.getLocation());
        for (Arena arena : plugin.arenas().all()) {
            if (arena.signs.contains(key)) {
                return arena;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignWrite(SignChangeEvent event) {
        Component first = event.line(0);
        if (first == null || !PlainTextComponentSerializer.plainText().serialize(first).trim().equalsIgnoreCase(TAG)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("blockparty.admin")) {
            event.setCancelled(true);
            plugin.mensajes().send(player, "sin-permiso");
            return;
        }
        Component second = event.line(1);
        String name = second == null ? "" : PlainTextComponentSerializer.plainText().serialize(second).trim();
        Arena arena = plugin.arenas().get(name);
        if (arena == null) {
            plugin.mensajes().send(player, "arena-no-existe", Map.of("arena", name));
            return;
        }
        String key = key(event.getBlock().getLocation());
        if (!arena.signs.contains(key)) {
            arena.signs.add(key);
            plugin.arenas().save();
        }
        Component[] lines = lines(arena);
        for (int i = 0; i < 4; i++) {
            event.line(i, lines[i]);
        }
        plugin.mensajes().send(player, "cartel-creado", Map.of("arena", arena.name));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK)
                || !(event.getClickedBlock().getState(false) instanceof Sign)) {
            return;
        }
        Arena arena = arenaOfSign(event.getClickedBlock());
        if (arena == null) {
            return;
        }
        Player player = event.getPlayer();
        // Admins can still break signs by left clicking while sneaking.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking() && player.hasPermission("blockparty.admin")) {
            return;
        }
        // Cancelled so the sign editor doesn't open.
        event.setCancelled(true);
        if (plugin.partidaOf(player.getUniqueId()) == null) {
            plugin.joinArena(player, arena);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Arena arena = arenaOfSign(event.getBlock());
        if (arena == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("blockparty.admin")) {
            event.setCancelled(true);
            return;
        }
        arena.signs.remove(key(event.getBlock().getLocation()));
        plugin.arenas().save();
        plugin.mensajes().send(event.getPlayer(), "cartel-quitado", Map.of("arena", arena.name));
    }

    /** Rewrites every sign of the arena; signs that no longer exist are forgotten. */
    void update(Arena arena) {
        Component[] lines = lines(arena);
        boolean changed = false;
        Iterator<String> it = arena.signs.iterator();
        while (it.hasNext()) {
            String[] parts = it.next().split(";");
            if (parts.length != 4) {
                it.remove();
                changed = true;
                continue;
            }
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                continue;
            }
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            BlockState state = world.getBlockAt(x, y, z).getState();
            if (!(state instanceof Sign sign)) {
                it.remove();
                changed = true;
                continue;
            }
            SignSide front = sign.getSide(Side.FRONT);
            for (int i = 0; i < 4; i++) {
                front.line(i, lines[i]);
            }
            sign.update();
        }
        if (changed) {
            plugin.arenas().save();
        }
    }

    void updateAll() {
        for (Arena arena : plugin.arenas().all()) {
            update(arena);
        }
    }

    private Component[] lines(Arena arena) {
        Partida partida = plugin.existingPartida(arena);
        String stateKey;
        if (!arena.enabled) {
            stateKey = "cartel.estado-desactivada";
        } else if (partida == null) {
            stateKey = "cartel.estado-esperando";
        } else {
            stateKey = switch (partida.estado()) {
                case ESPERANDO -> "cartel.estado-esperando";
                case CUENTA_ATRAS -> "cartel.estado-empezando";
                case JUGANDO, TERMINANDO -> "cartel.estado-en-partida";
                case RESTAURANDO -> "cartel.estado-restaurando";
            };
        }
        Map<String, String> placeholders = Map.of(
                "arena", arena.name,
                "estado", plugin.mensajes().raw(stateKey, Map.of()),
                "jugadores", String.valueOf(partida == null ? 0 : partida.playerCount()),
                "max", String.valueOf(arena.maxPlayers(plugin.ajustes())));
        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = plugin.mensajes().get("cartel.linea" + (i + 1), placeholders);
        }
        return lines;
    }
}
