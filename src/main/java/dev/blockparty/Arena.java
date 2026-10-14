package dev.blockparty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

/** An arena as configured by the admins (arenas.yml), plus its floor backup. */
final class Arena {

    final String name;
    boolean enabled;
    String worldName;
    // Floor: a single layer at height y, from (minX, minZ) to (maxX, maxZ) inclusive.
    boolean hasFloor;
    int minX;
    int minZ;
    int maxX;
    int maxZ;
    int y;
    Location lobby;
    Location spawn;
    Location spectator;
    Location exit;
    /** 0 = use the global value from config.yml. */
    int minPlayers;
    int maxPlayers;
    final List<String> signs = new ArrayList<>();
    /** Original floor blocks, x-major: index = (x - minX) * depth() + (z - minZ). */
    BlockData[] backup;

    Arena(String name) {
        this.name = name;
    }

    int width() {
        return maxX - minX + 1;
    }

    int depth() {
        return maxZ - minZ + 1;
    }

    int size() {
        return width() * depth();
    }

    World world() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    Block block(int index) {
        return world().getBlockAt(minX + index / depth(), y, minZ + index % depth());
    }

    /** Index of the floor cell at this position, or -1 if it's outside the floor. */
    int indexAt(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return -1;
        }
        return (x - minX) * depth() + (z - minZ);
    }

    boolean isFloorBlock(Block block) {
        return hasFloor && block.getY() == y && block.getWorld().getName().equals(worldName)
                && indexAt(block.getX(), block.getZ()) >= 0;
    }

    /** Text listing what is still missing to play, or null if the arena is ready. */
    String missing(Mensajes mensajes) {
        List<String> missing = new ArrayList<>();
        if (!hasFloor || backup == null) {
            missing.add(mensajes.raw("falta.suelo", Map.of()));
        }
        if (lobby == null) {
            missing.add(mensajes.raw("falta.sala-espera", Map.of()));
        }
        if (spawn == null) {
            missing.add(mensajes.raw("falta.inicio", Map.of()));
        }
        if (spectator == null) {
            missing.add(mensajes.raw("falta.espectadores", Map.of()));
        }
        if (hasFloor && world() == null) {
            missing.add(mensajes.raw("falta.mundo", Map.of("mundo", worldName)));
        }
        return missing.isEmpty() ? null : String.join(", ", missing);
    }

    int minPlayers(Ajustes ajustes) {
        return minPlayers > 0 ? minPlayers : ajustes.minPlayers;
    }

    int maxPlayers(Ajustes ajustes) {
        return maxPlayers > 0 ? maxPlayers : ajustes.maxPlayers;
    }

    void save(ConfigurationSection s) {
        s.set("activada", enabled);
        s.set("mundo", worldName);
        if (hasFloor) {
            s.set("suelo.x1", minX);
            s.set("suelo.z1", minZ);
            s.set("suelo.x2", maxX);
            s.set("suelo.z2", maxZ);
            s.set("suelo.y", y);
        }
        s.set("sala-espera", lobby);
        s.set("inicio", spawn);
        s.set("espectadores", spectator);
        s.set("salida", exit);
        if (minPlayers > 0) {
            s.set("min-jugadores", minPlayers);
        }
        if (maxPlayers > 0) {
            s.set("max-jugadores", maxPlayers);
        }
        s.set("carteles", signs);
    }

    static Arena load(String name, ConfigurationSection s) {
        Arena arena = new Arena(name);
        arena.enabled = s.getBoolean("activada", false);
        arena.worldName = s.getString("mundo");
        if (s.isConfigurationSection("suelo")) {
            arena.hasFloor = true;
            arena.minX = s.getInt("suelo.x1");
            arena.minZ = s.getInt("suelo.z1");
            arena.maxX = s.getInt("suelo.x2");
            arena.maxZ = s.getInt("suelo.z2");
            arena.y = s.getInt("suelo.y");
        }
        arena.lobby = location(s, "sala-espera");
        arena.spawn = location(s, "inicio");
        arena.spectator = location(s, "espectadores");
        arena.exit = location(s, "salida");
        arena.minPlayers = s.getInt("min-jugadores", 0);
        arena.maxPlayers = s.getInt("max-jugadores", 0);
        arena.signs.addAll(s.getStringList("carteles"));
        return arena;
    }

    /** Locations whose world is not loaded yet come back as null instead of failing. */
    private static Location location(ConfigurationSection s, String path) {
        try {
            return s.getLocation(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
