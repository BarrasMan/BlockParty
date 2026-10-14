package dev.blockparty;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads and saves arenas.yml, the floor backups (suelos/<arena>.yml) and the list of
 * floors that were left painted by a game in progress (recuperacion.yml).
 */
final class Arenas {

    private final BlockParty plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final File arenasFile;
    private final File floorsFolder;
    private final File recoveryFile;

    Arenas(BlockParty plugin) {
        this.plugin = plugin;
        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        this.floorsFolder = new File(plugin.getDataFolder(), "suelos");
        this.recoveryFile = new File(plugin.getDataFolder(), "recuperacion.yml");
    }

    void load() {
        arenas.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            Arena arena = Arena.load(name, section.getConfigurationSection(name));
            arena.backup = loadBackup(arena);
            arenas.put(key(name), arena);
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "BlockParty arenas. Managed with the /bp commands; there is no need to edit this file."));
        for (Arena arena : arenas.values()) {
            arena.save(yaml.createSection("arenas." + arena.name));
        }
        try {
            yaml.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    Arena get(String name) {
        return name == null ? null : arenas.get(key(name));
    }

    Collection<Arena> all() {
        return arenas.values();
    }

    List<String> names() {
        List<String> names = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            names.add(arena.name);
        }
        return names;
    }

    Arena create(String name) {
        Arena arena = new Arena(name);
        arenas.put(key(name), arena);
        save();
        return arena;
    }

    void delete(Arena arena) {
        arenas.remove(key(arena.name));
        new File(floorsFolder, arena.name + ".yml").delete();
        save();
    }

    // ---------------------------------------------------------------- floor backup

    /** Copies the current floor blocks: they are what gets restored after every game. */
    void captureBackup(Arena arena) {
        BlockData[] backup = new BlockData[arena.size()];
        for (int i = 0; i < backup.length; i++) {
            backup[i] = arena.block(i).getBlockData();
        }
        arena.backup = backup;
        saveBackup(arena);
    }

    private void saveBackup(Arena arena) {
        // Palette + indexes keeps the file small: most floors use a handful of block types.
        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        StringBuilder indexes = new StringBuilder();
        for (int i = 0; i < arena.backup.length; i++) {
            String data = arena.backup[i].getAsString();
            Integer index = paletteIndex.get(data);
            if (index == null) {
                index = palette.size();
                palette.add(data);
                paletteIndex.put(data, index);
            }
            if (i > 0) {
                indexes.append(',');
            }
            indexes.append(index);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tamano", arena.backup.length);
        yaml.set("paleta", palette);
        yaml.set("bloques", indexes.toString());
        floorsFolder.mkdirs();
        try {
            yaml.save(new File(floorsFolder, arena.name + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the floor backup of " + arena.name, e);
        }
    }

    private BlockData[] loadBackup(Arena arena) {
        File file = new File(floorsFolder, arena.name + ".yml");
        if (!file.exists() || !arena.hasFloor) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> palette = yaml.getStringList("paleta");
        String[] indexes = yaml.getString("bloques", "").split(",");
        if (indexes.length != arena.size()) {
            plugin.getLogger().warning("The floor backup of '" + arena.name + "' does not match its size; "
                    + "use /bp setfloor again.");
            return null;
        }
        try {
            List<BlockData> parsed = new ArrayList<>();
            for (String data : palette) {
                parsed.add(Bukkit.createBlockData(data));
            }
            BlockData[] backup = new BlockData[indexes.length];
            for (int i = 0; i < indexes.length; i++) {
                backup[i] = parsed.get(Integer.parseInt(indexes[i].trim()));
            }
            return backup;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "The floor backup of '" + arena.name + "' is corrupted.", e);
            return null;
        }
    }

    /** Puts the original floor back right now (used at startup and shutdown). */
    void restoreNow(Arena arena) {
        if (arena.backup == null || arena.world() == null) {
            return;
        }
        for (int i = 0; i < arena.backup.length; i++) {
            arena.block(i).setBlockData(arena.backup[i], false);
        }
    }

    // ---------------------------------------------------------------- recovery after a crash

    private Set<String> dirtyFloors() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        return new LinkedHashSet<>(yaml.getStringList("suelos-modificados"));
    }

    private void writeDirtyFloors(Set<String> names) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Floors left half-changed by a game. They are restored automatically when the server starts."));
        yaml.set("suelos-modificados", new ArrayList<>(names));
        try {
            yaml.save(recoveryFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save recuperacion.yml", e);
        }
    }

    void markDirty(Arena arena) {
        Set<String> dirty = dirtyFloors();
        if (dirty.add(arena.name)) {
            writeDirtyFloors(dirty);
        }
    }

    void markClean(Arena arena) {
        Set<String> dirty = dirtyFloors();
        if (dirty.remove(arena.name)) {
            writeDirtyFloors(dirty);
        }
    }

    /** At startup: restore any floor a crash left painted or half removed. */
    void recoverFloors() {
        for (String name : dirtyFloors()) {
            Arena arena = get(name);
            if (arena != null && arena.backup != null && arena.world() != null) {
                restoreNow(arena);
                markClean(arena);
                plugin.getLogger().info("Floor of arena '" + name + "' restored after an unexpected shutdown.");
            }
        }
    }
}
