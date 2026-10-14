package dev.blockparty;

import java.util.Locale;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rules for players inside an arena. Everything runs at HIGHEST priority, after
 * WorldGuard, so a region that allows something can't override these rules.
 */
final class Protecciones implements Listener {

    private final BlockParty plugin;

    Protecciones(BlockParty plugin) {
        this.plugin = plugin;
    }

    private boolean inArena(Object entity) {
        return entity instanceof Player player && plugin.partidaOf(player.getUniqueId()) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        // No damage at all: falling off the floor is detected by height, not by damage.
        if (inArena(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        Object attacker = event.getDamager();
        if (attacker instanceof Projectile projectile) {
            attacker = projectile.getShooter();
        }
        if (inArena(attacker) || inArena(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (inArena(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (inArena(event.getPlayer()) || plugin.isActiveFloor(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (inArena(event.getPlayer()) || plugin.isActiveFloor(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (inArena(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        String powerupArena = item.getItemStack().hasItemMeta()
                ? item.getItemStack().getItemMeta().getPersistentDataContainer()
                        .get(plugin.powerupKey(), PersistentDataType.STRING)
                : null;
        if (powerupArena != null) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                Partida partida = plugin.partidaOf(player.getUniqueId());
                if (partida != null && partida.arena.name.equalsIgnoreCase(powerupArena)
                        && partida.isAlive(player.getUniqueId())) {
                    partida.pickUpPowerup(player, item);
                }
            }
            return;
        }
        if (inArena(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (inArena(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (inArena(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (inArena(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.isLeaveItem(event.getItem())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
                return;
            }
            if (inArena(player)) {
                player.performCommand("bp leave");
            } else {
                // Kept by mistake outside a game: it has no use there.
                plugin.removeLeaveItems(player);
            }
            return;
        }
        if (inArena(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (inArena(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearl(PlayerTeleportEvent event) {
        if (inArena(event.getPlayer()) && (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)) {
            event.setCancelled(true);
        }
    }

    /** Only whitelisted commands work inside an arena (teleports like /spawn, /home, /is go are blocked). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!inArena(player) || player.hasPermission("blockparty.bypass.comandos")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (label.equals("bp") || label.equals("blockparty") || plugin.ajustes().allowedCommands.contains(label)) {
            return;
        }
        event.setCancelled(true);
        plugin.mensajes().send(player, "comando-bloqueado");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Partida partida = plugin.partidaOf(event.getPlayer().getUniqueId());
        if (partida != null) {
            // Their things stay saved on disk and come back when they reconnect.
            partida.leave(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (EstadoJugador.hasSaved(plugin.getDataFolder(), player.getUniqueId())) {
            // Left a game by disconnecting or a server crash: give everything back.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.partidaOf(player.getUniqueId()) == null) {
                    plugin.restoreSaved(player);
                }
            }, 5L);
        }
    }
}
