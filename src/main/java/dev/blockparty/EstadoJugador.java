package dev.blockparty;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Everything a player had before joining (inventory, game mode, health, hunger,
 * experience, effects, position). It's written to jugadores/<uuid>.yml the moment
 * they join, so a disconnect or a server crash never loses it.
 */
final class EstadoJugador {

    private EstadoJugador() {
    }

    private static File file(File folder, UUID id) {
        return new File(new File(folder, "jugadores"), id + ".yml");
    }

    static boolean hasSaved(File folder, UUID id) {
        return file(folder, id).exists();
    }

    /** Arena the saved state belongs to (to send the player to its exit when restoring later). */
    static String savedArena(File folder, UUID id) {
        return YamlConfiguration.loadConfiguration(file(folder, id)).getString("arena");
    }

    /** Saves the player's state to disk. Returns false if it could not be written (then don't let them join). */
    static boolean save(File folder, Player player, String arena, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("arena", arena);
        ItemStack[] contents = player.getInventory().getContents();
        yaml.set("inventario", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        yaml.set("modo", player.getGameMode().name());
        yaml.set("vida", player.getHealth());
        yaml.set("comida", player.getFoodLevel());
        yaml.set("saturacion", player.getSaturation());
        yaml.set("agotamiento", player.getExhaustion());
        yaml.set("nivel", player.getLevel());
        yaml.set("experiencia", player.getExp());
        yaml.set("experiencia-total", player.getTotalExperience());
        yaml.set("efectos", new ArrayList<>(player.getActivePotionEffects()));
        yaml.set("puede-volar", player.getAllowFlight());
        yaml.set("volando", player.isFlying());
        yaml.set("ubicacion", player.getLocation());
        File target = file(folder, player.getUniqueId());
        target.getParentFile().mkdirs();
        try {
            yaml.save(target);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save the state of " + player.getName(), e);
            return false;
        }
    }

    /** Leaves the player clean for the game: empty inventory, adventure mode, full health and food. */
    static void prepare(Player player) {
        player.getInventory().clear();
        player.setItemOnCursor(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setLevel(0);
        player.setExp(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    /**
     * Gives back everything and deletes the saved file. The player goes to {@code exit}
     * if set, otherwise to where they were before joining.
     */
    static void restore(File folder, Player player, Location exit, Logger logger) {
        File source = file(folder, player.getUniqueId());
        if (!source.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        try {
            player.closeInventory();
            player.getInventory().clear();
            String inventory = yaml.getString("inventario");
            if (inventory != null) {
                player.getInventory().setContents(
                        ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(inventory)));
            }
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            List<?> effects = yaml.getList("efectos", List.of());
            for (Object effect : effects) {
                if (effect instanceof PotionEffect potion) {
                    player.addPotionEffect(potion);
                }
            }
            try {
                player.setGameMode(GameMode.valueOf(yaml.getString("modo", "SURVIVAL")));
            } catch (IllegalArgumentException e) {
                player.setGameMode(Bukkit.getDefaultGameMode());
            }
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealth == null ? 20.0 : maxHealth.getValue();
            player.setHealth(Math.max(0.5, Math.min(max, yaml.getDouble("vida", max))));
            player.setFoodLevel(yaml.getInt("comida", 20));
            player.setSaturation((float) yaml.getDouble("saturacion", 5));
            player.setExhaustion((float) yaml.getDouble("agotamiento", 0));
            player.setLevel(yaml.getInt("nivel", 0));
            player.setExp((float) Math.max(0, Math.min(1, yaml.getDouble("experiencia", 0))));
            player.setTotalExperience(yaml.getInt("experiencia-total", 0));
            player.setAllowFlight(yaml.getBoolean("puede-volar", false));
            player.setFlying(player.getAllowFlight() && yaml.getBoolean("volando", false));
            player.setFallDistance(0f);
            player.setFireTicks(0);

            Location destination = exit;
            if (destination == null) {
                try {
                    destination = yaml.getLocation("ubicacion");
                } catch (IllegalArgumentException e) {
                    destination = null;
                }
            }
            if (destination == null || destination.getWorld() == null) {
                destination = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            player.teleport(destination);
        } catch (RuntimeException e) {
            // Keep the file: the next attempt (rejoin) can still give the items back.
            logger.log(Level.SEVERE, "Could not give back the state of " + player.getName()
                    + "; it stays saved in " + source.getName(), e);
            return;
        }
        source.delete();
    }
}
