package dev.blockparty;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockParty extends JavaPlugin {

    private final Ajustes ajustes = new Ajustes();
    private final Mensajes mensajes = new Mensajes(this);
    private Arenas arenas;
    private Estadisticas estadisticas;
    private Carteles carteles;
    private Economia economia;
    private NamespacedKey powerupKey;
    private NamespacedKey powerupIdKey;
    private NamespacedKey leaveItemKey;

    /** Game runtime of each arena (lowercase name), created on first use. */
    private final Map<String, Partida> partidas = new HashMap<>();
    /** Which game every player inside an arena belongs to. */
    private final Map<UUID, Partida> tracked = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ajustes.load(getConfig(), getLogger());
        mensajes.load(ajustes.language);
        powerupKey = new NamespacedKey(this, "potenciador");
        powerupIdKey = new NamespacedKey(this, "potenciador-id");
        leaveItemKey = new NamespacedKey(this, "salir");

        arenas = new Arenas(this);
        arenas.load();
        estadisticas = new Estadisticas(getDataFolder(), getLogger());
        estadisticas.load();
        carteles = new Carteles(this);

        Comandos comandos = new Comandos(this);
        PluginCommand command = getCommand("blockparty");
        if (command != null) {
            command.setExecutor(comandos);
            command.setTabCompleter(comandos);
        }
        getServer().getPluginManager().registerEvents(new Protecciones(this), this);
        getServer().getPluginManager().registerEvents(carteles, this);

        // Once every world and plugin is ready: recover from a crash, hook the economy, refresh signs.
        getServer().getScheduler().runTask(this, () -> {
            arenas.recoverFloors();
            hookEconomy();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (EstadoJugador.hasSaved(getDataFolder(), online.getUniqueId())) {
                    restoreSaved(online);
                }
            }
            carteles.updateAll();
            getLogger().info(arenas.all().size() + " arena(s) loaded.");
        });
    }

    @Override
    public void onDisable() {
        // Server stopping or restarting: floors and players are restored right now.
        for (Partida partida : partidas.values()) {
            partida.stop(true);
        }
        if (estadisticas != null) {
            estadisticas.save();
        }
    }

    private void hookEconomy() {
        economia = null;
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault is not installed: games will not give money.");
            return;
        }
        economia = Economia.hook();
        if (economia == null) {
            getLogger().warning("Vault has no economy plugin: games will not give money.");
        }
    }

    /** /bp reload: config and messages always; arenas only when no game is running. */
    void reloadAll(CommandSender sender) {
        reloadConfig();
        ajustes.load(getConfig(), getLogger());
        mensajes.load(ajustes.language);
        boolean busy = partidas.values().stream().anyMatch(p -> p.playerCount() > 0 || p.estado() != Partida.Estado.ESPERANDO);
        if (busy) {
            mensajes.send(sender, "recargado-sin-arenas");
        } else {
            partidas.clear();
            arenas.load();
            mensajes.send(sender, "recargado");
        }
        carteles.updateAll();
    }

    // ---------------------------------------------------------------- arenas and games

    Ajustes ajustes() {
        return ajustes;
    }

    Mensajes mensajes() {
        return mensajes;
    }

    Arenas arenas() {
        return arenas;
    }

    Estadisticas estadisticas() {
        return estadisticas;
    }

    Carteles carteles() {
        return carteles;
    }

    NamespacedKey powerupKey() {
        return powerupKey;
    }

    NamespacedKey powerupIdKey() {
        return powerupIdKey;
    }

    // ---------------------------------------------------------------- leave item

    /** Puts the "leave" item in its hotbar slot (if enabled in config.yml). */
    void giveLeaveItem(Player player) {
        if (!ajustes.leaveItemEnabled) {
            return;
        }
        ItemStack item = new ItemStack(ajustes.leaveItemMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mensajes.get("objeto-salir-nombre"));
        meta.lore(mensajes.list("objeto-salir-descripcion", Map.of()));
        meta.getPersistentDataContainer().set(leaveItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(ajustes.leaveItemSlot, item);
    }

    boolean isLeaveItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(leaveItemKey, PersistentDataType.BYTE);
    }

    /** Hotbar slot reserved for the leave item, or -1 if there is none. */
    int leaveItemSlot() {
        return ajustes.leaveItemEnabled ? ajustes.leaveItemSlot : -1;
    }

    /** Takes away any leave item a player kept by mistake outside an arena. */
    void removeLeaveItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isLeaveItem(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    Partida partida(Arena arena) {
        return partidas.computeIfAbsent(arena.name.toLowerCase(Locale.ROOT), k -> new Partida(this, arena));
    }

    Partida existingPartida(Arena arena) {
        return partidas.get(arena.name.toLowerCase(Locale.ROOT));
    }

    void forgetPartida(Arena arena) {
        partidas.remove(arena.name.toLowerCase(Locale.ROOT));
    }

    Partida partidaOf(UUID player) {
        return tracked.get(player);
    }

    void track(UUID player, Partida partida) {
        tracked.put(player, partida);
    }

    void untrack(UUID player) {
        tracked.remove(player);
    }

    /** True if this block is part of the floor of an arena with a game running. */
    boolean isActiveFloor(Block block) {
        for (Partida partida : partidas.values()) {
            if (partida.estado() != Partida.Estado.ESPERANDO && partida.arena.isFloorBlock(block)) {
                return true;
            }
        }
        return false;
    }

    /** Common checks for /bp join and join signs. */
    void joinArena(Player player, Arena arena) {
        Map<String, String> name = Map.of("arena", arena.name);
        if (!player.hasPermission("blockparty.play")) {
            mensajes.send(player, "sin-permiso");
            return;
        }
        if (partidaOf(player.getUniqueId()) != null) {
            mensajes.send(player, "ya-en-partida");
            return;
        }
        if (EstadoJugador.hasSaved(getDataFolder(), player.getUniqueId())) {
            // Things from a previous game are still pending: give them back first.
            restoreSaved(player);
            return;
        }
        if (!arena.enabled) {
            mensajes.send(player, "arena-desactivada-jugador", name);
            return;
        }
        if (arena.missing(mensajes) != null) {
            mensajes.send(player, "arena-sin-configurar", name);
            return;
        }
        Partida partida = partida(arena);
        if (partida.isActive() || partida.estado() == Partida.Estado.RESTAURANDO) {
            mensajes.send(player, "arena-en-partida", name);
            return;
        }
        if (!partida.canJoin()) {
            mensajes.send(player, "arena-llena-jugador", name);
            return;
        }
        if (partida.join(player)) {
            mensajes.send(player, "has-entrado", name);
        }
    }

    /** Gives a player back what they had before a game they couldn't finish (disconnect, crash). */
    void restoreSaved(Player player) {
        Arena arena = arenas.get(EstadoJugador.savedArena(getDataFolder(), player.getUniqueId()));
        EstadoJugador.restore(getDataFolder(), player, arena == null ? null : arena.exit, getLogger());
        mensajes.send(player, "recuperado");
    }

    // ---------------------------------------------------------------- rewards and sounds

    void reward(OfflinePlayer player, double money, List<String> commands, String name) {
        if (economia != null && money > 0) {
            economia.give(player, money);
        }
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", name));
        }
    }

    String formatMoney(double amount) {
        return economia != null ? economia.format(amount) : String.format(Locale.ROOT, "%.2f", amount);
    }

    /** Plays one of the sounds of config.yml (sonidos.<key>). */
    void sound(Player player, String key, float pitch) {
        String sound = ajustes.sound(key);
        if (sound != null && !sound.isBlank()) {
            playKey(player, sound, 1f, pitch);
        }
    }

    /** Plays a sound by its Minecraft id, e.g. "block.note_block.harp". Invalid ids are ignored. */
    void playKey(Player player, String id, float volume, float pitch) {
        try {
            player.playSound(Sound.sound(Key.key(id.toLowerCase(Locale.ROOT)), Sound.Source.RECORD, volume, pitch));
        } catch (InvalidKeyException e) {
            // Wrong id in config.yml: nothing is played.
        }
    }
}
